package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageInfo;

import java.util.ArrayList;
import java.util.List;

// names come from the pack metadata so they read right in their own script
public final class LanguageCatalog {
    public record Entry(String code, String displayName) {
    }

    private LanguageCatalog() {
    }

    public static List<Entry> available() {
        List<Entry> out = new ArrayList<>();
        try {
            for (LanguageInfo info : Minecraft.getInstance().getLanguageManager().getLanguages()) {
                out.add(new Entry(info.getCode(), info.getName() + " (" + info.getRegion() + ")"));
            }
        } catch (Throwable t) {
            BetterSearch.LOGGER.warn("[{}] could not list game languages", BetterSearch.MOD_NAME, t);
        }
        if (out.isEmpty()) {
            for (String code : SearchSettings.DEFAULT_LANGUAGES) {
                out.add(new Entry(code, code));
            }
        }
        return out;
    }

    public static String currentCode() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected().getCode();
        } catch (Throwable t) {
            return "en_us";
        }
    }
}
