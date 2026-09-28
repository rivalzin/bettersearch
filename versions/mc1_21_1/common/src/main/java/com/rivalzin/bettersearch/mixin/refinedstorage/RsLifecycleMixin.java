package com.rivalzin.bettersearch.mixin.refinedstorage;

import com.rivalzin.bettersearch.client.refinedstorage.RsScreenAccess;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class RsLifecycleMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void bettersearch$rsRemoved(CallbackInfo ci) {
        if ((Object) this instanceof RsScreenAccess) {
            ((RsScreenAccess) (Object) this).bettersearch$rsClose();
        }
    }
}
