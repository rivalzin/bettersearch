package com.rivalzin.bettersearch.mixin.rei;

import com.rivalzin.bettersearch.client.ReiSearch;
import me.shedaniel.rei.api.client.search.SearchFilter;
import me.shedaniel.rei.impl.client.search.SearchProviderImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SearchProviderImpl.class, remap = false)
public abstract class SearchProviderImplMixin {
    @Inject(method = "createFilter(Ljava/lang/String;)Lme/shedaniel/rei/api/client/search/SearchFilter;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void bettersearch$wrap(String searchTerm, CallbackInfoReturnable<SearchFilter> cir) {
        SearchFilter original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        SearchFilter wrapped = ReiSearch.wrap(original);
        if (wrapped != null && wrapped != original) {
            cir.setReturnValue(wrapped);
        }
    }
}
