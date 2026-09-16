package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class CreativeIndex {
    private static final int MAX_TOOLTIP_LINES = 6;

    private CreativeIndex() {
    }

    public static Supplier<SearchIndex<ItemStack>> prepare(List<ItemStack> source, SearchSettings settings) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        List<String> codes = LangTable.activeCodes(settings);
        return EntrySnapshot.capture(source, stack -> {
            try {
                List<String> tooltip = null;
                if (settings.searchTooltips && player != null) {
                    tooltip = new ArrayList<>();
                    for (Object line : stack.getTooltip(player, net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL)) {
                        tooltip.add(String.valueOf(line));
                    }
                }
                EntrySnapshot<ItemStack> entry = new EntrySnapshot<>(stack);
                fill(entry, stack, settings, codes, stack.getDisplayName(), tooltip);
                return entry;
            } catch (RuntimeException | LinkageError error) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
                BetterSearch.LOGGER.debug("[{}] skipped item while capturing: {}",
                        BetterSearch.MOD_NAME, error.toString());
                return null;
            }
        });
    }

    static void fill(EntrySnapshot<?> builder, ItemStack stack, SearchSettings settings,
                     List<String> codes, String displayName, List<String> tooltip) {
        ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
        if (id != null) {
            builder.modId(id.getNamespace());
            builder.family(id.getPath());
        }

        if (displayName != null) {
            builder.add(displayName, SearchField.SOURCE_NATIVE);
        }

        if (settings.crossLanguage) {
            String key = stack.getTranslationKey() + ".name";
            for (String code : codes) {
                String translated = LangTable.get(code, key);
                if (translated != null) {
                    builder.add(translated, "en_us".equalsIgnoreCase(code)
                            ? SearchField.SOURCE_ENGLISH
                            : SearchField.SOURCE_FOREIGN);
                }
            }
        }

        if (settings.searchItemIds && id != null) {
            builder.add(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                    SearchField.SOURCE_ID);
        }

        if (tooltip != null) {
            int limit = Math.min(tooltip.size(), MAX_TOOLTIP_LINES + 1);
            for (int i = 1; i < limit; i++) {
                builder.add(tooltip.get(i), SearchField.SOURCE_TOOLTIP);
            }
        }
    }
}
