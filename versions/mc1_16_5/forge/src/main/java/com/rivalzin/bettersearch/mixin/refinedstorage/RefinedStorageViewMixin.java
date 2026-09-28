package com.rivalzin.bettersearch.mixin.refinedstorage;

import com.rivalzin.bettersearch.client.refinedstorage.RefinedStorageSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.screen.grid.view.GridViewImpl", remap = false)
public abstract class RefinedStorageViewMixin {
    @Inject(method = "getActiveFilters", at = @At("RETURN"), cancellable = true, remap = false)
    private void bettersearch$filters(CallbackInfoReturnable<Predicate<Object>> cir) {
        cir.setReturnValue(RefinedStorageSearch.bind(this, cir.getReturnValue()));
    }
    @Inject(method = "setStacks", at = @At("RETURN"), remap = false)
    private void bettersearch$changed(CallbackInfo ci) {
        RefinedStorageSearch.changed(this);
    }
    @Inject(method = "postChange", at = @At("RETURN"), remap = false)
    private void bettersearch$delta(CallbackInfo ci) {
        RefinedStorageSearch.delta(this);
    }
}
