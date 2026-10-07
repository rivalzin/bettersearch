package com.rivalzin.bettersearch.client.compat;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class LanguageReloadTables {
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override
        protected Access computeValue(Class<?> type) {
            try {
                Field pending = type.getDeclaredField("separateTranslationsOnLoad");
                Field table = type.getDeclaredField("separateTranslations");
                pending.setAccessible(true);
                table.setAccessible(true);
                Field enabled = type.getClassLoader()
                        .loadClass("org.hiedacamellia.languagereload.core.config.ClientConfig")
                        .getDeclaredField("multilingualItemSearch");
                enabled.setAccessible(true);
                return new Access(pending, table, enabled);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unsupported Language Reload translation storage", error);
            }
        }
    };

    private LanguageReloadTables() {
    }

    public static void initialize(Object language, Class<?> type) {
        Access access = ACCESS.get(type);
        try {
            if (access.table.get(language) == null) {
                Object pending = access.pending.get(null);
                access.table.set(language, pending != null ? pending : Collections.emptyMap());
            }
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Could not initialize Language Reload translation storage", error);
        }
    }

    public static void release(Class<?> type) {
        try {
            ACCESS.get(type).pending.set(null, null);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Could not release Language Reload translation storage", error);
        }
    }

    public static BiConsumer<String, String> capture(BiConsumer<String, String> original, Class<?> type, String code) {
        Access access = ACCESS.get(type);
        try {
            if (!access.enabled.getBoolean(null)) {
                return original;
            }
            Map<String, Map<String, String>> pending = (Map<String, Map<String, String>>) access.pending.get(null);
            if (pending == null) {
                return original;
            }
            Map<String, String> translations = pending.computeIfAbsent(code, key -> new HashMap<>());
            return (key, value) -> {
                original.accept(key, value);
                translations.put(key, value);
            };
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Could not capture Language Reload translations", error);
        }
    }

    private record Access(Field pending, Field table, Field enabled) {
    }
}
