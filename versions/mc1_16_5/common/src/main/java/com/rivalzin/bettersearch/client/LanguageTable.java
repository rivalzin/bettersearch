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
    public static final LanguageTable EMPTY = new LanguageTable(java.util.Collections.emptyMap(), java.util.Collections.emptyList(), java.util.Collections.emptySet());

    private final Map<String, Map<String, String>> byLanguage;
    private final List<String> order;
    private final Set<String> requested;

    private LanguageTable(Map<String, Map<String, String>> byLanguage, List<String> order, Set<String> requested) {
        this.byLanguage = java.util.Collections.unmodifiableMap(byLanguage);
        this.order = java.util.Collections.unmodifiableList(new ArrayList<>(order));
        this.requested = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(requested));
    }

    public static Set<String> requestFor(SearchSettings settings) {
        if (!settings.crossLanguage) {
            return java.util.Collections.emptySet();
        }
        if (settings.indexesAllLanguages()) {
            return Collections2.setOf("*");
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

    public static LanguageTable load(ResourceManager resourceManager, SearchSettings settings) {
        if (!settings.crossLanguage) {
            return EMPTY;
        }

        Set<String> request = requestFor(settings);

        Set<String> wanted = settings.indexesAllLanguages() ? null : new LinkedHashSet<>(settings.languages);
        if (wanted != null) {
            if (wanted.isEmpty()) {
                return new LanguageTable(java.util.Collections.emptyMap(), java.util.Collections.emptyList(), request);
            }
        }

        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        try {
            for (ResourceLocation id : resourceManager.listResources("lang", name -> name.endsWith(".json"))) {
                String code = languageCodeOf(id.getPath());
                if (code == null || (wanted != null && !wanted.contains(code))) {
                    continue;
                }
                try {
                    List<Resource> resources = resourceManager.getResources(id);
                    Map<String, String> translations = result.computeIfAbsent(code, unused -> new HashMap<>(2048));
                    for (Resource resource : resources) {
                        readInto(resource, translations);
                    }
                } catch (Exception error) {
                    com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
                    BetterSearch.LOGGER.debug("[{}] language file skipped ({})", BetterSearch.MOD_NAME, id, error);
                }
            }
        } catch (Exception error) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
            BetterSearch.LOGGER.warn("[{}] could not list language files", BetterSearch.MOD_NAME, error);
            return EMPTY;
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
        return new LanguageTable(result, java.util.Collections.unmodifiableList(new java.util.ArrayList<>(order)), request);
    }

    private static String languageCodeOf(String path) {
        if (!path.endsWith(".json")) {
            return null;
        }
        String code = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
        return code.isEmpty() ? null : code;
    }

    private static void readInto(Resource resource, Map<String, String> out) {
        try (Resource closeable = resource;
             InputStream in = closeable.getInputStream();
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
                if (isInteresting(key)) {
                    out.put(key, json.nextString());
                } else {
                    json.skipValue();
                }
            }
            json.endObject();
        } catch (Exception e) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(e);
            BetterSearch.LOGGER.debug("[{}] skipped language file: {}", BetterSearch.MOD_NAME, e.toString());
        }
    }

    private static boolean isInteresting(String key) {
        return key.startsWith("item.") || key.startsWith("block.");
    }
}
