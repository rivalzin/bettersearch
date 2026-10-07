package com.rivalzin.bettersearch.client.simplestorage;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.client.CreativeIndex;
import com.rivalzin.bettersearch.client.LangTable;
import com.rivalzin.bettersearch.client.ModConfig;
import com.rivalzin.bettersearch.compat.SimpleStorageController;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

public final class SimpleStorageSearch {
    private static final SimpleStorageController CONTROLLER = new SimpleStorageController(new Platform());

    private SimpleStorageSearch() {
    }

    public static void begin(Object widget, List<?> stacks, Object searchBar) {
        CONTROLLER.begin(widget, stacks, searchBar instanceof GuiTextField ? ((GuiTextField) searchBar).getText() : "");
    }

    public static boolean matches(boolean nativeMatch, Object widget, Object stack) {
        return CONTROLLER.matches(nativeMatch, widget, stack);
    }

    public static void changed(Object widget) { CONTROLLER.changed(widget); }
    public static void closeInactive() { CONTROLLER.closeInactive(); }

    private static final class Platform implements SimpleStorageController.Platform {
        @Override
        public Object screen() { return Minecraft.getMinecraft().currentScreen; }
        @Override
        public SearchSettings settings() { return ModConfig.settings(); }
        @Override
        public long languageRevision(SearchSettings settings) {
            if (settings.enabled && settings.crossLanguage) LangTable.ensure(settings);
            return LangTable.stamp();
        }
        @Override
        public Object key(Object value) {
            return value instanceof ItemStack && !((ItemStack) value).isEmpty() ? new StackKey((ItemStack) value) : null;
        }
        @Override
        public Object snapshot(Object value) {
            ItemStack stack = ((StackKey) value).stack.copy();
            stack.setCount(1);
            return new StackKey(stack);
        }
        @Override
        public Executor client() { return task -> Minecraft.getMinecraft().addScheduledTask(task); }
        @Override
        public Executor worker() { return ForkJoinPool.commonPool(); }
        @Override
        public void report(Throwable error) {
            FailurePolicy.rethrowFatal(error);
            BetterSearch.LOGGER.debug("[{}] Simple Storage Network search fallback: {}", BetterSearch.MOD_NAME, error.toString());
        }
        @Override
        public Supplier<SearchIndex<Object>> prepare(List<Object> items, SearchSettings settings) {
            List<String> codes = LangTable.activeCodes(settings);
            net.minecraft.entity.player.EntityPlayer player = Minecraft.getMinecraft().player;
            return EntrySnapshot.capture(items, value -> {
                try {
                    ItemStack stack = ((StackKey) value).stack;
                    List<String> tooltip = settings.searchTooltips && player != null
                            ? stack.getTooltip(player, ITooltipFlag.TooltipFlags.NORMAL) : null;
                    EntrySnapshot<Object> entry = new EntrySnapshot<>(value);
                    CreativeIndex.fill(entry, stack, settings, codes, stack.getDisplayName(), tooltip);
                    return entry;
                } catch (RuntimeException | LinkageError error) {
                    report(error);
                    return null;
                }
            }, false);
        }
    }

    private static final class StackKey {
        private final ItemStack stack;
        private final Object item;
        private final int metadata;
        private final Object data;
        private final int hash;

        private StackKey(ItemStack stack) {
            this.stack = stack;
            item = stack.getItem();
            metadata = stack.getMetadata();
            data = stack.getTagCompound();
            hash = 31 * (31 * System.identityHashCode(item) + metadata) + Objects.hashCode(data);
        }

        @Override
        public int hashCode() { return hash; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StackKey)) return false;
            StackKey key = (StackKey) other;
            return item == key.item && metadata == key.metadata && Objects.equals(data, key.data);
        }
    }
}
