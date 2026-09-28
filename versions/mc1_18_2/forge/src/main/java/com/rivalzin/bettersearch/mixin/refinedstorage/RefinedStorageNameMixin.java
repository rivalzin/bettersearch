package com.rivalzin.bettersearch.mixin.refinedstorage;

import com.rivalzin.bettersearch.client.refinedstorage.RefinedStorageSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.screen.grid.filtering.NameGridFilter", remap = false)
public abstract class RefinedStorageNameMixin {
    @Shadow @Final private String name;
    @Inject(method = "test(Lcom/refinedmods/refinedstorage/screen/grid/stack/IGridStack;)Z",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void bettersearch$name(@Coerce Object stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(RefinedStorageSearch.matches(cir.getReturnValueZ(), name, stack));
    }
}
