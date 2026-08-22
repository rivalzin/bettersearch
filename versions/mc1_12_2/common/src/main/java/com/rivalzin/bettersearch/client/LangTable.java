package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LangTable {
    private static volatile Map<String, Map<String, String>> table;
    private static volatile int stamp;
    private static volatile boolean loading;

    private static volatile int generation;

    private LangTable() {
    }

    public static void invalidate() {
        generation++;
        table = null;
    }

    public static int stamp() {
        return stamp;
    }

    public static String get(String code, String key) {
        Map<String, Map<String, String>> current = table;
        if (current == null) {
            return null;
        }
        Map<String, String> forLanguage = current.get(code);
        return forLanguage == null ? null : forLanguage.get(key);
    }

    public static List<String> activeCodes(SearchSettings settings) {
        Map<String, Map<String, String>> current = table;
        if (current == null) {
            return java.util.Collections.emptyList();
        }
        String fromGame = Minecraft.getMinecraft().gameSettings.language;
        List<String> out2 = new ArrayList<>();
        for (String code : current.keySet()) {
            if (settings.indexesLanguage(code) && !code.equalsIgnoreCase(fromGame)) {
                out2.add(code);
            }
        }
        return out2;
    }

    public static void ensure(SearchSettings settings) {
        if (table != null || loading || !settings.crossLanguage) {
            return;
        }
        loading = true;
        final int loadGeneration = generation;
        final IResourceManager resources = Minecraft.getMinecraft().getResourceManager();
        final List<String> requested = new ArrayList<>(settings.indexesAllLanguages()
                ? SearchSettings.DEFAULT_LANGUAGES : settings.languages);
        final List<String> domains = new ArrayList<>(resources.getResourceDomains());

        Thread worker = new Thread(() -> {
            try {
                long started = System.nanoTime();
                Map<String, Map<String, String>> fresh = new LinkedHashMap<>();
                for (String code : requested) {
                    if ("*".equals(code)) {
                        continue;
                    }
                    Map<String, String> translations = new HashMap<>(2048);
                    for (String domain : domains) {
                        try {
                            for (IResource resource : resources.getAllResources(
                                    new ResourceLocation(domain, "lang/" + code + ".lang"))) {
                                read(resource, translations);
                            }
                        } catch (Throwable noFile) {
                        }
                    }
                    if (!translations.isEmpty()) {
                        fresh.put(code, translations);
                    }
                }
                if (generation != loadGeneration) {
                    BetterSearch.LOGGER.debug("[{}] language table dropped, list changed while loading",
                            BetterSearch.MOD_NAME);
                    return;
                }
                table = fresh;
                stamp++;
                int total = 0;
                for (Map<String, String> m : fresh.values()) {
                    total += m.size();
                }
                BetterSearch.LOGGER.info("[{}] {} languages indexed (1.12.2, {} strings) in {} ms: {}",
                        BetterSearch.MOD_NAME, fresh.size(), total,
                        (System.nanoTime() - started) / 1_000_000, fresh.keySet());
            } finally {
                loading = false;
            }
        }, "BetterSearch-LangTable-1.12.2");
        worker.setDaemon(true);
        worker.start();
    }

    private static void read(IResource resource, Map<String, String> target) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                int same = line.indexOf('=');
                if (same > 0) {
                    target.put(line.substring(0, same), line.substring(same + 1));
                }
            }
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] skipped language file: {}",
                    BetterSearch.MOD_NAME, t.toString());
        }
    }
}
