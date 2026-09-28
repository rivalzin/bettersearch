package com.rivalzin.bettersearch.mixin.refinedstorage;

import com.rivalzin.bettersearch.client.refinedstorage.RsApi;
import com.rivalzin.bettersearch.client.refinedstorage.RsRepositoryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu", remap = false)
public abstract class RsMenuMixin {
    @Inject(method = "onSearchTextChanged", at = @At("HEAD"), require = 1, allow = 1)
    private void bettersearch$rsQuery(String query, CallbackInfoReturnable<Boolean> cir) {
        RsRepositoryAccess repository = RsApi.repository(this);
        if (repository != null) {
            repository.bettersearch$rsQuery(query);
        }
    }
}
