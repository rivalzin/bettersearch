package com.rivalzin.bettersearch.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.google.gson.JsonPrimitive;
import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchSettings;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public final class ConfigIo {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final String[] HELP = {
            "Better Search - makes search bars smarter and better.",
            "You do NOT need to edit this file: everything is in the game, on the",
            "  mod list (Better Search -> Config) or with the Alt + O shortcut.",
            "enabled: false restores Minecraft's original search everywhere.",
            "searchCreative / searchRecipeBook: where the mod is allowed to act.",
            "searchPlayerNames / searchCommandItems: extra chat and command suggestions.",
            "fixCommandErrors: '/gamemode criativo' suggests 'creative'. Nothing to tune.",
            "commandSuggestionLimit: how many entries the mod may add to a suggestion list.",
            "typoTolerance: 0 = off, 1 = low, 2 = normal, 3 = high.",
            "minTypoLength: words shorter than this must be spelled correctly.",
            "matchInitials: 'obwc' finds 'Oak Boat with Chest'.",
            "ignoreSpaces: 'goldenapple' finds 'Golden Apple'.",
            "searchTooltips: also search the lines under the item name (potions, books).",
            "searchItemIds: allow searching by id, e.g. redstone_torch.",
            "searchModIds: allow the @mod filter, e.g. @create cogwheel.",
            "crossLanguage: also search the item name in other languages ('pomme').",
            "languages: which languages are indexed. Use [\"*\"] for ALL of them.",
            "foreignStrictOnly: other languages only match exact spelling (avoids noise).",
            "crossFieldMatching: allow one search to mix words from different languages.",
            "fuzzyThreshold / crossFieldThreshold: how few results justify running the more",
            "  permissive passes. Higher = tries harder, slightly more time per keystroke.",
            "sortByRelevance: order by best match instead of creative tab order.",
            "maxResults: 0 = unlimited.",
            "searchJei / searchEmi / searchRei: use this same search inside JEI's, EMI's and",
            "  REI's item lists. There is no separate tuning for them on purpose: every option",
            "  above applies there too, because it is the same index and the same matcher."
    };

    private ConfigIo() {
    }

    public static SearchSettings loadOrCreate(Path file) {
        SearchSettings settings = new SearchSettings();
        boolean rewrite = true;
        if (!Files.notExists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement parsed = new JsonParser().parse(reader);
                if (parsed != null && parsed.isJsonObject()) {
                    settings = fromJsonTolerant(parsed.getAsJsonObject());
                }
            } catch (Exception e) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(e);

                rewrite = !isReadFailure(e);
                BetterSearch.LOGGER.warn("[{}] bad config at {}, using defaults{}",
                        BetterSearch.MOD_NAME, file, rewrite ? "" : " (file left untouched)", e);
                settings = new SearchSettings();
            }
        }
        settings.sanitize();
        if (rewrite) {
            save(file, settings);
        }
        return settings;
    }

    private static SearchSettings fromJsonTolerant(JsonObject source) {
        SearchSettings whole = fromJsonQuiet(source);
        if (whole != null) {
            return whole;
        }
        java.util.List<Map.Entry<String, JsonElement>> good =
                new java.util.ArrayList<Map.Entry<String, JsonElement>>();
        SearchSettings best = new SearchSettings();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            JsonObject attempt = new JsonObject();
            for (Map.Entry<String, JsonElement> kept : good) {
                attempt.add(kept.getKey(), kept.getValue());
            }
            attempt.add(entry.getKey(), entry.getValue());
            SearchSettings read = fromJsonQuiet(attempt);
            if (read == null) {
                BetterSearch.LOGGER.warn("[{}] config key \"{}\" has a broken value, kept default",
                        BetterSearch.MOD_NAME, entry.getKey());
            } else {
                good.add(entry);
                best = read;
            }
        }
        return best;
    }

    private static SearchSettings fromJsonQuiet(JsonObject source) {
        try {
            return GSON.fromJson(source, SearchSettings.class);
        } catch (RuntimeException t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
            return null;
        }
    }

    private static boolean isReadFailure(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {

            if (t instanceof java.io.IOException
                    && !(t instanceof java.io.EOFException)
                    && !t.getClass().getName().startsWith("com.google.gson")) {
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean save(Path file, SearchSettings settings) {
        Path temp = null;
        try {
            file = file.toAbsolutePath().normalize();
            Files.createDirectories(file.getParent());
            SearchSettings snapshot = settings.copy();
            snapshot.sanitize();
            JsonObject out = new JsonObject();
            JsonArray help = new JsonArray();
            for (String line : HELP) {
                help.add(new JsonPrimitive(line));
            }
            out.add("_ajuda", help);
            for (Map.Entry<String, JsonElement> entry : GSON.toJsonTree(snapshot).getAsJsonObject().entrySet()) {
                out.add(entry.getKey(), entry.getValue());
            }

            temp = Files.createTempFile(file.getParent(), file.getFileName() + ".", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                writer.write(GSON.toJson(out));
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException atomicNotSupported) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(atomicNotSupported);
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(e);
            BetterSearch.LOGGER.warn("[{}] could not save config to {}",
                    BetterSearch.MOD_NAME, file, e);
            return false;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (java.io.IOException cleanupFailure) {
                    com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(cleanupFailure);
                    BetterSearch.LOGGER.debug("[{}] could not remove temporary config {}",
                            BetterSearch.MOD_NAME, temp, cleanupFailure);
                }
            }
        }
    }
}
