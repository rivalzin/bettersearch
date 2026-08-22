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
        List<String> out2 = new ArrayList<String>();
        for (String code : current.keySet()) {
            if (isOn(settings, code) && !code.equalsIgnoreCase(fromGame)) {
                out2.add(code);
            }
        }
        return out2;
    }

    private static boolean isOn(SearchSettings settings, String code) {
        return settings.crossLanguage
                && (settings.indexesAllLanguages() || LanguageCatalog.contains(settings.languages, code));
    }

    public static void ensure(SearchSettings settings) {
        if (table != null || loading || !settings.crossLanguage) {
            return;
        }
        loading = true;
        final int loadGeneration = generation;
        final IResourceManager resources = Minecraft.getMinecraft().getResourceManager();
        final List<String> requested = requestedInGameSpelling(settings);

        final List<String> domains = new ArrayList<String>();
        for (Object domain : resources.getResourceDomains()) {
            domains.add(String.valueOf(domain));
        }

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long started = System.nanoTime();
                    Map<String, Map<String, String>> fresh = new LinkedHashMap<String, Map<String, String>>();
                    for (String code : requested) {
                        if ("*".equals(code)) {
                            continue;
                        }
                        Map<String, String> translations = new HashMap<String, String>(2048);
                        for (String domain : domains) {
                            try {
                                for (Object resource : resources.getAllResources(
                                        new ResourceLocation(domain, "lang/" + code + ".lang"))) {
                                    read((IResource) resource, translations);
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
                    BetterSearch.LOGGER.info("[{}] {} languages indexed (1.7.10, {} strings) in {} ms: {}",
                            BetterSearch.MOD_NAME, fresh.size(), total,
                            (System.nanoTime() - started) / 1_000_000, fresh.keySet());
                } finally {
                    loading = false;
                }
            }
        }, "BetterSearch-LangTable-1.7.10");
        worker.setDaemon(true);
        worker.start();
    }

    private static List<String> requestedInGameSpelling(SearchSettings settings) {
        List<LanguageCatalog.Entry> catalog = LanguageCatalog.available();
        List<String> out = new ArrayList<String>();
        if (settings.indexesAllLanguages()) {
            for (LanguageCatalog.Entry entry : catalog) {
                out.add(entry.code());
            }
            return out;
        }
        for (String request : settings.languages) {
            String nativeCode = request;
            for (LanguageCatalog.Entry entry : catalog) {
                if (entry.code().equalsIgnoreCase(request)) {
                    nativeCode = entry.code();
                    break;
                }
            }
            out.add(nativeCode);
        }
        return out;
    }

    private static void read(IResource resource, Map<String, String> target) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            try {
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
            } finally {
                reader.close();
            }
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] skipped language file: {}",
                    BetterSearch.MOD_NAME, t.toString());
        }
    }
}
