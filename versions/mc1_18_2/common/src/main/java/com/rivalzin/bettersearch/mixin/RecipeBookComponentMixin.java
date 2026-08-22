package com.rivalzin.bettersearch.mixin;

import com.rivalzin.bettersearch.client.RecipeSearch;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.searchtree.MutableSearchTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    // the recipe book rebuilds its list here, after the search box changed
    @Inject(method = "tick", at = @At("HEAD"))
    private void bettersearch$prepareIndex(CallbackInfo ci) {
        RecipeSearch.prepare();
    }

    @Redirect(
            method = "updateCollections",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/searchtree/MutableSearchTree;"
                            + "search(Ljava/lang/String;)Ljava/util/List;"))
    private List<RecipeCollection> bettersearch$searchRecipes(MutableSearchTree<RecipeCollection> tree,
                                                             String query) {
        List<RecipeCollection> ours = RecipeSearch.search(query);
        return ours != null ? ours : tree.search(query);
    }
}
