package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.search.EmiSearch;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EmiSearchBridge {
    private static final String EMI_SYNTAX = "#$/|";

    private static final com.rivalzin.bettersearch.async.SourceSnapshot<EmiIngredient> SOURCES =
            new com.rivalzin.bettersearch.async.SourceSnapshot<>();
    private static final AsyncIndex<EmiIngredient> INDEX = new AsyncIndex<>("EMI ingredients");

    static {

        BetterSearchClient.onInvalidate(EmiSearchBridge::invalidate);
        BetterSearchClient.onSettingsApplied(() -> {
            try {
                EmiSearch.update();
            } catch (RuntimeException | LinkageError ignored) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(ignored);
            }
        });
    }

    private EmiSearchBridge() {
    }

    public static void invalidate() {
        INDEX.invalidate();
        SOURCES.clear();
    }

    public static List<? extends EmiIngredient> search(String query,
                                                       List<? extends EmiIngredient> result,
                                                       List<? extends EmiIngredient> source) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchEmi) {
                return null;
            }
            if (query == null || query.isBlank() || source == null || source.isEmpty()) {
                return null;
            }
            if (usesEmiSyntax(query)) {
                return null;
            }

            SearchIndex<EmiIngredient> ready = ensureIndex(source, settings);
            if (ready == null) {
                return null;
            }
            SearchQuery parsed = SearchQuery.parse(query, settings);
            if (parsed.isEmpty()) {
                return null;
            }

            if ((parsed.isBrowseOnly() || SearchQuery.isBrowsingByMod(query))
                    && result != null && !result.isEmpty()) {
                return null;
            }

            List<EmiIngredient> ours = ready.search(parsed, settings);
            if (result == null || result.isEmpty()) {
                return ours.isEmpty() ? null : List.copyOf(ours);
            }

            List<EmiIngredient> merged = new ArrayList<>(ours.size() + result.size());
            if (settings.sortByRelevance) {
                Set<EmiIngredient> seen = new HashSet<>(ours);
                merged.addAll(ours);
                for (EmiIngredient ingredient : result) {
                    if (seen.add(ingredient)) {
                        merged.add(ingredient);
                    }
                }
            } else {
                Set<EmiIngredient> fromEmi = new HashSet<>(result);
                merged.addAll(result);
                for (EmiIngredient ingredient : ours) {
                    if (fromEmi.add(ingredient)) {
                        merged.add(ingredient);
                    }
                }
            }

            return merged;
        } catch (RuntimeException | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
            BetterSearch.LOGGER.debug("[{}] EMI search left untouched: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return null;
        }
    }

    private static SearchIndex<EmiIngredient> ensureIndex(List<? extends EmiIngredient> source,
                                                          SearchSettings settings) {
        long stamp = BetterSearchClient.languageStamp();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        List<EmiIngredient> capturedSource = SOURCES.capture(source);
        return INDEX.getPrepared(capturedSource, capturedSource.size(), stamp, () -> {
            SearchSettings captured = settings.copy();
            return prepare(capturedSource, captured);
        }, () -> EmiSearch.update());
    }

    private static java.util.function.Supplier<SearchIndex<EmiIngredient>> prepare(List<? extends EmiIngredient> source,
                                                         SearchSettings settings) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        LanguageTable languages = BetterSearchClient.languages();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);
        Item.TooltipContext tooltipContext = Item.TooltipContext.of(minecraft.level);

        return EntrySnapshot.capture(source, ingredient -> {
            try {
                EntrySnapshot<EmiIngredient> builder = new EntrySnapshot<>(ingredient);
                ItemStack stack = stackOf(ingredient);
                if (stack != null && !stack.isEmpty()) {
                    CreativeIndexBuilder.fill(builder, stack, languages, codes, settings,
                            tooltipContext, minecraft.player, englishSearched);
                } else {
                    fillOther(builder, ingredient, settings);
                }
                if (!builder.isEmpty()) {
                    return builder;
                }
            } catch (RuntimeException | LinkageError t) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
                BetterSearch.LOGGER.debug("[{}] skipped EMI ingredient: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
            return null;
        });
    }

    private static void fillOther(EntrySnapshot<EmiIngredient> builder, EmiIngredient ingredient,
                                  SearchSettings settings) {
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks.isEmpty()) {
            return;
        }
        builder.add(stacks.get(0).getName().getString(), SearchField.SOURCE_NATIVE);

        Object key = stacks.get(0).getKey();
        Identifier id = key instanceof Item item ? BuiltInRegistries.ITEM.getKey(item)
                : key instanceof net.minecraft.world.level.material.Fluid fluid
                        ? BuiltInRegistries.FLUID.getKey(fluid)
                        : null;
        if (id != null) {
            builder.modId(id.getNamespace());
            builder.family(id.getPath());
            if (settings.searchItemIds) {
                builder.add(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                        SearchField.SOURCE_ID);
            }
        }
    }

    private static ItemStack stackOf(EmiIngredient ingredient) {
        try {
            List<EmiStack> stacks = ingredient.getEmiStacks();
            return stacks.isEmpty() ? null : stacks.get(0).getItemStack();
        } catch (RuntimeException | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
            return null;
        }
    }

    private static boolean usesEmiSyntax(String query) {
        if (query.indexOf('|') >= 0) {
            return true;
        }
        for (String piece : query.split("\\s+")) {
            if (!piece.isEmpty() && EMI_SYNTAX.indexOf(piece.charAt(0)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
