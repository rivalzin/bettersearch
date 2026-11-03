package com.rivalzin.bettersearch.mixin.sophisticated;

import com.rivalzin.bettersearch.client.SophisticatedScreenAccess;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Screen.class, AbstractContainerScreen.class})
public abstract class SophisticatedLifecycleMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void bettersearch$closeSophisticatedScreen(CallbackInfo ci) {
        if ((Object) this instanceof SophisticatedScreenAccess) {
            ((SophisticatedScreenAccess) this).bettersearch$closeSophisticatedSearch();
        }
    }
}
