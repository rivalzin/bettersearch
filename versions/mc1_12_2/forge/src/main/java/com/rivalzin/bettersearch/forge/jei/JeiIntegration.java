package com.rivalzin.bettersearch.forge.jei;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.ModConfig;
import mezz.jei.Internal;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.suffixtree.CombinedSearchTrees;

import java.lang.reflect.Field;

public final class JeiIntegration {
    private static Field treesField;
    private static boolean announced;
    private static int appliedStamp = -1;
    private static int appliedGeneration = -1;

    private JeiIntegration() {
    }

    public static void install() throws Exception {
        IngredientFilter filter = Internal.getIngredientFilter();
        if (filter == null) {
            return;
        }
        if (treesField == null) {
            // JEI 4.16 has no api for this, the tree field is swapped directly
            treesField = IngredientFilter.class.getDeclaredField("combinedSearchTrees");
            treesField.setAccessible(true);
        }
        Object current = treesField.get(filter);
        boolean changed = false;
        if (current != null && !(current instanceof JeiSearchTree)) {
            treesField.set(filter, new JeiSearchTree((CombinedSearchTrees) current, filter));
            changed = true;
            if (!announced) {
                announced = true;
                BetterSearch.LOGGER.info("[{}] JEI search hooked (wrapped tree, no mixin)",
                        BetterSearch.MOD_NAME);
            }
        }

        int stamp = ModConfig.stamp();
        // the config stamp misses the language table and the off-thread build
        int generation = JeiSearchBridge.generation();
        if (changed || stamp != appliedStamp || generation != appliedGeneration) {
            appliedStamp = stamp;
            appliedGeneration = generation;
            filter.invalidateCache();
        }
    }
}
