package com.rivalzin.bettersearch.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Families that belong together and have an order of their own.
 *
 * <p>A helmet and a pair of boots are two families, but a player reading a list wants them side
 * by side, head first, the way the game itself shows a set. Same for the tools. This is the one
 * thing the JEI list does that plain creative order does not, and it is what makes its results
 * look sorted while ours looked shuffled.
 *
 * <p>The key is the family, so it comes from the registry name and not from the display name:
 * it reads the same in every language, and a mod that names its pieces the usual way joins the
 * vanilla ones with nothing to declare.
 */
public final class ItemKinds {
    // The row order IS the order inside the kind. Armour goes head to feet like the creative
    // menu; the tools follow the order Items.java registers them in.
    private static final String[][] TABLE = {
            {"helmet", "armor"},
            {"chestplate", "armor"},
            {"leggings", "armor"},
            {"boots", "armor"},

            {"sword", "tool"},
            {"shovel", "tool"},
            {"pickaxe", "tool"},
            {"axe", "tool"},
            {"hoe", "tool"},
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

    /** The kind this family belongs to, or the family itself when it belongs to none. */
    public static String kindOf(String family) {
        String kind = KIND.get(family);
        return kind == null ? family : kind;
    }

    /** Where this family sits inside its kind. Zero for a family that stands alone. */
    public static int orderOf(String family) {
        Integer order = ORDER.get(family);
        return order == null ? 0 : order;
    }

    public static int size() {
        return TABLE.length;
    }
}
