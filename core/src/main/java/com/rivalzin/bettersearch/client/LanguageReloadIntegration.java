package com.rivalzin.bettersearch.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.core.SearchSettings;

import java.io.BufferedReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LanguageReloadIntegration {
    private static final String[] CONFIGS = {
            "jerozgen.languagereload.config.Config",
            "com.euphony.neo_language_reload.config.Config"
    };
    private static BooleanSupplier enabled;
    private static Supplier<SearchSettings> current;
    private static Consumer<SearchSettings> apply;
    private static long nextCheck;
    private static boolean reportedFailure;

    private LanguageReloadIntegration() {
    }

    public static boolean available() {
        return AccessHolder.ACCESS != null;
    }

    public static void bind(BooleanSupplier active, Supplier<SearchSettings> settings,
                            Consumer<SearchSettings> update) {
        enabled = active;
        current = settings;
        apply = update;
        nextCheck = 0;
    }

    public static void importInitial(SearchSettings settings, Path betterSearchFile) {
        Access access = AccessHolder.ACCESS;
        if (access == null) {
            return;
        }
        try {
            Path source = access.path();
            if (source == null || source.toAbsolutePath().normalize()
                    .equals(betterSearchFile.toAbsolutePath().normalize())
                    || !Files.isRegularFile(source) || Files.size(source) > 1_048_576L) {
                return;
            }
            try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
                JsonElement json = new JsonParser().parse(reader);
                List<String> languages = fromStored(json);
                if (languages != null && languages.size() > 2) {
                    settings.languages = languages;
                }
            }
        } catch (Exception | LinkageError error) {
            failed(error);
        }
    }

    public static void synchronize(SearchSettings settings) {
        if (!settings.syncLanguageReload || !available()) {
            return;
        }
        List<String> languages = readCurrent();
        if (languages != null && !sameLanguages(languages, settings.languages)) {
            settings.languages = new ArrayList<>(languages);
        }
    }

    public static void tick() {
        if (enabled == null || !enabled.getAsBoolean() || !available()) {
            return;
        }
        long now = System.nanoTime();
        if (nextCheck != 0 && now - nextCheck < 0) {
            return;
        }
        nextCheck = now + 250_000_000L;
        List<String> languages = readCurrent();
        if (languages == null) {
            return;
        }
        SearchSettings settings = current.get();
        if (languages != null && !sameLanguages(languages, settings.languages)) {
            settings.languages = new ArrayList<>(languages);
            apply.accept(settings);
        }
    }

    private static boolean sameLanguages(List<String> first, List<String> second) {
        return second != null && (first.equals(second)
                || first.size() == second.size() && first.containsAll(second));
    }

    private static List<String> readCurrent() {
        try {
            Access access = AccessHolder.ACCESS;
            Object value = access.instance.get(null);
            if (value == null) {
                return null;
            }
            Object language = access.language.get(value);
            Object fallbacks = access.fallbacks.get(value);
            if (!(language instanceof String) || !(fallbacks instanceof Iterable)) {
                return null;
            }
            boolean implicitEnglish = access.version == null || access.version.getInt(value) < 1;
            Snapshot cached = access.snapshot;
            if (cached != null && cached.matches((String) language, (Iterable<?>) fallbacks, implicitEnglish)) {
                return cached.languages;
            }
            List<String> normalized = normalize((String) language, (Iterable<?>) fallbacks, implicitEnglish);
            if (normalized == null) {
                return null;
            }
            access.snapshot = new Snapshot((String) language, (Iterable<?>) fallbacks, implicitEnglish, normalized);
            return normalized;
        } catch (Exception | LinkageError error) {
            failed(error);
            return null;
        }
    }

    static List<String> fromStored(JsonElement json) {
        if (json == null || !json.isJsonObject()) {
            return null;
        }
        JsonObject object = json.getAsJsonObject();
        JsonElement language = object.get("language");
        JsonElement fallbacks = object.get("fallbacks");
        if (language == null || !language.isJsonPrimitive() || !language.getAsJsonPrimitive().isString()
                || fallbacks == null || !fallbacks.isJsonArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonElement fallback : fallbacks.getAsJsonArray()) {
            if (!fallback.isJsonPrimitive() || !fallback.getAsJsonPrimitive().isString()) {
                return null;
            }
            values.add(fallback.getAsString());
        }
        JsonElement version = object.get("version");
        boolean implicitEnglish = version == null;
        if (version != null) {
            if (!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            implicitEnglish = version.getAsInt() < 1;
        }
        return normalize(language.getAsString(), values, implicitEnglish);
    }

    private static List<String> normalize(String language, Iterable<?> fallbacks, boolean implicitEnglish) {
        if (language == null || language.trim().isEmpty()) {
            return null;
        }
        Set<String> codes = new LinkedHashSet<>();
        if (!add(codes, language)) {
            return null;
        }
        for (Object fallback : fallbacks) {
            if (!(fallback instanceof String)) {
                return null;
            }
            if (!add(codes, (String) fallback)) {
                return null;
            }
        }
        if (implicitEnglish && !codes.isEmpty() && !"*".equals(language.trim())) {
            codes.add("en_us");
        }
        return new ArrayList<>(codes);
    }

    private static boolean add(Set<String> codes, String raw) {
        if (raw == null) {
            return false;
        }
        String code = raw.trim().toLowerCase(Locale.ROOT);
        if (code.isEmpty() || "*".equals(code)) {
            return true;
        }
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_') {
                return false;
            }
        }
        codes.add(code);
        return true;
    }

    private static void failed(Throwable error) {
        FailurePolicy.rethrowFatal(error);
        if (!reportedFailure) {
            reportedFailure = true;
            BetterSearch.LOGGER.warn("[{}] could not read Language Reload languages: {}",
                    BetterSearch.MOD_NAME, error.toString());
        }
    }

    private static final class AccessHolder {
        static final Access ACCESS = discover();

        private static Access discover() {
            for (String name : CONFIGS) {
                try {
                    Class<?> type = Class.forName(name, false, LanguageReloadIntegration.class.getClassLoader());
                    return new Access(type);
                } catch (ClassNotFoundException absent) {
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    failed(error);
                }
            }
            return null;
        }
    }

    private static final class Access {
        final Field instance;
        final Field language;
        final Field fallbacks;
        final Field configPath;
        final Field version;
        Snapshot snapshot;

        Access(Class<?> type) throws ReflectiveOperationException {
            instance = type.getDeclaredField("INSTANCE");
            configPath = type.getDeclaredField("PATH");
            language = type.getField("language");
            fallbacks = type.getField("fallbacks");
            if (!Modifier.isStatic(instance.getModifiers()) || !type.isAssignableFrom(instance.getType())
                    || !Modifier.isStatic(configPath.getModifiers()) || !Path.class.isAssignableFrom(configPath.getType())
                    || language.getType() != String.class || !Iterable.class.isAssignableFrom(fallbacks.getType())) {
                throw new NoSuchFieldException("Unsupported Language Reload configuration");
            }
            instance.setAccessible(true);
            configPath.setAccessible(true);
            Field revision;
            try {
                revision = type.getField("version");
            } catch (NoSuchFieldException absent) {
                revision = null;
            }
            if (revision != null && revision.getType() != int.class) {
                throw new NoSuchFieldException("Unsupported Language Reload configuration version");
            }
            version = revision;
        }

        Path path() throws IllegalAccessException {
            return (Path) configPath.get(null);
        }
    }

    private static final class Snapshot {
        final String language;
        final List<String> fallbacks = new ArrayList<>();
        final boolean implicitEnglish;
        final List<String> languages;

        Snapshot(String language, Iterable<?> fallbacks, boolean implicitEnglish, List<String> languages) {
            this.language = language;
            for (Object fallback : fallbacks) {
                this.fallbacks.add((String) fallback);
            }
            this.implicitEnglish = implicitEnglish;
            this.languages = languages;
        }

        boolean matches(String language, Iterable<?> fallbacks, boolean implicitEnglish) {
            if (!this.language.equals(language) || this.implicitEnglish != implicitEnglish) {
                return false;
            }
            int index = 0;
            for (Object fallback : fallbacks) {
                if (index >= this.fallbacks.size() || !this.fallbacks.get(index++).equals(fallback)) {
                    return false;
                }
            }
            return index == this.fallbacks.size();
        }
    }
}
