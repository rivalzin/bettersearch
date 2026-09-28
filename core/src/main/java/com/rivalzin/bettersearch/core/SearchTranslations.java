package com.rivalzin.bettersearch.core;

public final class SearchTranslations {
    private SearchTranslations() {
    }

    public static boolean includes(String key) {
        return key.startsWith("item.") || key.startsWith("block.")
                || key.startsWith("fluid.") || key.startsWith("fluid_type.")
                || key.startsWith("gas.") || key.startsWith("chemical.")
                || key.startsWith("substance.");
    }
}
