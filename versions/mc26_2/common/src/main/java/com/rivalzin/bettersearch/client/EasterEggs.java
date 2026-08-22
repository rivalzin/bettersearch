package com.rivalzin.bettersearch.client;

import java.util.List;
import java.util.Map;

public final class EasterEggs {
    private static final Map<String, List<String>> ALWAYS = Map.ofEntries(

            Map.entry("minecraft:pig_spawn_egg", List.of("technoblade")),
            Map.entry("minecraft:potato", List.of("technoblade")),
            Map.entry("minecraft:golden_helmet", List.of("technoblade")),
            Map.entry("minecraft:red_bed", List.of("technoblade")),

            Map.entry("minecraft:spider_spawn_egg", List.of("venomextreme", "venoninho", "venom extreme")),
            Map.entry("minecraft:gold_ingot", List.of("venomextreme", "venoninho", "venom extreme")),
            Map.entry("minecraft:arrow", List.of("venomextreme", "venoninho", "venom extreme")),

            Map.entry("minecraft:cat_spawn_egg", List.of("rival", "rivalzin")),
            Map.entry("minecraft:music_disc_wait", List.of("rival", "rivalzin")),

            Map.entry("minecraft:fox_spawn_egg", List.of("spacey", "spaceybubs", "xspaceybubs")),
            Map.entry("minecraft:brush", List.of("spacey", "spaceybubs", "xspaceybubs")),
            Map.entry("minecraft:yellow_dye", List.of("spacey", "spaceybubs", "xspaceybubs")));

    // these only fire when english is being searched, otherwise they collide
    private static final Map<String, List<String>> ENGLISH = Map.of(

            "minecraft:crafting_table", List.of("workbench"));

    private EasterEggs() {
    }

    public static List<String> aliasesFor(String itemId, boolean englishSearched) {
        List<String> always = ALWAYS.get(itemId);
        List<String> english = englishSearched ? ENGLISH.get(itemId) : null;
        if (english == null) {
            return always == null ? List.of() : always;
        }
        if (always == null) {
            return english;
        }
        return java.util.stream.Stream.concat(always.stream(), english.stream()).toList();
    }
}
