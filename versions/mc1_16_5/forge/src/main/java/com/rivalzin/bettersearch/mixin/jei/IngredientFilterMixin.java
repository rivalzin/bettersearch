package com.rivalzin.bettersearch.mixin.jei;

import com.rivalzin.bettersearch.client.JeiSearch;
import mezz.jei.ingredients.IIngredientListElementInfo;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.search.IElementSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = IngredientFilter.class, remap = false)
public abstract class IngredientFilterMixin {
    @Shadow
    private IElementSearch elementSearch;

    @Inject(method = "getIngredientListUncached(Ljava/lang/String;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void bettersearch$search(String filterText,
                                     CallbackInfoReturnable<List<IIngredientListElementInfo<?>>> cir) {
        List<IIngredientListElementInfo<?>> fromJei = cir.getReturnValue();
        if (fromJei == null) {
            return;
        }
        if (elementSearch == null) {
            return;
        }
        List<IIngredientListElementInfo<?>> ours = JeiSearch.search(filterText, fromJei,
                elementSearch.getAllIngredients(), (IngredientFilter) (Object) this);
        if (ours != null) {
            cir.setReturnValue(ours);
        }
    }
}
