package com.rivalzin.bettersearch.mixin.rei;

import com.rivalzin.bettersearch.client.ReiSearch;
import me.shedaniel.rei.api.client.search.SearchFilter;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.impl.client.search.AsyncSearchManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = AsyncSearchManager.class, remap = false)
public abstract class AsyncSearchManagerMixin {
    @Shadow
    private SearchFilter filter;

    @Inject(method = "get()Ljava/util/List;", at = @At("RETURN"), cancellable = true, require = 0)
    private void bettersearch$order(CallbackInfoReturnable<List<EntryStack<?>>> cir) {
        List<EntryStack<?>> found = cir.getReturnValue();
        if (found == null || filter == null) {
            return;
        }
        ReiSearch.rememberManager((AsyncSearchManager) (Object) this);

        List<EntryStack<?>> ours = ReiSearch.reorder(filter, found, entry -> entry);
        if (ours != null) {
            cir.setReturnValue(ours);
        }
    }
}
