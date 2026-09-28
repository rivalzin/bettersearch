package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.Ae2ScreenAccess;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin({Screen.class, AbstractContainerScreen.class})
public abstract class Ae2LifecycleMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void bettersearch$closeAe2(CallbackInfo ci) {
        if ((Object) this instanceof Ae2ScreenAccess) {
            ((Ae2ScreenAccess) (Object) this).bettersearch$closeAe2();
        }
    }
}
