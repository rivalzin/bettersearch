package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;

import java.util.ArrayList;
import java.util.List;

// names come from the pack metadata so they read right in their own script
public final class LanguageCatalog {
    public static final class Entry {
        private final String code;
        private final String displayName;

        public Entry(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String code() {
            return code;
        }

        public String displayName() {
            return displayName;
        }
    }

    private LanguageCatalog() {
    }

    public static List<Entry> available() {
        List<Entry> out = new ArrayList<Entry>();
        try {
            for (Object item : Minecraft.getMinecraft().getLanguageManager().getLanguages()) {
                Language language = (Language) item;
                out.add(new Entry(language.getLanguageCode(), language.toString()));
            }
        } catch (Throwable t) {
            BetterSearch.LOGGER.warn("[{}] could not list game languages: {}",
                    BetterSearch.MOD_NAME, t.toString());
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
            return Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
        } catch (Throwable t) {
            return "en_US";
        }
    }

    public static boolean contains(List<String> list, String code) {
        for (String candidate : list) {
            if (candidate.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }
}
