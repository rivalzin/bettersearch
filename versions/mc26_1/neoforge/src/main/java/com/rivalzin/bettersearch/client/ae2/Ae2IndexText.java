package com.rivalzin.bettersearch.client.ae2;

import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.client.CreativeIndexBuilder;
import com.rivalzin.bettersearch.client.LanguageTable;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;

import java.util.List;

public final class Ae2IndexText {
    private Ae2IndexText() {
    }

    public static void fill(EntrySnapshot<?> entry, Object value, LanguageTable languages,
                            List<String> codes, SearchSettings settings) {
        if (!(value instanceof Ae2KeyAccess)) {
            return;
        }
        Ae2KeyAccess key = (Ae2KeyAccess) value;
        Minecraft minecraft = Minecraft.getInstance();
        if (value instanceof Ae2ItemKeyAccess) {
            CreativeIndexBuilder.fill(entry, ((Ae2ItemKeyAccess) value).toStack(), languages, codes,
                    settings, Item.TooltipContext.of(minecraft.level), minecraft.player,
                    CreativeIndexBuilder.englishSearched(codes));
        } else {
            entry.modId(key.getModId());
            entry.add(key.getDisplayName().getString(), SearchField.SOURCE_NATIVE);
            if (settings.searchItemIds) {
                var id = key.getId();
                entry.add(id.getNamespace() + ' ' + id.getPath().replace('_', ' '), SearchField.SOURCE_ID);
            }
        }
        if (settings.crossLanguage) {
            translated(entry, key.getDisplayName(), languages, codes, 0);
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
                String translation = languages.get(code, contents.getKey());
                entry.add(translation, "en_us".equals(code)
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
