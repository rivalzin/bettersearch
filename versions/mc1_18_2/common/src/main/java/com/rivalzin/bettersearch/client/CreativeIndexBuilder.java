package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

public final class CreativeIndexBuilder {
    private static final int MAX_TOOLTIP_LINES = 6;

    private CreativeIndexBuilder() {
    }

    public static SearchIndex<ItemStack> build(List<ItemStack> stacks,
                                               LanguageTable languages,
                                               SearchSettings settings,
                                               Player player) {
        long start = System.nanoTime();
        List<SearchIndex.Entry<ItemStack>> entries = new ArrayList<>(stacks.size());

        List<String> codes = activeCodes(languages, settings);
        boolean englishSearched = englishSearched(codes);

        for (ItemStack stack : stacks) {
            try {
                entries.add(buildEntry(stack, languages, codes, settings, player, englishSearched));
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] skipped item: {}", BetterSearch.MOD_NAME, t.toString());
            }
        }

        SearchIndex<ItemStack> index = new SearchIndex<>(entries);
        BetterSearch.LOGGER.info("[{}] index ready: {} items in {} ms",
                BetterSearch.MOD_NAME, entries.size(), (System.nanoTime() - start) / 1_000_000);
        return index;
    }

    // the whole cost of the mod is here, once per item
    private static SearchIndex.Entry<ItemStack> buildEntry(ItemStack stack,
                                                           LanguageTable languages,
                                                           List<String> codes,
                                                           SearchSettings settings,
                                                           Player player,
                                                           boolean englishSearched) {
        EntryBuilder<ItemStack> builder = new EntryBuilder<>(stack);
        fill(builder, stack, languages, codes, settings, player, englishSearched);
        return builder.build();
    }

    public static List<String> activeCodes(LanguageTable languages, SearchSettings settings) {
        List<String> codes = new ArrayList<>();
        for (String code : languages.languageCodes()) {
            if (settings.indexesLanguage(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    public static boolean englishSearched(List<String> codes) {
        return codes.contains("en_us") || "en_us".equals(LanguageCatalog.currentCode());
    }

    @SuppressWarnings("deprecation")
    public static void fill(EntryBuilder<?> builder,
                            ItemStack stack,
                            LanguageTable languages,
                            List<String> codes,
                            SearchSettings settings,
                            Player player,
                            boolean englishSearched) {
        ResourceLocation id = Registry.ITEM.getKey(stack.getItem());
        builder.modId(id.getNamespace());
        builder.family(id.getPath());

        builder.add(stack.getHoverName().getString(), SearchField.SOURCE_NATIVE);

        if (settings.crossLanguage && !codes.isEmpty()) {
            String descriptionId = stack.getDescriptionId();
            for (String code : codes) {
                String translated = languages.get(code, descriptionId);
                if (translated != null) {
                    builder.add(translated, code.equals("en_us")
                            ? SearchField.SOURCE_ENGLISH
                            : SearchField.SOURCE_FOREIGN);
                }
            }
        }

        if (settings.searchItemIds) {
            builder.add(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                    SearchField.SOURCE_ID);
        }

        if (settings.searchTooltips && hasExtraData(stack)) {
            List<Component> lines = stack.getTooltipLines(player, TooltipFlag.Default.NORMAL);
            int limit = Math.min(lines.size(), MAX_TOOLTIP_LINES + 1);
            for (int i = 1; i < limit; i++) {
                builder.add(lines.get(i).getString(), SearchField.SOURCE_TOOLTIP);
            }
        }

        for (String alias : EasterEggs.aliasesFor(id.toString(), englishSearched)) {
            builder.add(alias, SearchField.SOURCE_NATIVE);
        }
    }

    // enchanted or named stacks get their own entry, plain ones share
    private static boolean hasExtraData(ItemStack stack) {
        return stack.hasTag();
    }
}
