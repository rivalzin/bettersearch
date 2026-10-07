package com.rivalzin.bettersearch.client.compat;

import java.lang.reflect.Field;
import java.util.Collections;

public final class LanguageReloadTables {
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override
        protected Access computeValue(Class<?> type) {
            try {
                Field pending = type.getDeclaredField("separateTranslationsOnLoad");
                Field table = type.getDeclaredField("separateTranslations");
                pending.setAccessible(true);
                table.setAccessible(true);
                return new Access(pending, table);
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

    private record Access(Field pending, Field table) {
    }
}
