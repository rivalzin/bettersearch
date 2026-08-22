package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.core.SearchSettings;

import java.nio.file.Path;

public final class ModConfig {
    private static SearchSettings settings = new SearchSettings();
    private static Path file;

    private static volatile int stamp;

    private ModConfig() {
    }

    public static SearchSettings settings() {
        return settings;
    }

    public static int stamp() {
        return stamp;
    }

    public static void load(Path path) {
        file = path;
        SearchSettings loaded = ConfigIo.loadOrCreate(path);
        if (loaded != null) {
            settings = loaded;
        }
        settings.sanitize();
    }

    public static void apply(SearchSettings updated) {
        updated.sanitize();
        boolean languagesChanged = !updated.languages.equals(settings.languages)
                || updated.crossLanguage != settings.crossLanguage;
        settings = updated;
        stamp++;
        if (languagesChanged) {
            LangTable.invalidate();
        }
        if (file != null) {
            ConfigIo.save(file, settings);
        }
    }
}
