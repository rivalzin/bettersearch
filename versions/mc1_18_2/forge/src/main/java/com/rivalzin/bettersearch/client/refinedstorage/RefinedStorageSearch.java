package com.rivalzin.bettersearch.client.refinedstorage;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.*;
import com.rivalzin.bettersearch.compat.RefinedStorageController;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;

public final class RefinedStorageSearch {
    private static final RefinedStorageController CONTROLLER = new RefinedStorageController(new Platform());

    private RefinedStorageSearch() {
    }

    public static void begin(Object view) { CONTROLLER.begin(view); }
    public static <T> Predicate<T> bind(Object view, Predicate<T> original) { return CONTROLLER.bind(view, original); }
    public static <T> boolean test(Predicate<T> original, T value, Object view) { return CONTROLLER.test(original, value, view); }
    public static boolean matches(boolean original, String term, Object value) { return CONTROLLER.matches(original, term, value); }
    public static void changed(Object view) { CONTROLLER.changed(view); }
    public static void delta(Object view) { CONTROLLER.delta(view); }
    public static void tick() { CONTROLLER.tick(); }
    public static void close(Object screen) { CONTROLLER.closeScreen(screen); }

    private static final class Platform implements RefinedStorageController.Platform {
        @Override
        public Object screen() { return Minecraft.getInstance().screen; }
        @Override
        public SearchSettings settings() {
            SearchSettings settings = BetterSearchClient.settings();
            settings.enabled &= settings.searchRefinedStorage;
            return settings;
        }
        @Override
        public long languageRevision(SearchSettings settings) {
            if (settings.enabled && settings.crossLanguage) BetterSearchClient.ensureLanguagesLoaded();
            return BetterSearchClient.languageStamp();
        }
        @Override
        public String query(Object screen) {
            Object text = RefinedStorageController.call(screen, "getSearchFieldText");
            return text == null ? "" : String.valueOf(text);
        }
        @Override
        public Executor client() { return task -> Minecraft.getInstance().execute(task); }
        @Override
        public Executor worker() { return ForkJoinPool.commonPool(); }
        @Override
        public void report(Throwable error) {
            BetterSearch.LOGGER.debug("[{}] Refined Storage search fallback: {}", BetterSearch.MOD_NAME, error.toString());
        }
        @Override
        public void fill(EntrySnapshot<Object> entry, Object gridStack, SearchSettings settings) {
            Object value = RefinedStorageController.call(gridStack, "getStack");
            if (value instanceof ItemStack) {
                ItemStack stack = ((ItemStack) value).copy();
                stack.setCount(1);
                CreativeIndexBuilder.fill(entry, stack, BetterSearchClient.languages(),
                        CreativeIndexBuilder.activeCodes(BetterSearchClient.languages(), settings),
                        settings, Minecraft.getInstance().player,
                        CreativeIndexBuilder.englishSearched(CreativeIndexBuilder.activeCodes(
                                BetterSearchClient.languages(), settings)));
            } else if (value instanceof FluidStack) {
                FluidStack fluid = ((FluidStack) value).copy();
                entry.add(fluid.getDisplayName().getString(), SearchField.SOURCE_NATIVE);
                String key = fluid.getTranslationKey();
                if (settings.crossLanguage) {
                    for (String code : CreativeIndexBuilder.activeCodes(BetterSearchClient.languages(), settings)) {
                        entry.add(BetterSearchClient.languages().get(code, key), code.equals("en_us")
                                ? SearchField.SOURCE_ENGLISH : SearchField.SOURCE_FOREIGN);
                    }
                }
            }
            Object name = RefinedStorageController.call(gridStack, "getName");
            if (name != null) entry.add(String.valueOf(name), SearchField.SOURCE_NATIVE);
        }
    }
}
