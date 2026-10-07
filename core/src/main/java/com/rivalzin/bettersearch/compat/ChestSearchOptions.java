package com.rivalzin.bettersearch.compat;

import com.rivalzin.bettersearch.FailurePolicy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class ChestSearchOptions {
    private static final Field TOOLTIP = tooltipField();

    private ChestSearchOptions() {
    }

    public static boolean allowsTooltips() {
        if (TOOLTIP == null) {
            return true;
        }
        try {
            return TOOLTIP.getBoolean(null);
        } catch (IllegalAccessException | RuntimeException | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            return false;
        }
    }

    private static Field tooltipField() {
        try {
            Class<?> config = Class.forName("cgcm.chestsearchbar.config.Config", false,
                    ChestSearchOptions.class.getClassLoader());
            Field field = config.getField("searchInTooltips");
            return field.getType() == boolean.class && Modifier.isStatic(field.getModifiers()) ? field : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            return null;
        }
    }
}
