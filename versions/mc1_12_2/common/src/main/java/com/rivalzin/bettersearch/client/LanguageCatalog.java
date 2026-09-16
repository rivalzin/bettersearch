package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;

import java.util.ArrayList;
import java.util.List;

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
        List<Entry> out = new ArrayList<>();
        try {
            for (Language language : Minecraft.getMinecraft().getLanguageManager().getLanguages()) {
                out.add(new Entry(language.getLanguageCode(), language.toString()));
            }
        } catch (Exception t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
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
        } catch (Exception t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
            return "en_us";
        }
    }
}
