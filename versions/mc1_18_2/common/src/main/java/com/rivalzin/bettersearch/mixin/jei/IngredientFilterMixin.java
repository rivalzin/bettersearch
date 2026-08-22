package com.rivalzin.bettersearch.mixin.jei;

import com.rivalzin.bettersearch.client.JeiSearch;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.ingredients.IngredientFilter;
import mezz.jei.common.search.IElementSearch;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = IngredientFilter.class, remap = false)
public abstract class IngredientFilterMixin {
    @Shadow
    @Final
    private IElementSearch elementSearch;

    @Inject(method = "getIngredientListUncached", at = @At("RETURN"), cancellable = true, require = 0)
    private void bettersearch$search(String filterText,
                                     CallbackInfoReturnable<List<ITypedIngredient<?>>> cir) {
        List<ITypedIngredient<?>> fromJei = cir.getReturnValue();
        if (fromJei == null) {
            return;
        }
        List<ITypedIngredient<?>> ours = JeiSearch.search(filterText, fromJei,
                elementSearch.getAllIngredients(), (IngredientFilter) (Object) this);
        if (ours != null) {
            cir.setReturnValue(ours);
        }
    }
}
