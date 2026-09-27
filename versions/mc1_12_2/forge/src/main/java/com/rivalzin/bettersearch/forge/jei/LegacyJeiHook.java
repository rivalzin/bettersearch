package com.rivalzin.bettersearch.forge.jei;

import java.lang.reflect.Field;
import java.util.List;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.suffixtree.CombinedSearchTrees;

final class LegacyJeiHook {
    private LegacyJeiHook() {
    }

    static boolean install(IngredientFilter filter, Field treesField) throws Exception {
        Object current = treesField.get(filter);
        if (current == null || current instanceof JeiSearchTree) {
            return false;
        }
        Field elements = IngredientFilter.class.getDeclaredField("elementList");
        elements.setAccessible(true);
        treesField.set(filter, new JeiSearchTree((CombinedSearchTrees) current,
                () -> (List<?>) elements.get(filter)));
        return true;
    }
}
