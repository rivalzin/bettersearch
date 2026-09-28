package com.rivalzin.bettersearch.forge.jei;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.ModConfig;
import com.rivalzin.bettersearch.client.LangTable;
import net.minecraft.client.Minecraft;
import mezz.jei.Internal;
import mezz.jei.ingredients.IngredientFilter;

import java.lang.reflect.Field;

public final class JeiIntegration {
    private static Field filterField;
    private static Field searchField;
    private static boolean legacy;
    private static IngredientFilter appliedFilter;
    private static boolean announced;
    private static int appliedStamp = -1;
    private static int appliedGeneration = -1;
    private static int appliedLanguageStamp = -1;
    private static String appliedLanguage = "";
    private static boolean appliedEnabled;

    private JeiIntegration() {
    }

    public static void install() throws Exception {
        if (filterField == null) {
            filterField = Internal.class.getDeclaredField("ingredientFilter");
            filterField.setAccessible(true);
        }
        IngredientFilter filter = (IngredientFilter) filterField.get(null);
        if (filter == null) {
            return;
        }
        if (searchField == null) {
            searchField = HeiSearchHook.findField(IngredientFilter.class, "combinedSearchTrees");
            legacy = searchField != null;
            if (!legacy) {
                searchField = HeiSearchHook.findField(IngredientFilter.class, "elementSearch");
            }
            if (searchField == null) {
                throw new NoSuchFieldException("No supported JEI/HEI search engine");
            }
        }
        boolean changed = legacy ? LegacyJeiHook.install(filter, searchField)
                : HeiSearchHook.install(filter, searchField);
        if (changed && !announced) {
            announced = true;
            BetterSearch.LOGGER.info("[{}] {} search hooked", BetterSearch.MOD_NAME, legacy ? "JEI" : "HEI");
        }

        int stamp = ModConfig.stamp();

        int generation = JeiSearchBridge.generation();
        int languageStamp = LangTable.stamp();
        String language = Minecraft.getMinecraft().gameSettings.language;
        boolean enabled = JeiSearchBridge.enabledViewer(legacy ? "jei" : "hei", ModConfig.settings());
        if (changed || filter != appliedFilter || stamp != appliedStamp || generation != appliedGeneration
                || languageStamp != appliedLanguageStamp || !language.equals(appliedLanguage)
                || enabled != appliedEnabled) {
            appliedFilter = filter;
            appliedStamp = stamp;
            appliedGeneration = generation;
            appliedLanguageStamp = languageStamp;
            appliedLanguage = language;
            appliedEnabled = enabled;
            filter.invalidateCache();
        }
    }
}
