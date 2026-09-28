package com.rivalzin.bettersearch.client.refinedstorage;

import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.client.CreativeIndexBuilder;
import com.rivalzin.bettersearch.client.LanguageTable;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.Collection;
import java.util.List;

public final class RsIndexText {
    private RsIndexText() {
    }

    public static void fill(EntrySnapshot<?> entry, Object value, LanguageTable languages,
                            List<String> codes, SearchSettings settings) {
        Object names = RsApi.call(value, "getSearchableNames");
        if (names instanceof Collection<?>) {
            for (Object name : (Collection<?>) names) {
                if (name instanceof String) {
                    entry.add((String) name, SearchField.SOURCE_NATIVE);
                }
            }
        } else {
            Object name = RsApi.call(value, "getName");
            if (!(name instanceof String)) {
                name = RsApi.call(value, "getSortName");
            }
            if (name instanceof String) {
                entry.add((String) name, SearchField.SOURCE_NATIVE);
            }
        }
        Object stack = RsApi.call(value, "getItemStack");
        if (stack instanceof ItemStack && !((ItemStack) stack).isEmpty()) {
            Minecraft minecraft = Minecraft.getInstance();
            CreativeIndexBuilder.fill(entry, (ItemStack) stack, languages, codes, settings,
                    Item.TooltipContext.of(minecraft.level), minecraft.player,
                    CreativeIndexBuilder.englishSearched(codes));
            return;
        }
        Object resource = RsApi.call(value, "getResourceForRecipeMods");
        if (resource == null) {
            resource = RsApi.call(value, "getAutocraftingResource");
        }
        Object fluid = RsApi.call(resource, "fluid");
        if (fluid instanceof Fluid) {
            var id = BuiltInRegistries.FLUID.getKey((Fluid) fluid);
            entry.modId(id.getNamespace());
            if (settings.searchItemIds) {
                entry.add(id.getNamespace() + ' ' + id.getPath().replace('_', ' '), SearchField.SOURCE_ID);
            }
        }
        Object tooltip = RsApi.call(value, "getTooltip");
        if (tooltip instanceof List<?>) {
            List<?> lines = (List<?>) tooltip;
            int limit = settings.searchTooltips ? Math.min(lines.size(), 6) : Math.min(lines.size(), 1);
            for (int index = 0; index < limit; index++) {
                if (lines.get(index) instanceof Component) {
                    Component component = (Component) lines.get(index);
                    entry.add(component.getString(), SearchField.SOURCE_NATIVE);
                    if (settings.crossLanguage) {
                        translated(entry, component, languages, codes, 0);
                    }
                }
            }
        }
    }

    private static void translated(EntrySnapshot<?> entry, Component component, LanguageTable languages,
                                   List<String> codes, int depth) {
        if (depth >= 16 || component == null) {
            return;
        }
        if (component.getContents() instanceof TranslatableContents) {
            TranslatableContents contents = (TranslatableContents) component.getContents();
            for (String code : codes) {
                entry.add(languages.get(code, contents.getKey()), "en_us".equals(code)
                        ? SearchField.SOURCE_ENGLISH : SearchField.SOURCE_FOREIGN);
            }
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component) {
                    translated(entry, (Component) argument, languages, codes, depth + 1);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            translated(entry, sibling, languages, codes, depth + 1);
        }
    }
}
