package com.rivalzin.bettersearch.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class Reflect {
    private Reflect() {
    }

    // MCP name first, SRG second - dev and obf runs each hit one
    public static Field field(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException findNext) {
            }
        }
        throw new IllegalStateException("no field " + Arrays.toString(names) + " in " + type.getName());
    }

    public static Method method(Class<?> type, String[] names, Class<?>... paramTypes) {
        for (String name : names) {
            try {
                Method method = type.getDeclaredMethod(name, paramTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException findNext) {
            }
        }
        throw new IllegalStateException("no method " + Arrays.toString(names) + " in " + type.getName());
    }
}
