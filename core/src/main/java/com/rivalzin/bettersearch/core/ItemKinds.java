package com.rivalzin.bettersearch.core;

import java.util.HashMap;
import java.util.Map;

public final class ItemKinds {

    private static final String[][] TABLE = {
            {"helmet", "ARMOR"},
            {"chestplate", "ARMOR"},
            {"leggings", "ARMOR"},
            {"boots", "ARMOR"},

            {"sword", "TOOL"},
            {"shovel", "TOOL"},
            {"pickaxe", "TOOL"},
            {"axe", "TOOL"},
            {"hoe", "TOOL"},
    };

    private static final Map<String, String> KIND = new HashMap<>();
    private static final Map<String, Integer> ORDER = new HashMap<>();

    static {
        Map<String, Integer> nextInKind = new HashMap<>();
        for (String[] row : TABLE) {
            String family = row[0];
            String kind = row[1];
            Integer next = nextInKind.get(kind);
            int place = next == null ? 0 : next;
            KIND.put(family, kind);
            ORDER.put(family, place);
            nextInKind.put(kind, place + 1);
        }
    }

    private ItemKinds() {
    }

    public static String kindOf(String family) {
        String kind = KIND.get(family);
        return kind == null ? family : kind;
    }

    public static int orderOf(String family) {
        Integer order = ORDER.get(family);
        return order == null ? 0 : order;
    }

    public static int size() {
        return TABLE.length;
    }
}
