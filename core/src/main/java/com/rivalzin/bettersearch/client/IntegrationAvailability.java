package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.FailurePolicy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class IntegrationAvailability {
    private static final String OWN = "com.rivalzin.bettersearch.";

    private IntegrationAvailability() {
    }

    public static boolean available(String id) {
        return Boolean.TRUE.equals(Loaded.VALUES.get(id));
    }

    public static boolean hasClass(String name) {
        try {
            return IntegrationAvailability.class.getClassLoader()
                    .getResource(name.replace('.', '/') + ".class") != null;
        } catch (RuntimeException | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            return false;
        }
    }

    static boolean detect(String id, Predicate<String> classes) {
        if (id == null) {
            return false;
        }
        switch (id) {
            case "jei":
                return all(classes, OWN + "mixin.jei.JeiMixinPlugin")
                        && any(classes, "mezz.jei.gui.ingredients.IngredientFilter",
                                "mezz.jei.common.ingredients.IngredientFilter",
                                "mezz.jei.ingredients.IngredientFilter")
                        || all(classes, OWN + "forge.jei.LegacyJeiHook",
                                "mezz.jei.ingredients.IngredientFilter", "mezz.jei.Internal")
                        && !classes.test("mezz.jei.search.TokenInfo");
            case "hei":
                return all(classes, OWN + "forge.jei.HeiSearchHook",
                        "mezz.jei.search.TokenInfo", "mezz.jei.ingredients.IngredientFilter",
                        "mezz.jei.Internal");
            case "nei":
                return classes.test(OWN + "forge.nei.NeiIntegration")
                        && (all(classes, "codechicken.nei.SearchField", "codechicken.nei.api.ItemFilter")
                        || all(classes, "codechicken.nei.widget.SearchField",
                                "codechicken.lib.item.filtering.IItemFilter"));
            case "emi":
                return all(classes, OWN + "mixin.emi.EmiMixinPlugin", "dev.emi.emi.search.EmiSearch");
            case "rei":
                return classes.test(OWN + "mixin.rei.ReiMixinPlugin")
                        && any(classes, "me.shedaniel.rei.impl.client.search.SearchProviderImpl",
                                "me.shedaniel.rei.gui.widget.EntryListWidget");
            case "ae2":
                return all(classes, OWN + "client.Ae2Search", "appeng.client.me.ItemRepo")
                        || all(classes, OWN + "mixin.ae2.Ae2MixinPlugin",
                                "appeng.client.gui.me.common.Repo");
            case "toms_storage":
                return classes.test(OWN + "mixin.toms.TomsStorageMixinPlugin")
                        && any(classes, "com.tom.storagemod.screen.AbstractStorageTerminalScreen",
                                "com.tom.storagemod.gui.AbstractStorageTerminalScreen",
                                "com.tom.storagemod.gui.GuiStorageTerminalBase");
            case "refined_storage":
                return classes.test(OWN + "client.refinedstorage.RefinedStorageSearch")
                        && (all(classes, "com.raoulvdberge.refinedstorage.gui.grid.view.GridViewBase",
                                "com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterName")
                        || all(classes, "com.refinedmods.refinedstorage.screen.grid.view.GridViewImpl",
                                "com.refinedmods.refinedstorage.screen.grid.filtering.NameGridFilter"))
                        || all(classes, OWN + "client.refinedstorage.RsStorageSearch",
                                "com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen",
                                "com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu",
                                "com.refinedmods.refinedstorage.common.grid.query.GridQueryParser",
                                "com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryImpl");
            default:
                return false;
        }
    }

    private static boolean all(Predicate<String> classes, String... names) {
        for (String name : names) {
            if (!classes.test(name)) {
                return false;
            }
        }
        return true;
    }

    private static boolean any(Predicate<String> classes, String... names) {
        for (String name : names) {
            if (classes.test(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean legacyAe2Compatible() {
        String transformer = OWN + "forge.ae2.Ae2Transformer";
        if (!hasClass(transformer)) {
            return true;
        }
        try {
            Class<?> type = Class.forName(transformer, false, IntegrationAvailability.class.getClassLoader());
            return Boolean.TRUE.equals(type.getMethod("supportsRepository").invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            return false;
        }
    }

    private static final class Loaded {
        static final Map<String, Boolean> VALUES = load();

        private static Map<String, Boolean> load() {
            Map<String, Boolean> values = new HashMap<>();
            for (String id : new String[]{"jei", "hei", "nei", "emi", "rei", "ae2",
                    "toms_storage", "refined_storage"}) {
                boolean available = detect(id, IntegrationAvailability::hasClass);
                if (available && "ae2".equals(id)) {
                    available = legacyAe2Compatible();
                }
                values.put(id, available);
            }
            return Collections.unmodifiableMap(values);
        }
    }
}
