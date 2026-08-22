package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CreativeIndex {
    // some tooltips are enormous, cap what goes into the index
    private static final int MAX_TOOLTIP_LINES = 6;

    private CreativeIndex() {
    }

    public static SearchIndex<ItemStack> build(List<ItemStack> source, SearchSettings settings) {
        long started = System.nanoTime();
        List<SearchIndex.Entry<ItemStack>> entries = new ArrayList<SearchIndex.Entry<ItemStack>>(source.size());
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;

        for (ItemStack stack : source) {
            try {
                EntryBuilder<ItemStack> builder = new EntryBuilder<ItemStack>(stack);
                fill(builder, stack, settings, player);
                entries.add(builder.build());
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] skipped item: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }

        SearchIndex<ItemStack> index = new SearchIndex<ItemStack>(entries);
        BetterSearch.LOGGER.info("[{}] creative index ready (1.7.10): {} items in {} ms",
                BetterSearch.MOD_NAME, entries.size(), (System.nanoTime() - started) / 1_000_000);
        return index;
    }

    static void fill(EntryBuilder<?> builder, ItemStack stack,
                                  SearchSettings settings, EntityPlayer player) {
        String id = Item.itemRegistry.getNameForObject(stack.getItem());
        String domain = null;
        String path = null;
        if (id != null) {
            int colon = id.indexOf(':');
            domain = colon > 0 ? id.substring(0, colon) : "minecraft";
            path = colon > 0 ? id.substring(colon + 1) : id;
            builder.modId(domain);
        }

        builder.add(stack.getDisplayName(), SearchField.SOURCE_NATIVE);

        if (settings.crossLanguage) {
            String key = stack.getUnlocalizedName() + ".name";
            for (String code : LangTable.activeCodes(settings)) {
                String translated = LangTable.get(code, key);
                if (translated != null) {
                    builder.add(translated, "en_us".equalsIgnoreCase(code)
                            ? SearchField.SOURCE_ENGLISH
                            : SearchField.SOURCE_FOREIGN);
                }
            }
        }

        if (settings.searchItemIds && domain != null) {
            builder.addNormalized(domain + ' ' + path.replace('_', ' '),
                    SearchField.SOURCE_ID);
        }

        if (settings.searchTooltips && player != null && stack.hasTagCompound()) {
            List<?> lines = stack.getTooltip(player, false);
            int limit = Math.min(lines.size(), MAX_TOOLTIP_LINES + 1);
            for (int i = 1; i < limit; i++) {
                builder.add(String.valueOf(lines.get(i)), SearchField.SOURCE_TOOLTIP);
            }
        }
    }
}
