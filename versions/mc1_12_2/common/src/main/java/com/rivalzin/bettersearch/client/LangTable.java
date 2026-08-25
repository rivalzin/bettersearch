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
    // the client tick and the viewer thread both come through here, and a plain
    // read-then-write let the two of them start the same work twice
    private static final java.util.concurrent.atomic.AtomicBoolean loading =
            new java.util.concurrent.atomic.AtomicBoolean();

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
        List<String> out = new ArrayList<>();
        for (String code : current.keySet()) {
            if (settings.indexesLanguage(code) && !code.equalsIgnoreCase(fromGame)) {
                out.add(code);
            }
        }
        return out;
    }

    public static void ensure(SearchSettings settings) {
        if (table != null || loading.get() || !settings.crossLanguage) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        boolean queued = false;
        try {
            final int loadGeneration = generation;
            final IResourceManager resources = Minecraft.getMinecraft().getResourceManager();
            final List<String> requested = settings.indexesAllLanguages()
                    ? everyGameLanguage() : new ArrayList<>(settings.languages);
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
                        BetterSearch.LOGGER.debug("[{}] language table dropped, list changed while loading.get()",
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
                    loading.set(false);
                }
            }, "BetterSearch-LangTable-1.12.2");
            worker.setDaemon(true);
            worker.start();
            queued = true;
        } finally {
            // nothing was queued, so the flag has to come back down here:
            // otherwise one throw closes this path for the rest of the session
            if (!queued) {
                loading.set(false);
            }
        }
    }

    // a star means every language the game lists, the same as on the other versions
    private static List<String> everyGameLanguage() {
        List<String> out = new ArrayList<>();
        for (LanguageCatalog.Entry entry : LanguageCatalog.available()) {
            out.add(entry.code());
        }
        return out;
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
