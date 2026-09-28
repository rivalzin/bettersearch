package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.Ae2Search;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.common.Repo", remap = false)
public abstract class Ae2RepositoryMixin {
    @Inject(method = "updateView", at = @At("HEAD"), remap = false, require = 1, allow = 1)
    private void bettersearch$begin(CallbackInfo ci) {
        Ae2Search.beginRepository(this);
    }
}
