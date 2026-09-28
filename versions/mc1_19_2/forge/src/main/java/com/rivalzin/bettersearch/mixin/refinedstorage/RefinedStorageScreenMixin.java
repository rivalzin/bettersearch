package com.rivalzin.bettersearch.mixin.refinedstorage;

import com.rivalzin.bettersearch.client.refinedstorage.RefinedStorageSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Pseudo;
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.screen.grid.GridScreen", remap = false)
public abstract class RefinedStorageScreenMixin {
    @Inject(method = "tick(II)V", at = @At("TAIL"), remap = false)
    private void bettersearch$tick(int mouseX, int mouseY, CallbackInfo ci) {
        RefinedStorageSearch.tick();
    }
}
