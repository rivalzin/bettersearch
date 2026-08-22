package com.rivalzin.bettersearch.client;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class LanguageTable {
    public static final LanguageTable EMPTY = new LanguageTable(Map.of(), List.of(), Set.of());

    private final Map<String, Map<String, String>> byLanguage;
    private final List<String> order;
    private final Set<String> requested;

    private LanguageTable(Map<String, Map<String, String>> byLanguage, List<String> order, Set<String> requested) {
        this.byLanguage = byLanguage;
        this.order = order;
        this.requested = requested;
    }

    // a star means every language the packs ship
    public static Set<String> requestFor(SearchSettings settings) {
        if (!settings.crossLanguage) {
            return Set.of();
        }
        if (settings.indexesAllLanguages()) {
            return Set.of("*");
        }
        return new LinkedHashSet<>(settings.languages);
    }

    public boolean matchesRequest(SearchSettings settings) {
        return requested.equals(requestFor(settings));
    }

    public List<String> languageCodes() {
        return order;
    }

    public String get(String language, String key) {
        Map<String, String> map = byLanguage.get(language);
        return map == null ? null : map.get(key);
    }

    public boolean isEmpty() {
        return byLanguage.isEmpty();
    }

    public int entryCount() {
        int total = 0;
        for (Map<String, String> map : byLanguage.values()) {
            total += map.size();
        }
        return total;
    }

    public static LanguageTable load(ResourceManager resourceManager, SearchSettings settings) {
        if (!settings.crossLanguage) {
            return EMPTY;
        }

        Set<String> request = requestFor(settings);

        Set<String> wanted = settings.indexesAllLanguages() ? null : new LinkedHashSet<>(settings.languages);
        if (wanted != null) {
            wanted.remove("*");
            if (wanted.isEmpty()) {
                return new LanguageTable(Map.of(), List.of(), request);
            }
        }

        Map<ResourceLocation, List<Resource>> available = new LinkedHashMap<>();
        try {
            for (ResourceLocation id : resourceManager.listResources("lang", name -> name.endsWith(".json"))) {
                available.put(id, resourceManager.getResources(id));
            }
        } catch (Exception e) {
            BetterSearch.LOGGER.warn("[{}] could not list language files",
                    BetterSearch.MOD_NAME, e);
            return new LanguageTable(Map.of(), List.of(), request);
        }

        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<Resource>> entry : available.entrySet()) {
            String code = languageCodeOf(entry.getKey().getPath());
            if (code == null || (wanted != null && !wanted.contains(code))) {
                continue;
            }
            Map<String, String> translations = result.computeIfAbsent(code, unused -> new HashMap<>(2048));
            try {
                for (Resource resource : entry.getValue()) {
                    readInto(resource, translations);
                }
            } catch (Exception e) {
                BetterSearch.LOGGER.debug("[{}] language pack skipped ({}): {}",
                        BetterSearch.MOD_NAME, entry.getKey(), e.toString());
            }
        }
        result.values().removeIf(Map::isEmpty);

        List<String> order = new ArrayList<>();
        if (wanted != null) {
            for (String code : wanted) {
                if (result.containsKey(code)) {
                    order.add(code);
                }
            }
        } else {
            order.addAll(new TreeSet<>(result.keySet()));
        }

        BetterSearch.LOGGER.info("[{}] {} languages indexed ({} item strings): {}",
                BetterSearch.MOD_NAME, order.size(),
                result.values().stream().mapToInt(Map::size).sum(), order);
        return new LanguageTable(result, List.copyOf(order), request);
    }

    private static String languageCodeOf(String path) {
        if (!path.endsWith(".json")) {
            return null;
        }
        String code = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
        return code.isEmpty() ? null : code;
    }

    private static void readInto(Resource resource, Map<String, String> out) {
        try (InputStream in = resource.getInputStream();
             Reader charReader = new InputStreamReader(in, StandardCharsets.UTF_8);
             JsonReader json = new JsonReader(charReader)) {
            json.setLenient(true);
            if (json.peek() != JsonToken.BEGIN_OBJECT) {
                return;
            }
            json.beginObject();
            while (json.hasNext()) {
                String key = json.nextName();
                if (json.peek() != JsonToken.STRING) {
                    json.skipValue();
                    continue;
                }
                String value = json.nextString();
                if (isInteresting(key)) {
                    out.put(key, value);
                }
            }
            json.endObject();
        } catch (Exception e) {
            BetterSearch.LOGGER.debug("[{}] skipped language file: {}", BetterSearch.MOD_NAME, e.toString());
        }
    }

    // only item and block keys, the rest of the lang file is noise here
    private static boolean isInteresting(String key) {
        return key.startsWith("item.") || key.startsWith("block.");
    }
}
