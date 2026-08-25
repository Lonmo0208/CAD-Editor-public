package com.github.rinorsi.cadeditor.common.logic;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.lang.reflect.Method;

/**
 * "Instant-kill flag": attached to any weapon (cadeditor.instant_kill marker in custom_data).
 * When an OP4 player holding the weapon attacks a target, zero the target's health
 * and trigger the full death flow, bypassing the damage system
 * (armor, resistance, invulnerability frames, absorption hearts, totems, etc.).
 * Pure server-side logic; unaffected by modless clients.
 */
public final class InstantKillLogic {
    public static final String INSTANT_KILL_KEY = "cadeditor.instant_kill";
    /** Fixed damage dealt by non-OP players holding the OP sword (golden sword base attack damage) */
    public static final float NORMAL_PLAYER_DAMAGE = 4.0F;

    /** Re-entry guard: set true while bypassing hurt() / re-applying clamped damage to avoid infinite recursion */
    private static boolean applyingClampedDamage = false;

    // Reflection cache: getExperienceReward(ServerLevel, Player) — used for fallback experience
    private static Method m_getExperienceReward = null;
    private static boolean reflectionInited = false;

    private static void initReflection() {
        if (reflectionInited) return;
        reflectionInited = true;
        try {
            // protected int getExperienceReward(ServerLevel level, Player killer)
            m_getExperienceReward = LivingEntity.class.getDeclaredMethod(
                    "getExperienceReward", ServerLevel.class, Player.class);
            m_getExperienceReward.setAccessible(true);
        } catch (NoSuchMethodException ignored) {
            try {
                // SRG fallback: m_7615_(getExperienceReward)
                m_getExperienceReward = LivingEntity.class.getDeclaredMethod(
                        "m_7615_", ServerLevel.class, Player.class);
                m_getExperienceReward.setAccessible(true);
            } catch (NoSuchMethodException ignored2) { /* leave null */ }
        }
    }

    private InstantKillLogic() {
    }

    public static boolean isApplyingClampedDamage() {
        return applyingClampedDamage;
    }

    public static void setApplyingClampedDamage(boolean applyingClampedDamage) {
        InstantKillLogic.applyingClampedDamage = applyingClampedDamage;
    }

    /**
     * Checks whether the weapon carries the instant-kill flag.
     */
    public static boolean isInstantKillWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        return customData.copyTag().getBoolean(INSTANT_KILL_KEY);
    }

    /**
     * Clamps OP sword damage for normal players:
     * - OP4 / creative players: keep original damage (∞ or instant kill)
     * - Normal players holding the OP sword: forced to fixed 4 (golden sword base damage) to prevent infinite damage abuse
     *
     * @return the corrected damage value
     */
    public static float clampDamageForNormalPlayer(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer player)) {
            return amount;
        }
        if (PermissionLogic.isAdmin(player) || player.isCreative()) {
            return amount;
        }
        if (!isInstantKillWeapon(player.getMainHandItem())) {
            return amount;
        }
        return NORMAL_PLAYER_DAMAGE;
    }

    /**
     * Tries to trigger an instant kill when an entity takes damage.
     *
     * @return true if an instant kill was triggered and this damage was intercepted
     */
    public static boolean tryInstantKill(Entity target, DamageSource source) {
        if (target.level().isClientSide()) {
            return false;
        }
        if (!(target instanceof LivingEntity living)) {
            return false;
        }
        if (living.isRemoved() || living.isDeadOrDying()) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer player)) {
            return false;
        }
        // Only effective when held by an OP4 player
        if (!PermissionLogic.isAdmin(player)) {
            return false;
        }
        if (!isInstantKillWeapon(player.getMainHandItem())) {
            return false;
        }

        initReflection();

        // Let a single 0-damage hurt() through so vanilla fully sets the kill attribution.
        // We intercept hurt() HEAD in a Mixin, so vanilla never gets a chance to run:
        //   - set lastHurtByPlayer / lastHurtByMob / lastHurtByMobTimestamp
        //   - set hurtDuration / hurtTime / hurtDir / invulnerableTime
        //   - call awardKillScore / recordKill (statistics)
        // die()'s exp and loot drops heavily depend on these flags; without them nothing drops.
        // Fix: set applyingClampedDamage (Mixin skips all interception) and
        //      call hurt() with 0 damage — 0 damage won't drain health or trigger totems,
        //      but runs the full flag-setting flow.
        DamageSource playerKillSource = living.damageSources().playerAttack(player);
        boolean wasAlive = !living.isDeadOrDying();
        applyingClampedDamage = true;
        try {
            living.hurt(playerKillSource, 0.0F);
        } finally {
            applyingClampedDamage = false;
        }
        // Guard: if the 0-damage hurt() killed the entity via some logic (e.g. thorns), return directly
        if (wasAlive && (living.isRemoved() || living.isDeadOrDying())) {
            SecurityAuditLog.logSuccessfulEdit(player, "instant_kill_weapon",
                    "killed (via 0dmg side-effect) " + living.getType().getDescriptionId()
                            + " at " + living.blockPosition(), null);
            return true;
        }

        // Read the expected exp before MAX_HEALTH is modified.
        int expectedExp = 0;
        ServerLevel serverLevel = living.level() instanceof ServerLevel sl ? sl : null;
        if (m_getExperienceReward != null && serverLevel != null) {
            try {
                Object result = m_getExperienceReward.invoke(living, serverLevel, player);
                if (result instanceof Integer exp) {
                    expectedExp = exp;
                }
            } catch (Exception ignored) { /* fallthrough */ }
        }

        if (living instanceof Player) {
            // Players: empty the health bar completely (health = 0) with NO death flow —
            // no popup, no drops, no experience, and Creation Heart can't intercept it
            // because no LivingDeathEvent fires. MAX_HEALTH is restored right away so no
            // clamped sliver ("half heart") persists after respawn.
            AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(0.0001F);
            }
            living.setHealth(0.0F);
            if (maxHealth != null) {
                maxHealth.setBaseValue(Attributes.MAX_HEALTH.value().getDefaultValue());
            }
        } else {
            // Non-player entities: keep experience and loot drops via the (silent)
            // death flow. Clamp MAX_HEALTH to 0 so self-repair / mod protection can't
            // push health back above 0 (setHealth clamps to [0, maxHealth]).
            AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(0.0F);
            }
            living.setHealth(0.0F);

            DamageSource killSource = living.damageSources().genericKill();
            if (!living.isRemoved()) {
                living.die(killSource);
            }
            if (!living.isRemoved() && !living.isDeadOrDying()) {
                living.setHealth(0.0F);
                if (!living.isRemoved()) {
                    living.die(killSource);
                }
            }
            if (!living.isRemoved()) {
                living.remove(Entity.RemovalReason.KILLED);
            }
        }

        // die() usually drops exp on its own since we already set lastHurtByPlayer;
        // top up again for Boss/special entities or remove-fallback cases to guarantee drops.
        if (expectedExp > 0 && serverLevel != null && !(living instanceof Player)) {
            ExperienceOrb.award(serverLevel, living.position(), expectedExp);
        }

        SecurityAuditLog.logSuccessfulEdit(player, "instant_kill_weapon",
                "killed " + living.getType().getDescriptionId() + " at " + living.blockPosition(), null);
        return true;
    }
}
