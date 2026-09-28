package com.rivalzin.bettersearch.mixin.refinedstorage;

import com.rivalzin.bettersearch.client.refinedstorage.RefinedStorageSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
@Mixin({Screen.class, AbstractContainerScreen.class})
public abstract class RefinedStorageLifecycleMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void bettersearch$close(CallbackInfo ci) {
        RefinedStorageSearch.close(this);
    }
}
