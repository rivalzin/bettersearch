package com.rivalzin.bettersearch.mixin.refinedstorage;

import com.rivalzin.bettersearch.client.refinedstorage.RsApi;
import com.rivalzin.bettersearch.client.refinedstorage.RsRepositoryAccess;
import com.rivalzin.bettersearch.client.refinedstorage.RsScreenAccess;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen", remap = false)
public abstract class RsScreenMixin implements RsScreenAccess {
    @Unique
    private RsRepositoryAccess bettersearch$rsRepository;

    @Inject(method = "trySynchronizeToGrid", at = @At("HEAD"), require = 1, allow = 1)
    private void bettersearch$rsUpdate(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (bettersearch$rsRepository == null) {
            bettersearch$rsRepository = RsApi.repository(screen.getMenu());
        }
        if (bettersearch$rsRepository != null) {
            bettersearch$rsRepository.bettersearch$rsOpen(screen);
            bettersearch$rsRepository.bettersearch$rsTick();
        }
    }

    @Override
    public void bettersearch$rsClose() {
        if (bettersearch$rsRepository != null) {
            bettersearch$rsRepository.bettersearch$rsClose();
            bettersearch$rsRepository = null;
        }
    }
}
