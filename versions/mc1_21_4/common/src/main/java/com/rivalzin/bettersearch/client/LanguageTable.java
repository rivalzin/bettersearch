package com.rivalzin.bettersearch.client;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
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
        this.byLanguage = java.util.Collections.unmodifiableMap(byLanguage);
        this.order = java.util.Collections.unmodifiableList(new ArrayList<>(order));
        this.requested = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(requested));
    }

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

    public static LanguageTable load(ResourceManager resourceManager, SearchSettings settings) {
        if (!settings.crossLanguage) {
            return EMPTY;
        }

        Set<String> request = requestFor(settings);

        Set<String> wanted = settings.indexesAllLanguages() ? null : new LinkedHashSet<>(settings.languages);
        if (wanted != null) {
            if (wanted.isEmpty()) {
                return new LanguageTable(Map.of(), List.of(), request);
            }
        }

        Map<ResourceLocation, List<IoSupplier<InputStream>>> available = new LinkedHashMap<>();
        resourceManager.listPacks().forEach(pack -> {
            try {
                for (String namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
                    pack.listResources(PackType.CLIENT_RESOURCES, namespace, "lang",
                            (location, supplier) -> {
                                String code = languageCodeOf(location.getPath());
                                if (code != null && (wanted == null || wanted.contains(code))) {
                                    available.computeIfAbsent(location, unused -> new ArrayList<>())
                                            .add(supplier);
                                }
                            });
                }
            } catch (Exception t) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
                BetterSearch.LOGGER.warn("[{}] resource pack '{}' failed while listing languages, skipping it: {}",
                        BetterSearch.MOD_NAME, safeId(pack), t.toString());
            }
        });

        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<IoSupplier<InputStream>>> entry : available.entrySet()) {
            String code = languageCodeOf(entry.getKey().getPath());
            if (code == null || (wanted != null && !wanted.contains(code))) {
                continue;
            }
            Map<String, String> translations = result.computeIfAbsent(code, unused -> new HashMap<>(2048));
            try {
                for (IoSupplier<InputStream> resource : entry.getValue()) {
                    readInto(resource, translations);
                }
            } catch (Exception e) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(e);
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

    private static String safeId(PackResources pack) {
        try {
            return pack.packId();
        } catch (Exception t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
            return pack.getClass().getName();
        }
    }

    private static void readInto(IoSupplier<InputStream> resource, Map<String, String> out) {
        try (InputStream in = resource.get();
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
