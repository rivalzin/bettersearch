package com.rivalzin.bettersearch.mixin.jei;

import com.rivalzin.bettersearch.client.JeiSearch;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.search.IElementSearch;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Stream;

@Mixin(value = IngredientFilter.class, remap = false)
public abstract class IngredientFilterMixin {
    @Shadow
    @Final
    private IIngredientManager ingredientManager;

    @Shadow
    private IElementSearch elementSearch;

    @Inject(method = "getIngredientListUncached", at = @At("RETURN"), cancellable = true, require = 0)
    private void bettersearch$search(String filterText,
                                     CallbackInfoReturnable<Stream<ITypedIngredient<?>>> cir) {
        Stream<ITypedIngredient<?>> original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        List<ITypedIngredient<?>> fromJei = original.toList();
        List<ITypedIngredient<?>> ours = JeiSearch.search(filterText, fromJei,
                elementSearch.getAllIngredients(), ingredientManager,
                (IngredientFilter) (Object) this);
        cir.setReturnValue((ours == null ? fromJei : ours).stream());
    }
}
