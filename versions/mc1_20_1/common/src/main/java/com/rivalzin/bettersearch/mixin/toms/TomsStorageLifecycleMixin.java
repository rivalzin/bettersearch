package com.rivalzin.bettersearch.mixin.toms;

import com.rivalzin.bettersearch.client.TomsStorageScreenAccess;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class TomsStorageLifecycleMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void bettersearch$closeStorageSearch(CallbackInfo ci) {
        if ((Object) this instanceof TomsStorageScreenAccess) {
            ((TomsStorageScreenAccess) (Object) this).bettersearch$closeSearch();
        }
    }
}
