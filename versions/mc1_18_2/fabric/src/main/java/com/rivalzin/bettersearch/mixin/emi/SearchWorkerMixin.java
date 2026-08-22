package com.rivalzin.bettersearch.mixin.emi;

import com.rivalzin.bettersearch.client.EmiSearchBridge;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

@Mixin(targets = "dev.emi.emi.search.EmiSearch$SearchWorker", remap = false)
public abstract class SearchWorkerMixin {
    @ModifyArg(method = "run",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Ldev/emi/emi/search/EmiSearch;apply(Ljava/util/List;)V"),
            index = 0, require = 0)
    private List<? extends EmiIngredient> bettersearch$augment(List<? extends EmiIngredient> result) {
        List<? extends EmiIngredient> ours = EmiSearchBridge.search(
                EmiSearchAccessor.bettersearch$query(), result, EmiScreenManager.getSearchSource());
        return ours == null ? result : ours;
    }
}
