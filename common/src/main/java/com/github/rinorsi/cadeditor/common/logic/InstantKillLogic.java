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

import java.lang.reflect.Field;
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

    // Reflection cache
    private static Method m_getExperienceReward = null;
    private static Field f_lastHurtByPlayer = null;
    private static Field f_lastHurtByMob = null;
    private static Field f_lastHurtByMobTimestamp = null;
    private static boolean reflectionInited = false;

    private static void initReflection() {
        if (reflectionInited) return;
        reflectionInited = true;

        // getExperienceReward
        try {
            m_getExperienceReward = LivingEntity.class.getDeclaredMethod(
                    "getExperienceReward", ServerLevel.class, Player.class);
            m_getExperienceReward.setAccessible(true);
        } catch (NoSuchMethodException ignored) {
            try {
                m_getExperienceReward = LivingEntity.class.getDeclaredMethod(
                        "m_7615_", ServerLevel.class, Player.class);
                m_getExperienceReward.setAccessible(true);
            } catch (NoSuchMethodException ignored2) { /* leave null */ }
        }

        // lastHurtByPlayer
        try {
            f_lastHurtByPlayer = LivingEntity.class.getDeclaredField("lastHurtByPlayer");
            f_lastHurtByPlayer.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
            try {
                f_lastHurtByPlayer = LivingEntity.class.getDeclaredField("f_21484_");
                f_lastHurtByPlayer.setAccessible(true);
            } catch (NoSuchFieldException ignored2) { /* leave null */ }
        }

        // lastHurtByMob
        try {
            f_lastHurtByMob = LivingEntity.class.getDeclaredField("lastHurtByMob");
            f_lastHurtByMob.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
            try {
                f_lastHurtByMob = LivingEntity.class.getDeclaredField("f_21481_");
                f_lastHurtByMob.setAccessible(true);
            } catch (NoSuchFieldException ignored2) { /* leave null */ }
        }

        // lastHurtByMobTimestamp
        try {
            f_lastHurtByMobTimestamp = LivingEntity.class.getDeclaredField("lastHurtByMobTimestamp");
            f_lastHurtByMobTimestamp.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
            try {
                f_lastHurtByMobTimestamp = LivingEntity.class.getDeclaredField("f_21483_");
                f_lastHurtByMobTimestamp.setAccessible(true);
            } catch (NoSuchFieldException ignored2) { /* leave null */ }
        }
    }

    /**
     * Manually set kill attribution flags on a living entity so that
     * die() and death-related logic (exp/loot drops) work correctly
     * even when hurt() was intercepted by a custom override (e.g. Lich shield).
     */
    private static void setKillAttribution(LivingEntity entity, ServerPlayer attacker) {
        if (f_lastHurtByPlayer != null) {
            try {
                f_lastHurtByPlayer.set(entity, attacker);
            } catch (IllegalAccessException ignored) { /* no-op */ }
        }
        if (f_lastHurtByMob != null) {
            try {
                f_lastHurtByMob.set(entity, attacker);
            } catch (IllegalAccessException ignored) { /* no-op */ }
        }
        if (f_lastHurtByMobTimestamp != null) {
            try {
                f_lastHurtByMobTimestamp.setLong(entity, entity.level().getGameTime());
            } catch (IllegalAccessException ignored) { /* no-op */ }
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
        if (!PermissionLogic.isAdmin(player)) {
            return false;
        }
        if (!isInstantKillWeapon(player.getMainHandItem())) {
            return false;
        }

        initReflection();

        // First, always set kill attribution flags via reflection.
        // This ensures that even if hurt() is intercepted (e.g. Lich shield),
        // the fallback processTick() can detect this attack via lastHurtByMob.
        setKillAttribution(living, player);

        // Let a single 0-damage hurt() through so vanilla fully sets additional flags.
        // We intercept hurt() HEAD in a Mixin, so vanilla never gets a chance to run:
        // Fix: set applyingClampedDamage (Mixin skips all interception) and
        //      call hurt() with 0 damage — 0 damage won't drain health or trigger totems,
        //      but runs the full flag-setting flow for entities that don't block hurt().
        DamageSource playerKillSource = living.damageSources().playerAttack(player);
        boolean wasAlive = !living.isDeadOrDying();
        applyingClampedDamage = true;
        try {
            living.hurt(playerKillSource, 0.0F);
        } finally {
            applyingClampedDamage = false;
        }

        // Re-apply kill attribution after hurt() since some implementations may clear it
        setKillAttribution(living, player);

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

            // Use playerAttack source so die() correctly attributes kill to the player
            setKillAttribution(living, player);
            DamageSource killSource = living.damageSources().playerAttack(player);
            if (!living.isRemoved()) {
                living.die(killSource);
            }
            if (!living.isRemoved() && !living.isDeadOrDying()) {
                living.setHealth(0.0F);
                setKillAttribution(living, player);
                if (!living.isRemoved()) {
                    living.die(killSource);
                }
            }
            if (!living.isRemoved()) {
                living.remove(Entity.RemovalReason.KILLED);
            }
            if (!living.isRemoved()) {
                living.discard();
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

    /**
     * Force-kill an entity directly without going through hurt().
     * Used as a fallback for entities that override hurt() and block damage
     * (e.g. Twilight Forest Lich with active shield).
     */
    public static void forceKillEntity(LivingEntity target, ServerPlayer attacker) {
        if (target.isRemoved() || target.isDeadOrDying()) {
            return;
        }

        initReflection();

        // Set kill attribution flags so die() can correctly handle exp/loot drops
        setKillAttribution(target, attacker);

        int expectedExp = 0;
        ServerLevel serverLevel = target.level() instanceof ServerLevel sl ? sl : null;
        if (m_getExperienceReward != null && serverLevel != null) {
            try {
                Object result = m_getExperienceReward.invoke(target, serverLevel, attacker);
                if (result instanceof Integer exp) {
                    expectedExp = exp;
                }
            } catch (Exception ignored) { /* fallthrough */ }
        }

        if (target instanceof Player) {
            AttributeInstance maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(0.0001F);
            }
            target.setHealth(0.0F);
            if (maxHealth != null) {
                maxHealth.setBaseValue(Attributes.MAX_HEALTH.value().getDefaultValue());
            }
        } else {
            AttributeInstance maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(0.0F);
            }
            target.setHealth(0.0F);

            // Ensure kill attribution is set before die()
            setKillAttribution(target, attacker);
            DamageSource killSource = target.damageSources().playerAttack(attacker);
            if (!target.isRemoved()) {
                target.die(killSource);
            }
            if (!target.isRemoved() && !target.isDeadOrDying()) {
                target.setHealth(0.0F);
                setKillAttribution(target, attacker);
                if (!target.isRemoved()) {
                    target.die(killSource);
                }
            }
            if (!target.isRemoved()) {
                target.remove(Entity.RemovalReason.KILLED);
            }
            if (!target.isRemoved()) {
                target.discard();
            }

            if (expectedExp > 0 && serverLevel != null) {
                ExperienceOrb.award(serverLevel, target.position(), expectedExp);
            }
        }

        SecurityAuditLog.logSuccessfulEdit(attacker, "instant_kill_weapon",
                "force-killed " + target.getType().getDescriptionId() + " at " + target.blockPosition(), null);
    }

    /**
     * Called every server tick to force-kill entities that were attacked by an OP
     * with the instant-kill sword but survived due to hurt() being intercepted
     * (e.g. Lich shield, invulnerability, custom hurt() overrides).
     */
    public static void processTick(ServerLevel level) {
        if (level.isClientSide()) {
            return;
        }

        long currentTime = level.getGameTime();

        for (ServerPlayer player : level.players()) {
            if (!PermissionLogic.isAdmin(player)) {
                continue;
            }
            if (!isInstantKillWeapon(player.getMainHandItem())) {
                continue;
            }

            Iterable<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(64.0));

            for (LivingEntity entity : entities) {
                if (entity == player || entity.isRemoved() || entity.isDeadOrDying()) {
                    continue;
                }

                // Check if this entity was recently attacked by this player
                // lastHurtByMob may be null if hurt() was completely intercepted (e.g. Lich shield)
                LivingEntity lastHurt = entity.getLastHurtByMob();
                if (lastHurt == player) {
                    forceKillEntity(entity, player);
                    continue;
                }

                // Also check via lastHurtByMobTimestamp — if it was set by our reflection
                // but the field didn't get updated, we can still detect recent attacks
                if (f_lastHurtByMobTimestamp != null) {
                    try {
                        long timestamp = f_lastHurtByMobTimestamp.getLong(entity);
                        // Attack was within last 20 ticks (1 second)
                        if (timestamp > 0 && currentTime - timestamp <= 20) {
                            // Check if lastHurtByMob is the player or if we need to check via lastHurtByPlayer
                            if (lastHurt == null) {
                                // Try to detect via lastHurtByPlayer
                                if (f_lastHurtByPlayer != null) {
                                    Object lastHurtByPlayer = f_lastHurtByPlayer.get(entity);
                                    if (lastHurtByPlayer == player) {
                                        forceKillEntity(entity, player);
                                    }
                                }
                            }
                        }
                    } catch (IllegalAccessException ignored) { /* no-op */ }
                }
            }
        }
    }
}
