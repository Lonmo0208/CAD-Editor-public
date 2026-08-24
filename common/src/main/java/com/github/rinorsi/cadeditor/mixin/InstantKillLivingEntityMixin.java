package com.github.rinorsi.cadeditor.mixin;

import com.github.rinorsi.cadeditor.common.logic.InstantKillLogic;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side: intercepts living entity (incl. player) hurt, checks if attacker holds a forced-kill weapon.
 * - OP4 player: triggers forced kill (instant kill)
 * - Normal player: clamps damage to a fixed 4 points then re-applies (re-entry safe)
 */
@Mixin(LivingEntity.class)
public abstract class InstantKillLivingEntityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void cadeditor$onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        // Re-entry guard: re-applying clamped damage, go through normal flow
        if (InstantKillLogic.isApplyingClampedDamage()) {
            return;
        }
        // OP4 holding OP sword: forced kill
        if (InstantKillLogic.tryInstantKill(self, source)) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }
        // Normal player holding OP sword: clamp to 4 points and re-apply this damage
        float clamped = InstantKillLogic.clampDamageForNormalPlayer(source, amount);
        if (clamped != amount) {
            InstantKillLogic.setApplyingClampedDamage(true);
            try {
                boolean result = self.hurt(source, clamped);
                cir.setReturnValue(result);
                cir.cancel();
            } finally {
                InstantKillLogic.setApplyingClampedDamage(false);
            }
        }
    }
}
