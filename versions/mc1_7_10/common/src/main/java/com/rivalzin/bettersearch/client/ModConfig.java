package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.core.SearchSettings;

import java.nio.file.Path;

public final class ModConfig {
    private static volatile SearchSettings settings = new SearchSettings();
    private static volatile Path file;

    private static volatile int stamp;

    private ModConfig() {
    }

    public static SearchSettings settings() {
        return settings.copy();
    }

    public static int stamp() {
        return stamp;
    }

    public static synchronized void load(Path path) {
        file = path;
        SearchSettings loaded = ConfigIo.loadOrCreate(path);
        if (loaded != null) {
            loaded = loaded.copy();
            loaded.sanitize();
            settings = loaded;
        }
    }

    public static synchronized void apply(SearchSettings updated) {
        updated = updated.copy();
        updated.sanitize();

        if (updated.equals(settings)) {
            if (file != null && !java.nio.file.Files.exists(file)) {
                ConfigIo.save(file, settings);
            }
            return;
        }
        boolean languagesChanged = updated.affectsLanguageTable(settings);
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
