package com.rivalzin.bettersearch.client;

import java.util.List;
import java.util.Map;

public final class EasterEggs {
    private static final Map<String, List<String>> ALWAYS = Collections2.map(

            Collections2.pair("minecraft:pig_spawn_egg", Collections2.list("technoblade")),
            Collections2.pair("minecraft:potato", Collections2.list("technoblade")),
            Collections2.pair("minecraft:golden_helmet", Collections2.list("technoblade")),
            Collections2.pair("minecraft:red_bed", Collections2.list("technoblade")),

            Collections2.pair("minecraft:spider_spawn_egg", Collections2.list("venomextreme", "venoninho", "venom extreme")),
            Collections2.pair("minecraft:gold_ingot", Collections2.list("venomextreme", "venoninho", "venom extreme")),
            Collections2.pair("minecraft:arrow", Collections2.list("venomextreme", "venoninho", "venom extreme")),

            Collections2.pair("minecraft:cat_spawn_egg", Collections2.list("rival", "rivalzin")),
            Collections2.pair("minecraft:music_disc_wait", Collections2.list("rival", "rivalzin")),

            Collections2.pair("minecraft:fox_spawn_egg", Collections2.list("spacey", "spaceybubs", "xspaceybubs")),
            Collections2.pair("minecraft:brush", Collections2.list("spacey", "spaceybubs", "xspaceybubs")),
            Collections2.pair("minecraft:yellow_dye", Collections2.list("spacey", "spaceybubs", "xspaceybubs")));

    // these only fire when english is being searched, otherwise they collide
    private static final Map<String, List<String>> ENGLISH = Collections2.map(

            Collections2.pair("minecraft:crafting_table", Collections2.list("workbench")));

    private EasterEggs() {
    }

    public static List<String> aliasesFor(String itemId, boolean englishSearched) {
        List<String> always = ALWAYS.get(itemId);
        List<String> english = englishSearched ? ENGLISH.get(itemId) : null;
        if (english == null) {
            return always == null ? java.util.Collections.emptyList() : always;
        }
        if (always == null) {
            return english;
        }
        return java.util.stream.Stream.concat(always.stream(), english.stream()).collect(java.util.stream.Collectors.toList());
    }
}
