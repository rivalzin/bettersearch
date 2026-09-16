package com.rivalzin.bettersearch.mixin;

import com.rivalzin.bettersearch.client.KeyConflictGuard;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void bettersearch$standDown(CallbackInfoReturnable<Boolean> cir) {
        if (KeyConflictGuard.holdsBack((KeyMapping) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "consumeClick", at = @At("RETURN"), cancellable = true)
    private void bettersearch$dropClick(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && KeyConflictGuard.holdsBack((KeyMapping) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
