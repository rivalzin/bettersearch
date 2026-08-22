package com.rivalzin.bettersearch.mixin.emi;

import com.rivalzin.bettersearch.client.EmiSearchBridge;
import dev.emi.emi.api.stack.EmiIngredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

@Mixin(targets = "dev.emi.emi.search.EmiSearch$SearchWorker", remap = false)
public abstract class SearchWorkerMixin {
    @Shadow
    @Final
    private String query;

    @Shadow
    @Final
    private List<? extends EmiIngredient> source;

    @ModifyArg(
            method = "run",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Ldev/emi/emi/search/EmiSearch;apply("
                            + "Ldev/emi/emi/search/EmiSearch$SearchWorker;Ljava/util/List;)V"),
            index = 1,
            require = 0)
    private List<? extends EmiIngredient> bettersearch$augment(List<? extends EmiIngredient> result) {
        List<? extends EmiIngredient> ours = EmiSearchBridge.search(query, result, source);
        return ours == null ? result : ours;
    }
}
