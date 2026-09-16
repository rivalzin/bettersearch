package com.rivalzin.bettersearch.forge.jei;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.ModConfig;
import com.rivalzin.bettersearch.client.LangTable;
import net.minecraft.client.Minecraft;
import mezz.jei.Internal;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.suffixtree.CombinedSearchTrees;

import java.lang.reflect.Field;

public final class JeiIntegration {
    private static Field treesField;
    private static boolean announced;
    private static int appliedStamp = -1;
    private static int appliedGeneration = -1;
    private static int appliedLanguageStamp = -1;
    private static String appliedLanguage = "";

    private JeiIntegration() {
    }

    public static void install() throws Exception {
        IngredientFilter filter = Internal.getIngredientFilter();
        if (filter == null) {
            return;
        }
        if (treesField == null) {

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

        int generation = JeiSearchBridge.generation();
        int languageStamp = LangTable.stamp();
        String language = Minecraft.getMinecraft().gameSettings.language;
        if (changed || stamp != appliedStamp || generation != appliedGeneration
                || languageStamp != appliedLanguageStamp || !language.equals(appliedLanguage)) {
            appliedStamp = stamp;
            appliedGeneration = generation;
            appliedLanguageStamp = languageStamp;
            appliedLanguage = language;
            filter.invalidateCache();
        }
    }
}
