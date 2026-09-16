package com.rivalzin.bettersearch.tools;

import com.rivalzin.bettersearch.core.CommandAliases;
import com.rivalzin.bettersearch.core.CommandFuzzy;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.FuzzyMatcher;
import com.rivalzin.bettersearch.core.ItemKinds;
import com.rivalzin.bettersearch.core.MatchPolicy;
import com.rivalzin.bettersearch.core.TextNormalizer;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import com.rivalzin.bettersearch.core.ShortcutRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BetterSearchCoreTest {

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) {
        SearchSettings settings = new SearchSettings();
        SearchIndex<String> index = buildIndex(settings);

        section("Acentos (o jogo esta em pt_br)");
        expectFirst(index, settings, "bau", "chest");
        expectFirst(index, settings, "baú", "chest");
        expectFirst(index, settings, "BAU", "chest");
        expectFirst(index, settings, "acucar", "sugar");
        expectFirst(index, settings, "maca", "apple");
        expectFirst(index, settings, "perola", "ender_pearl");

        section("Prefixo de palavra (nao precisa escrever o nome inteiro)");
        expectFirst(index, settings, "nether sword", "netherite_sword");
        expectFirst(index, settings, "esp netherite", "netherite_sword");
        expectFirst(index, settings, "dia", "diamond");
        expectContains(index, settings, "sword", "netherite_sword", "diamond_sword");

        section("Ordem livre das palavras");
        expectFirst(index, settings, "sword netherite", "netherite_sword");
        expectFirst(index, settings, "trabalho bancada", "crafting_table");

        section("Sem espacos / juntando palavras");
        expectFirst(index, settings, "netheritesword", "netherite_sword");
        expectFirst(index, settings, "bancadadetrabalho", "crafting_table");

        section("Erros de digitacao");
        expectFirst(index, settings, "dimaond", "diamond");
        expectFirst(index, settings, "espada de dimante", "diamond_sword");
        expectFirst(index, settings, "netherrite", "netherite_sword");
        expectFirst(index, settings, "encrenagem", "create:cogwheel");
        expectFirst(index, settings, "cofre", "chest");

        section("Iniciais");
        expectFirst(index, settings, "ct", "crafting_table");

        section("Outros idiomas (jogo em pt_br)");
        expectFirst(index, settings, "pomme", "apple");
        expectFirst(index, settings, "apple", "apple");
        expectFirst(index, settings, "crafting table", "crafting_table");
        expectFirst(index, settings, "werkbank", "crafting_table");
        expectFirst(index, settings, "manzana", "apple");
        expectFirst(index, settings, "苹果", "apple");
        expectFirst(index, settings, "golden apple", "golden_apple");

        section("Ids e filtro de mod");
        expectFirst(index, settings, "diamond_sword", "diamond_sword");
        expectFirst(index, settings, "minecraft:sugar", "sugar");
        expectOnlyMod(index, settings, "@create", "create");
        expectFirst(index, settings, "@create cog", "create:cogwheel");

        section("Frases misturando dois idiomas");
        expectFirst(index, settings, "swrod de diamante", "diamond_sword");
        expectFirst(index, settings, "pomme dourada", "golden_apple");

        section("Tooltip (livro encantado)");
        expectContains(index, settings, "afiacao", "enchanted_book");
        expectContains(index, settings, "sharpness", "enchanted_book");

        section("Ranking: o mais especifico vem primeiro");
        expectOrder(index, settings, "maca", "apple", "golden_apple");
        expectOrder(index, settings, "bau", "chest", "trapped_chest");

        section("Sem falso positivo");
        expectEmpty(index, settings, "zzzzqqq");
        expectNotContains(index, settings, "sugar", "chest");

        section("Tolerancia desligada");
        SearchSettings noTypos = settings.copy();
        noTypos.typoTolerance = 0;
        expectEmpty(index, noTypos, "dimaond");
        expectFirst(index, noTypos, "diamond", "diamond");

        section("Opcoes da tela de configuracao realmente mudam o algoritmo");
        SearchSettings noInitials = settings.copy();
        noInitials.matchInitials = false;
        expectNotContains(index, noInitials, "ct", "crafting_table");
        expectFirst(index, settings, "ct", "crafting_table");

        SearchSettings noSpaceMatching = settings.copy();
        noSpaceMatching.ignoreSpaces = false;
        expectNotContains(index, noSpaceMatching, "bancadadetrabalho", "crafting_table");

        SearchSettings longWordsOnly = settings.copy();
        longWordsOnly.minTypoLength = 8;
        expectNotContains(index, longWordsOnly, "dimaond", "diamond");
        expectFirst(index, longWordsOnly, "netherrite", "netherite_sword");

        SearchSettings noMixing = settings.copy();
        noMixing.crossFieldMatching = false;
        expectNotContains(index, noMixing, "pomme dourada", "golden_apple");

        SearchSettings noIds = settings.copy();
        noIds.searchItemIds = false;
        expectNotContains(index, noIds, "minecraft:sugar", "sugar");

        SearchSettings noTooltips = settings.copy();
        noTooltips.searchTooltips = false;
        expectNotContains(index, noTooltips, "afiacao", "enchanted_book");

        SearchSettings limited = settings.copy();
        limited.maxResults = 1;
        expectFirst(index, limited, "sword", "diamond_sword");
        expectCount(index, limited, "sword", 1);

        section("Sem busca entre idiomas");
        SearchSettings noCross = settings.copy();
        noCross.crossLanguage = false;
        SearchIndex<String> nativeOnly = buildIndex(noCross);
        expectEmpty(nativeOnly, noCross, "pomme");
        expectFirst(nativeOnly, noCross, "maca", "apple");

        section("Regressao: ligar/desligar idioma tem efeito imediato");

        expectContains(index, settings, "pomme", "apple", "potato");

        SearchSettings onlyPtEn = settings.copy();
        onlyPtEn.languages = new ArrayList<>(java.util.Arrays.asList("pt_br", "en_us"));
        SearchIndex<String> ptEn = buildIndex(onlyPtEn);
        expectEmpty(ptEn, onlyPtEn, "pomme");
        expectEmpty(ptEn, onlyPtEn, "coffre");
        expectFirst(ptEn, onlyPtEn, "apple", "apple");
        expectFirst(ptEn, onlyPtEn, "maca", "apple");
        expectFirst(ptEn, onlyPtEn, "batata", "potato");

        SearchSettings withFrench = onlyPtEn.copy();
        withFrench.languages.add("fr_fr");
        SearchIndex<String> ptEnFr = buildIndex(withFrench);
        expectFirst(ptEnFr, withFrench, "pomme", "apple");

        SearchSettings wildcard = settings.copy();
        wildcard.languages = new ArrayList<>(java.util.Arrays.asList("*"));
        SearchIndex<String> all = buildIndex(wildcard);
        expectFirst(all, wildcard, "kartoffel", "potato");

        SearchSettings crossOff = settings.copy();
        crossOff.crossLanguage = false;
        SearchIndex<String> noForeign = buildIndex(crossOff);
        expectEmpty(noForeign, crossOff, "pomme");
        expectFirst(noForeign, crossOff, "maca", "apple");

        section("AUDITORIA: cada nivel de tolerancia muda mesmo o resultado");

        SearchSettings tolOff = settings.copy();    tolOff.typoTolerance = 0;
        SearchSettings tolLow = settings.copy();    tolLow.typoTolerance = 1;
        SearchSettings tolNormal = settings.copy(); tolNormal.typoTolerance = 2;
        SearchSettings tolHigh = settings.copy();   tolHigh.typoTolerance = 3;

        expectEmpty(index, tolOff, "espoda");
        expectContains(index, tolLow, "espoda", "diamond_sword");
        expectContains(index, tolNormal, "espoda", "diamond_sword");
        expectEmpty(index, tolLow, "espodo");
        expectEmpty(index, tolNormal, "espodo");
        expectContains(index, tolHigh, "espodo", "diamond_sword");

        expectContains(index, tolNormal, "netherrrite", "netherite_sword");
        expectEmpty(index, tolLow, "netherrrite");

        section("AUDITORIA: tamanho minimo para erros");
        SearchSettings minShort = settings.copy(); minShort.minTypoLength = 3;
        SearchSettings minLong = settings.copy();  minLong.minTypoLength = 8;

        expectContains(index, minShort, "pedar", "stone");
        expectEmpty(index, minLong, "pedar");
        expectContains(index, minLong, "netherrite", "netherite_sword");

        section("AUDITORIA: limite para corrigir erros (fuzzyThreshold)");
        SearchSettings noFuzzyPass = settings.copy(); noFuzzyPass.fuzzyThreshold = 0;
        expectEmpty(index, noFuzzyPass, "espoda");
        expectContains(index, settings, "espoda", "diamond_sword");

        section("AUDITORIA: limite para misturar idiomas (crossFieldThreshold)");
        SearchSettings noCrossPass = settings.copy(); noCrossPass.crossFieldThreshold = 0;
        expectNotContains(index, noCrossPass, "pomme dourada", "golden_apple");
        expectFirst(index, settings, "pomme dourada", "golden_apple");

        section("AUDITORIA: iniciais agora funcionam com palavras de ligacao");
        expectFirst(index, settings, "bt", "crafting_table");
        expectFirst(index, settings, "bdt", "crafting_table");
        expectFirst(index, settings, "ed", "diamond_sword");
        expectNotContains(index, settings, "tb", "crafting_table");

        section("AUDITORIA: mudar opcao de busca NAO exige remontar o indice");
        SearchSettings a = new SearchSettings();
        SearchSettings b = a.copy();
        b.typoTolerance = 0;
        b.minTypoLength = 9;
        b.matchInitials = false;
        b.ignoreSpaces = false;
        b.sortByRelevance = false;
        b.maxResults = 10;
        b.fuzzyThreshold = 0;
        b.foreignStrictOnly = false;
        b.crossFieldMatching = false;
        expectFalse(b.affectsIndex(a), "opcoes de busca nao invalidam o indice");
        SearchSettings c = a.copy();
        c.crossLanguage = false;
        expectTrue(c.affectsIndex(a), "desligar idiomas invalida o indice");
        SearchSettings d = a.copy();
        d.languages = new ArrayList<>(java.util.Arrays.asList("en_us"));
        expectTrue(d.affectsIndex(a), "trocar a lista de idiomas invalida o indice");
        SearchSettings e = a.copy();
        e.searchTooltips = false;
        expectTrue(e.affectsIndex(a), "tooltips invalidam o indice");

        section("QuickMatcher (nomes de jogadores)");
        expectLoose(settings, "JourneyMap", "jorney", true);
        expectLoose(settings, "JourneyMap", "journey map", true);

        expectLoose(settings, "JourneyMap", "jm", false);
        expectLoose(settings, "Journey Map", "jm", true);
        expectLoose(settings, "Create", "creat", true);
        expectLoose(settings, "Create", "sodium", false);
        expectLoose(settings, "Steve", "steev", true);
        expectLoose(settings, "Notch", "steev", false);
        expectLoose(settings, "Herobrine", "hero", true);
        expectLoose(settings, "Águia", "aguia", true);

        section("Alvo de convite de party (/invite, /group, /grupo, /party)");
        expectLoose(settings, "xSpaceyBubs", "spacey", true);
        expectLoose(settings, "xSpaceyBubs", "spaceybubs", true);
        expectLoose(settings, "xSpaceyBubs", "spacebubs", true);
        expectLoose(settings, "Dream_XD", "dream", true);
        expectLoose(settings, "Dream_XD", "dreem", true);
        expectLoose(settings, "Dream_XD", "notch", false);
        expectLoose(settings, "Player123", "player", true);

        jeiParity();
        browseKeepsListOrder();
        familyGrouping();
        foreignStrict();
        commandCorrection();
        shortcutRule();
        easterEggs();
        auditRegressions();

        benchmark(settings);

        System.out.println();
        if (failures == 0) {
            System.out.println("OK - " + checks + " verificacoes passaram.");
        } else {
            System.out.println("FALHOU - " + failures + " de " + checks + " verificacoes.");
            System.exit(1);
        }
    }

    private static void foreignStrict() {
        section("Idiomas estrangeiros exatos");

        List<SearchIndex.Entry<String>> es = new ArrayList<>();
        es.add(comIdiomas("stone_button", "Stone Button", "Botao de Pedra", "Bouton en pierre"));
        es.add(comIdiomas("oak_button", "Oak Button", "Botao de Carvalho", "Bouton en chene"));
        es.add(comIdiomas("diamond", "Diamond", "Diamante", "Diamant"));
        es.add(comIdiomas("stone", "Stone", "Pedra", "Pierre"));
        SearchIndex<String> idx = new SearchIndex<>(es);

        SearchSettings ligado = new SearchSettings();
        ligado.foreignStrictOnly = true;
        SearchSettings desligado = new SearchSettings();
        desligado.foreignStrictOnly = false;

        expectContains(idx, ligado, "botao", "stone_button", "oak_button");
        expectContains(idx, desligado, "botao", "stone_button", "oak_button");
        expectContains(idx, ligado, "bouton", "stone_button", "oak_button");
        expectContains(idx, ligado, "diamante", "diamond");
        expectContains(idx, ligado, "pedra", "stone");

        expectNothing(idx, ligado, "botaa");
        expectContains(idx, desligado, "botaa", "stone_button", "oak_button");
        expectNothing(idx, ligado, "pdra");
        expectContains(idx, desligado, "pdra", "stone");
        expectNothing(idx, ligado, "diamnte");
        expectContains(idx, desligado, "diamnte", "diamond");
        expectNothing(idx, ligado, "boutan");
        expectContains(idx, desligado, "boutan", "oak_button");

        expectContains(idx, ligado, "stoen", "stone");
        expectContains(idx, desligado, "stoen", "stone");
        expectContains(idx, ligado, "buton", "stone_button");
    }

    private static void shortcutRule() {
        String O = "key.keyboard.o";
        String L = "key.keyboard.l";

        section("Atalho do menu - no padrao, so com Alt");
        expectTrue(ShortcutRule.opens(O, O, true, true), "Alt + O no padrao abre");
        expectFalse(ShortcutRule.opens(O, O, true, false), "O sozinho no padrao NAO abre");

        expectFalse(ShortcutRule.opens(L, O, true, true), "Alt + outra tecla nao abre");

        section("Atalho do menu - rebindado, vale a tecla sozinha");
        expectTrue(ShortcutRule.opens(L, L, false, false), "L sozinho abre depois de rebindar");
        expectTrue(ShortcutRule.opens(L, L, false, true), "Alt segurado nao atrapalha o L");
        expectFalse(ShortcutRule.opens(O, L, false, false), "a tecla velha para de abrir");

        section("Atalho do menu - sem tecla nenhuma");
        expectFalse(ShortcutRule.opens(O, null, true, true), "atalho vazio nunca abre");
        expectFalse(ShortcutRule.opens(O, null, false, false), "atalho vazio nunca abre, rebindado");
        expectFalse(ShortcutRule.opens(null, O, true, true), "tecla nula nunca abre");

        section("Atalho do menu - o nome da tecla e comparado inteiro");
        expectFalse(ShortcutRule.opens("key.keyboard.o", "key.keyboard.0", true, true), "o nao e zero");
        expectFalse(ShortcutRule.opens("key.mouse.left", O, true, true), "botao do mouse nao e o O");
    }

    private static void browseKeepsListOrder() {
        section("Navegar por mod devolve a lista na ordem em que ela entrou (issue #2)");

        SearchSettings settings = new SearchSettings();
        settings.searchModIds = true;
        settings.sortByRelevance = true;

        String[] ids = {
            "powah:energy_cell_nitro", "powah:nitro_sword", "powah:binding_card",
            "powah:blazing_helmet", "powah:steel_energized", "powah:nitro_boots",
            "powah:reactor_basic", "powah:blazing_pickaxe", "powah:dielectric_paste",
        };
        List<SearchIndex.Entry<String>> entries = new ArrayList<>();
        List<String> ordemDeEntrada = new ArrayList<>();
        for (String id : ids) {
            EntryBuilder<String> b = new EntryBuilder<>(id);
            b.modId("powah");
            b.add(id.substring(id.indexOf(':') + 1).replace('_', ' '), SearchField.SOURCE_NATIVE);
            b.add(id, SearchField.SOURCE_ID);
            entries.add(b.build());
            ordemDeEntrada.add(id);
        }

        EntryBuilder<String> outro = new EntryBuilder<>("minecraft:stone");
        outro.modId("minecraft");
        outro.add("Stone", SearchField.SOURCE_NATIVE);
        outro.add("minecraft:stone", SearchField.SOURCE_ID);
        entries.add(outro.build());

        SearchIndex<String> index = new SearchIndex<>(entries);

        checks++;
        SearchQuery browse = SearchQuery.parse("@powah", settings);
        report(browse.isBrowseOnly(), "@powah", "e navegacao, nao busca",
                java.util.Arrays.asList(String.valueOf(browse.isBrowseOnly())));

        checks++;
        List<String> saida = index.search(browse, settings);
        report(saida.equals(ordemDeEntrada), "@powah",
                "os 9 do mod, na ordem de entrada", saida);

        checks++;
        SearchIndex<String> agrupavel = new SearchIndex<>(entries, true);
        List<String> agrupado = agrupavel.search(SearchQuery.parse("@powah", settings), settings);
        report(agrupado.equals(ordemDeEntrada), "@powah (agrupado)",
                "mesma ordem de entrada", agrupado);

        checks++;
        SearchQuery mixed = SearchQuery.parse("@powah cell", settings);
        report(!mixed.isBrowseOnly(), "@powah cell", "volta a ser busca",
                java.util.Arrays.asList(String.valueOf(mixed.isBrowseOnly())));

        checks++;
        List<String> ranked = index.search(mixed, settings);
        report(!ranked.isEmpty() && ranked.get(0).equals("powah:energy_cell_nitro"),
                "@powah cell", "o mais parecido lidera", ranked);

        SearchSettings semMod = settings.copy();
        semMod.searchModIds = false;
        checks++;
        report(!SearchQuery.parse("@powah", semMod).isBrowseOnly()
                        && SearchQuery.isBrowsingByMod("@powah"),
                "@powah (searchModIds off)", "o texto cru ainda e navegacao",
                java.util.Arrays.asList(String.valueOf(SearchQuery.isBrowsingByMod("@powah"))));

        checks++;
        boolean fronteiras = SearchQuery.isBrowsingByMod("@a @b")
                && !SearchQuery.isBrowsingByMod("@powah cell")
                && !SearchQuery.isBrowsingByMod("cell")
                && !SearchQuery.isBrowsingByMod("@")
                && !SearchQuery.isBrowsingByMod("")
                && !SearchQuery.isBrowsingByMod(null);
        report(fronteiras, "isBrowsingByMod", "so texto 100% @mod conta",
                java.util.Arrays.asList(String.valueOf(fronteiras)));
    }

    private static void familyGrouping() {
        section("Resultado agrupado por tipo de item");

        String[][] mundo = {
                {"minecraft:stone_button", "Botao de Pedra"},
                {"minecraft:oak_button", "Botao de Carvalho"},
                {"minecraft:diamond", "Diamante"},
                {"minecraft:leather_boots", "Botas de Couro"},
                {"minecraft:iron_boots", "Botas de Ferro"},
                {"minecraft:netherite_boots", "Botas de Netherite"},
                {"minecraft:bone", "Osso"},
                {"outromod:copper_boots", "Botas de Cobre"},
                {"outromod:copper_ingot", "Barra de Cobre"},
                {"outromod:copper_button", "Botao de Cobre"},
        };
        List<SearchIndex.Entry<String>> entries = new ArrayList<>();
        for (String[] linha : mundo) {
            String id = linha[0];
            int colon = id.indexOf(':');
            EntryBuilder<String> builder = new EntryBuilder<>(id);
            builder.modId(id.substring(0, colon));
            builder.family(id.substring(colon + 1));
            builder.add(linha[1], SearchField.SOURCE_NATIVE);
            builder.add(id.replace(':', ' ').replace('_', ' '), SearchField.SOURCE_ID);
            entries.add(builder.build());
        }
        SearchIndex<String> mundoIndex = new SearchIndex<>(entries);
        SearchSettings s = new SearchSettings();

        expectTogether(mundoIndex, s, "bot", "minecraft:leather_boots", "minecraft:iron_boots",
                "minecraft:netherite_boots", "outromod:copper_boots");

        expectTogether(mundoIndex, s, "bot", "minecraft:stone_button", "minecraft:oak_button",
                "outromod:copper_button");

        expectTogether(mundoIndex, s, "botas", "minecraft:leather_boots", "minecraft:iron_boots",
                "minecraft:netherite_boots", "outromod:copper_boots");

        section("Agrupamento por tipo nao depende de traducao");

        String[][] ovos = {
                {"minecraft:zombie_spawn_egg", "Ovo Gerador de Zumbi", "Zombie Spawn Egg"},
                {"minecraft:creeper_spawn_egg", "Ovo Gerador de Creeper", "Creeper Spawn Egg"},
                {"minecraft:egg", "Ovo", "Egg"},
                {"minecraft:diamond", "Diamante", "Diamond"},
                {"outromod:golem_spawn_egg", "Golem Spawn Egg", "Golem Spawn Egg"},
        };
        List<SearchIndex.Entry<String>> comOvos = new ArrayList<>();
        for (String[] linha : ovos) {
            String id = linha[0];
            int colon = id.indexOf(':');
            EntryBuilder<String> builder = new EntryBuilder<>(id);
            builder.modId(id.substring(0, colon));
            builder.family(id.substring(colon + 1));
            builder.add(linha[1], SearchField.SOURCE_NATIVE);
            builder.add(linha[2], SearchField.SOURCE_ENGLISH);
            builder.add(id.replace(':', ' ').replace('_', ' '), SearchField.SOURCE_ID);
            comOvos.add(builder.build());
        }
        SearchIndex<String> ovoIndex = new SearchIndex<>(comOvos);

        expectTogether(ovoIndex, s, "spawn egg", "minecraft:zombie_spawn_egg",
                "minecraft:creeper_spawn_egg", "outromod:golem_spawn_egg");
        expectTogether(ovoIndex, s, "egg", "minecraft:egg", "minecraft:zombie_spawn_egg",
                "minecraft:creeper_spawn_egg", "outromod:golem_spawn_egg");

        section("Nome escrito por inteiro nao pode perder o primeiro lugar");

        String[] cores = {"branca", "laranja", "magenta", "ciano", "roxa", "vermelha", "preta"};
        List<SearchIndex.Entry<String>> tintas = new ArrayList<>();
        for (String cor : cores) {

            tintas.add(velaEntry(cor + "_vela", "Vela " + nomeBonito(cor)));
        }

        tintas.add(velaEntry("vela", "Vela"));
        SearchIndex<String> tintaIndex = new SearchIndex<>(tintas);

        expectFirst(tintaIndex, s, "vela", "vela");
        expectFirst(tintaIndex, s, "Vela", "vela");
        expectTogether(tintaIndex, s, "vela", "vela", "branca_vela", "laranja_vela",
                "magenta_vela", "ciano_vela", "roxa_vela", "vermelha_vela", "preta_vela");

        section("Lista que o jogador nao le inteira pede o indice sem grupo");

        SearchIndex<String> mundoSemGrupo = new SearchIndex<>(entries, false);
        expectDifferentOrder(mundoIndex, mundoSemGrupo, s, "bot");
        expectSameSet(mundoIndex, mundoSemGrupo, s, "bot");
        expectFirst(new SearchIndex<>(tintas, false), s, "vela", "vela");

        section("Agrupamento nao muda quantos resultados saem");
        SearchSettings comTeto = new SearchSettings();
        comTeto.maxResults = 5;
        expectTrue(run(tintaIndex, comTeto, "vela").size() == 5, "o teto de 5 continua valendo");
        expectTrue(run(tintaIndex, comTeto, "vela").get(0).equals("vela"),
                "com teto, quem casou melhor continua na frente");
        SearchSettings semTeto = new SearchSettings();
        semTeto.maxResults = 0;
        expectTrue(run(tintaIndex, semTeto, "vela").size() == tintas.size(),
                "sem teto saem todas as velas");
        expectSameSet(tintaIndex, new SearchIndex<>(tintas, false), semTeto, "vela");

        expectDifferentOrder(mundoIndex, mundoSemGrupo, comTeto, "bot");

        section("Agrupamento aguenta indice grande");

        List<SearchIndex.Entry<String>> muitos = new ArrayList<>();
        for (int i = 0; i < 70000; i++) {
            EntryBuilder<String> builder = new EntryBuilder<>("item" + i);
            builder.modId("minecraft");
            builder.family("coisa_comum");
            builder.add(i == 69999 ? "Agulha No Palheiro" : ("Palha " + i), SearchField.SOURCE_NATIVE);
            muitos.add(builder.build());
        }
        SearchIndex<String> palheiro = new SearchIndex<>(muitos);
        expectFirst(palheiro, semTeto, "Agulha No Palheiro", "item69999");
        expectTrue(run(palheiro, semTeto, "Agulha No Palheiro").contains("item69999"),
                "posicao acima de 65535 volta inteira");

        section("Armadura e ferramenta saem juntas e na ordem certa");

        String[] materiais = {"leather", "iron", "diamond"};
        String[] pecas = {"helmet", "chestplate", "leggings", "boots"};
        String[] ferramentas = {"sword", "shovel", "pickaxe", "axe", "hoe"};
        List<SearchIndex.Entry<String>> forja = new ArrayList<>();

        for (String material : materiais) {
            for (String peca : pecas) {
                forja.add(equipEntry("minecraft:" + material + "_" + peca));
            }
            for (String ferramenta : ferramentas) {
                forja.add(equipEntry("minecraft:" + material + "_" + ferramenta));
            }
        }
        forja.add(equipEntry("outromod:cobre_boots"));
        forja.add(equipEntry("outromod:cobre_helmet"));
        SearchIndex<String> forjaIndex = new SearchIndex<>(forja);
        SearchSettings tudo = new SearchSettings();
        tudo.maxResults = 0;

        expectTogether(forjaIndex, tudo, "equip",
                "minecraft:leather_helmet", "minecraft:iron_helmet", "minecraft:diamond_helmet",
                "outromod:cobre_helmet",
                "minecraft:leather_chestplate", "minecraft:iron_chestplate",
                "minecraft:diamond_chestplate",
                "minecraft:leather_leggings", "minecraft:iron_leggings",
                "minecraft:diamond_leggings",
                "minecraft:leather_boots", "minecraft:iron_boots", "minecraft:diamond_boots",
                "outromod:cobre_boots");

        expectTogether(forjaIndex, tudo, "equip",
                "minecraft:leather_sword", "minecraft:iron_sword", "minecraft:diamond_sword",
                "minecraft:leather_shovel", "minecraft:iron_shovel", "minecraft:diamond_shovel",
                "minecraft:leather_pickaxe", "minecraft:iron_pickaxe", "minecraft:diamond_pickaxe",
                "minecraft:leather_axe", "minecraft:iron_axe", "minecraft:diamond_axe",
                "minecraft:leather_hoe", "minecraft:iron_hoe", "minecraft:diamond_hoe");

        section("Sem vencedor claro, ninguem sai do lugar no tipo");

        expectFirst(forjaIndex, tudo, "equip", "minecraft:leather_sword");

        section("Vencedor por um nivel inteiro ainda vem na frente do tipo dele");

        List<SearchIndex.Entry<String>> conjunto = new ArrayList<>();
        conjunto.add(pecaEntry("minecraft:diamond_helmet", "Diamante Capacete"));
        conjunto.add(pecaEntry("minecraft:diamond_chestplate", "Diamante Peitoral"));
        conjunto.add(pecaEntry("minecraft:diamond_leggings", "Diamante Calca"));
        conjunto.add(pecaEntry("minecraft:diamond_boots", "Diamante"));
        SearchIndex<String> conjuntoIndex = new SearchIndex<>(conjunto);
        expectTogether(conjuntoIndex, tudo, "diamante", "minecraft:diamond_boots",
                "minecraft:diamond_helmet", "minecraft:diamond_chestplate",
                "minecraft:diamond_leggings");

        List<SearchIndex.Entry<String>> parelho = new ArrayList<>();
        parelho.add(pecaEntry("minecraft:diamond_helmet", "Diamante Capacete"));
        parelho.add(pecaEntry("minecraft:diamond_chestplate", "Diamante Peitoral"));
        parelho.add(pecaEntry("minecraft:diamond_leggings", "Diamante Calcao"));
        parelho.add(pecaEntry("minecraft:diamond_boots", "Diamante Botinha"));
        SearchIndex<String> parelhoIndex = new SearchIndex<>(parelho);
        expectTogether(parelhoIndex, tudo, "diamante", "minecraft:diamond_helmet",
                "minecraft:diamond_chestplate", "minecraft:diamond_leggings",
                "minecraft:diamond_boots");

        section("Tabela de tipos");
        expectKind("helmet", "ARMOR", 0);
        expectKind("chestplate", "ARMOR", 1);
        expectKind("leggings", "ARMOR", 2);
        expectKind("boots", "ARMOR", 3);
        expectKind("sword", "TOOL", 0);
        expectKind("hoe", "TOOL", 4);

        expectKind("button", "button", 0);
        expectKind("", "", 0);

        expectKind("armor", "armor", 0);
        expectTrue(!ItemKinds.kindOf("armor").equals(ItemKinds.kindOf("helmet")),
                "familia armor NAO entra no tipo das armaduras");

        expectTrue(EntryBuilder.familyOf("music_disc_13").equals("disc"), "music_disc_13 pertence aos discos");
        expectTrue(EntryBuilder.familyOf("copper_coil_2").equals("coil"), "copper_coil_2 pertence as bobinas");
        expectTrue(EntryBuilder.familyOf("stone").equals("stone"), "sem numero nada muda");

        section("A familia sai do fim do id do registro");
        expectFamily("Botas_de_COURO", "couro");
        expectFamily("bloco-de-ferro", "bloco de ferro");
        expectFamily("cana\u00e7ucar", "canacucar");
        expectFamily("netherite_boots", "boots");
        expectFamily("leather_boots", "boots");
        expectFamily("polished_blackstone_button", "button");
        expectFamily("zombie_spawn_egg", "egg");
        expectFamily("bone", "bone");
        expectFamily("", "");
        expectFamily(null, "");

        expectFamily("Copper_BOOTS", "boots");
    }

    private static void jeiParity() {
        section("Paridade da busca no JEI/EMI com a do menu");

        EntryBuilder<String> stoneButton = new EntryBuilder<>("stone_button");
        stoneButton.modId("minecraft");
        stoneButton.add("Stone Button", SearchField.SOURCE_NATIVE);
        stoneButton.add("Stone Button", SearchField.SOURCE_ENGLISH);
        stoneButton.add("Botao de Pedra", SearchField.SOURCE_FOREIGN);
        stoneButton.add("Bouton en pierre", SearchField.SOURCE_FOREIGN);
        stoneButton.add("minecraft stone button", SearchField.SOURCE_ID);

        EntryBuilder<String> diamond = new EntryBuilder<>("diamond");
        diamond.modId("minecraft");
        diamond.add("Diamond", SearchField.SOURCE_NATIVE);
        diamond.add("Diamante", SearchField.SOURCE_FOREIGN);
        diamond.add("minecraft diamond", SearchField.SOURCE_ID);

        EntryBuilder<String> moddedButton = new EntryBuilder<>("create:andesite_button");
        moddedButton.modId("create");
        moddedButton.add("Andesite Button", SearchField.SOURCE_NATIVE);
        moddedButton.add("Botao de Andesito", SearchField.SOURCE_FOREIGN);
        moddedButton.add("create andesite button", SearchField.SOURCE_ID);

        SearchIndex<String> index = new SearchIndex<>(java.util.Arrays.asList(
                stoneButton.build(), diamond.build(), moddedButton.build()));

        SearchSettings s = new SearchSettings();

        expectContains(index, s, "buton", "stone_button");

        expectContains(index, s, "botao", "stone_button");
        expectContains(index, s, "bouton", "stone_button");

        expectContains(index, s, "stone_button", "stone_button");
        expectContains(index, s, "sb", "stone_button");
        expectOnlyMod(index, s, "@create button", "create");

        expectFirst(index, s, "button", "stone_button");

        s.foreignStrictOnly = true;
        expectNotContains(index, s, "podra", "stone_button");
        expectNotContains(index, s, "diamnte", "diamond");
        s.foreignStrictOnly = false;
        expectContains(index, s, "podra", "stone_button");
        expectContains(index, s, "butao", "stone_button");
        expectContains(index, s, "diamnte", "diamond");
        s.foreignStrictOnly = true;

        s.languages = new java.util.ArrayList<>(java.util.Arrays.asList("en_us"));
        SearchIndex<String> onlyEnglish = new SearchIndex<>(java.util.Arrays.asList(
                stoneButton.build(), diamond.build(), moddedButton.build()));
        expectContains(onlyEnglish, s, "button", "stone_button");
    }

    private static void easterEggs() {
        section("Apelidos secretos");
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("minecraft:crafting_table", java.util.Arrays.asList("workbench"));
        aliases.put("minecraft:pig_spawn_egg", java.util.Arrays.asList("technoblade"));
        aliases.put("minecraft:potato", java.util.Arrays.asList("technoblade"));
        aliases.put("minecraft:golden_helmet", java.util.Arrays.asList("technoblade"));
        aliases.put("minecraft:red_bed", java.util.Arrays.asList("technoblade"));
        aliases.put("minecraft:spider_spawn_egg", java.util.Arrays.asList("venomextreme", "venoninho", "venom extreme"));
        aliases.put("minecraft:gold_ingot", java.util.Arrays.asList("venomextreme", "venoninho", "venom extreme"));
        aliases.put("minecraft:arrow", java.util.Arrays.asList("venomextreme", "venoninho", "venom extreme"));
        aliases.put("minecraft:cat_spawn_egg", java.util.Arrays.asList("rival", "rivalzin"));
        aliases.put("minecraft:music_disc_wait", java.util.Arrays.asList("rival", "rivalzin"));
        aliases.put("minecraft:fox_spawn_egg", java.util.Arrays.asList("spacey", "spaceybubs", "xspaceybubs"));
        aliases.put("minecraft:brush", java.util.Arrays.asList("spacey", "spaceybubs", "xspaceybubs"));
        aliases.put("minecraft:yellow_dye", java.util.Arrays.asList("spacey", "spaceybubs", "xspaceybubs"));

        List<SearchIndex.Entry<String>> entries = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
            EntryBuilder<String> builder = new EntryBuilder<>(entry.getKey());
            builder.modId("minecraft");
            builder.add(entry.getKey().substring("minecraft:".length()).replace('_', ' '),
                    SearchField.SOURCE_NATIVE);
            for (String alias : entry.getValue()) {
                builder.add(alias, SearchField.SOURCE_NATIVE);
            }
            entries.add(builder.build());
        }

        for (String plain : java.util.Arrays.asList("minecraft:stone", "minecraft:oak_log", "minecraft:bucket")) {
            EntryBuilder<String> builder = new EntryBuilder<>(plain);
            builder.modId("minecraft");
            builder.add(plain.substring("minecraft:".length()).replace('_', ' '), SearchField.SOURCE_NATIVE);
            entries.add(builder.build());
        }
        SearchIndex<String> eggs = new SearchIndex<>(entries);
        SearchSettings settings = new SearchSettings();

        expectExactly(eggs, settings, "workbench", "minecraft:crafting_table");
        expectExactly(eggs, settings, "technoblade", "minecraft:pig_spawn_egg", "minecraft:potato",
                "minecraft:golden_helmet", "minecraft:red_bed");
        expectExactly(eggs, settings, "venomextreme", "minecraft:spider_spawn_egg",
                "minecraft:gold_ingot", "minecraft:arrow");
        expectExactly(eggs, settings, "venoninho", "minecraft:spider_spawn_egg",
                "minecraft:gold_ingot", "minecraft:arrow");
        expectExactly(eggs, settings, "venom extreme", "minecraft:spider_spawn_egg",
                "minecraft:gold_ingot", "minecraft:arrow");
        expectExactly(eggs, settings, "rival", "minecraft:cat_spawn_egg", "minecraft:music_disc_wait");
        expectExactly(eggs, settings, "rivalzin", "minecraft:cat_spawn_egg", "minecraft:music_disc_wait");
        expectExactly(eggs, settings, "spacey", "minecraft:fox_spawn_egg", "minecraft:brush",
                "minecraft:yellow_dye");
        expectExactly(eggs, settings, "spaceybubs", "minecraft:fox_spawn_egg", "minecraft:brush",
                "minecraft:yellow_dye");
        expectExactly(eggs, settings, "xspaceybubs", "minecraft:fox_spawn_egg", "minecraft:brush",
                "minecraft:yellow_dye");

        expectContains(eggs, settings, "techno", "minecraft:potato");
        expectContains(eggs, settings, "spaceybus", "minecraft:brush");
        expectContains(eggs, settings, "tecnhoblade", "minecraft:red_bed");
        expectContains(eggs, settings, "rivalzim", "minecraft:cat_spawn_egg");

        expectNotContains(eggs, settings, "stone", "minecraft:potato");
    }

    private static void expectExactly(SearchIndex<String> index, SearchSettings s,
                                      String query, String... expected) {
        checks++;
        List<String> got = run(index, s, query);
        boolean ok = got.size() == expected.length;
        for (String e : expected) {
            ok &= got.contains(e);
        }
        report(ok, query, expected.length + " itens exatos", got);
    }

    private static final List<String> ROOT_COMMANDS = java.util.Arrays.asList(
            "advancement", "attribute", "ban", "ban-ip", "banlist", "bossbar", "clear", "clone",
            "damage", "data", "datapack", "debug", "defaultgamemode", "deop", "difficulty",
            "effect", "enchant", "execute", "experience", "fill", "fillbiome", "forceload",
            "function", "gamemode", "gamerule", "give", "help", "item", "kick", "kill", "list",
            "locate", "loot", "me", "msg", "op", "pardon", "particle", "place", "playsound",
            "publish", "random", "recipe", "reload", "return", "ride", "save-all", "save-off",
            "save-on", "say", "schedule", "scoreboard", "seed", "setblock", "setidletimeout",
            "setworldspawn", "spawnpoint", "spectate", "spreadplayers", "stop", "stopsound",
            "summon", "tag", "team", "teammsg", "teleport", "tell", "tellraw", "tick", "time",
            "title", "tp", "trigger", "w", "weather", "whitelist", "worldborder", "xp");

    private static final List<String> GAME_MODES =
            java.util.Arrays.asList("survival", "creative", "adventure", "spectator");

    private static final List<String> GAME_RULES = java.util.Arrays.asList(
            "announceAdvancements", "commandBlockOutput", "disableRaids", "doDaylightCycle",
            "doEntityDrops", "doFireTick", "doImmediateRespawn", "doInsomnia", "doMobLoot",
            "doMobSpawning", "doTileDrops", "doWeatherCycle", "drowningDamage", "fallDamage",
            "fireDamage", "keepInventory", "logAdminCommands", "maxEntityCramming",
            "mobGriefing", "naturalRegeneration", "randomTickSpeed", "reducedDebugInfo",
            "sendCommandFeedback", "showDeathMessages", "spawnRadius", "universalAnger");

    private static final List<String> ENTITY_TYPES = java.util.Arrays.asList(
            "minecraft:zombie", "minecraft:zombie_horse", "minecraft:zombie_villager",
            "minecraft:zoglin", "minecraft:skeleton", "minecraft:creeper", "minecraft:cow",
            "minecraft:pig", "minecraft:sheep", "minecraft:villager", "minecraft:enderman",
            "minecraft:spider", "minecraft:wither_skeleton");

    private static final List<String> TIME_VALUES = java.util.Arrays.asList("day", "night", "noon", "midnight");

    private static final List<String> MANY_ITEMS = manyItems();

    private static final List<String> REGISTRY_LIKE = registryLike();

    private static List<String> registryLike() {
        String[] materials = {"wooden", "stone", "iron", "golden", "diamond", "netherite"};
        String[] tools = {"sword", "pickaxe", "axe", "shovel", "hoe"};
        List<String> out = new java.util.ArrayList<>();
        for (String material : materials) {
            for (String tool : tools) {
                out.add("minecraft:" + material + "_" + tool);
            }
        }
        for (int i = 0; i < 280; i++) {
            out.add("minecraft:filler_block_" + (char) ('a' + i % 26) + i);
        }
        return out;
    }

    private static List<String> manyItems() {
        String[] mods = {"minecraft", "create", "mekanism", "thermal", "ae2", "botania"};
        String[] parts = {"ingot", "block", "sword", "pickaxe", "gear", "rod", "plate",
                "dust", "nugget", "diamond", "apple", "stone", "boat", "door"};
        List<String> out = new java.util.ArrayList<>(mods.length * parts.length * parts.length);
        for (String mod : mods) {
            for (String first : parts) {
                for (String second : parts) {
                    out.add(mod + ':' + first + '_' + second);
                }
            }
        }
        out.add("minecraft:enchanted_golden_apple");
        out.add("minecraft:netherite_pickaxe");
        out.add("minecraft:cobblestone");
        return java.util.Collections.unmodifiableList(out);
    }

    private static final List<String> EFFECTS = java.util.Arrays.asList(
            "minecraft:speed", "minecraft:slowness", "minecraft:haste", "minecraft:strength",
            "minecraft:jump_boost", "minecraft:regeneration", "minecraft:invisibility",
            "minecraft:night_vision", "minecraft:water_breathing", "minecraft:fire_resistance");

    private static void commandCorrection() {
        section("Correcao de comandos - nome do comando");
        expectFix("gemamode", ROOT_COMMANDS, "gamemode");
        expectFix("gamemod", ROOT_COMMANDS, "gamemode");
        expectFix("gamerules", ROOT_COMMANDS, "gamerule");
        expectFix("sumon", ROOT_COMMANDS, "summon");
        expectFix("efect", ROOT_COMMANDS, "effect");
        expectFix("tellrow", ROOT_COMMANDS, "tellraw");
        expectFix("wheater", ROOT_COMMANDS, "weather");
        expectFix("scoreboad", ROOT_COMMANDS, "scoreboard");
        expectFix("tpp", ROOT_COMMANDS, "tp");

        section("Correcao de comandos - palavra em outro idioma");
        expectFix("criativo", GAME_MODES, "creative");
        expectFix("creativo", GAME_MODES, "creative");
        expectFix("craetive", GAME_MODES, "creative");
        expectFix("espectador", GAME_MODES, "spectator");
        expectFix("aventura", GAME_MODES, "adventure");
        expectFix("survivel", GAME_MODES, "survival");
        expectFix("zumbi", ENTITY_TYPES, "minecraft:zombie");
        expectFix("creper", ENTITY_TYPES, "minecraft:creeper");
        expectFix("regeneracao", EFFECTS, "minecraft:regeneration");
        expectFix("nightvision", EFFECTS, "minecraft:night_vision");

        section("Correcao de comandos - gamerules e iniciais");
        expectFix("keepInvetory", GAME_RULES, "keepInventory");
        expectFix("keepinventory", GAME_RULES, "keepInventory");
        expectFix("ki", GAME_RULES, "keepInventory");
        expectFix("ddc", GAME_RULES, "doDaylightCycle");
        expectFix("mobgrifing", GAME_RULES, "mobGriefing");
        expectFix("randomtickspeed", GAME_RULES, "randomTickSpeed");

        section("Correcao de comandos - sempre sobra uma saida");

        expectFix("sobrevivencia", GAME_MODES, "survival");
        expectFix("dia", TIME_VALUES, "day");
        expectOneFallback("velocidade", EFFECTS);
        expectOneFallback("aranha", ENTITY_TYPES);
        expectOneFallback("xyzqwk", ROOT_COMMANDS);

        section("Correcao de comandos - quando nao ha o que adivinhar");
        expectNoFix("64", GAME_MODES);
        expectNoFix("a", ROOT_COMMANDS);
        expectNoFix("gamemode", java.util.Arrays.asList());

        section("Correcao de comandos - palavra sem sentido em lista enorme");

        expectNoFix("banana", MANY_ITEMS);
        expectNoFix("futebol", MANY_ITEMS);
        expectNoFix("teclado", MANY_ITEMS);
        expectNoFix("obrigado", MANY_ITEMS);
        expectNoFix("asdfgh", MANY_ITEMS);
        expectNoFix("hello world", MANY_ITEMS);

        expectFixAmong("diamnod sword", MANY_ITEMS, "ae2:diamond_sword");
        expectFixAmong("cobelstone", MANY_ITEMS, "minecraft:cobblestone");
        expectFixAmong("nethrite pikaxe", MANY_ITEMS, "minecraft:netherite_pickaxe");
        expectFixAmong("enchntd gldn appl", MANY_ITEMS, "minecraft:enchanted_golden_apple");

        expectFixAmong("swrd", REGISTRY_LIKE, "minecraft:diamond_sword");
        expectFixAmong("pickax", REGISTRY_LIKE, "minecraft:diamond_pickaxe");
        expectNoFix("banana", REGISTRY_LIKE);

        expectFix("sobrevivencia", GAME_MODES, "survival");

        section("Correcao de comandos - sem teto de resultados");

        expectAtLeast("zombi", ENTITY_TYPES, 4);
        expectAtLeast("do", GAME_RULES, 9);

        section("Correcao de comandos - recorte da palavra");
        expectSpan("/gamemode criativo @a", 18, 10, 18);
        expectSpan("/gamerule keepInvetory true", 21, 10, 22);
        expectSpan("/execute if entity @e[type=zumbi]", 32, 27, 32);
        expectSpan("/gemamode", 9, 1, 9);

        regressoes();
        nomesQueMudaramDeVersao();
    }

    private static void nomesQueMudaramDeVersao() {
        List<String> gamemode = java.util.Arrays.asList("survival", "creative", "adventure", "spectator");
        List<String> dificuldade = java.util.Arrays.asList("peaceful", "easy", "normal", "hard");

        section("Numero de /gamemode e /difficulty (some na 1.13)");
        expectAlias("1", gamemode, "creative");
        expectAlias("0", gamemode, "survival");
        expectAlias("2", gamemode, "adventure");
        expectAlias("3", gamemode, "spectator");

        expectNoAlias("c", gamemode);

        expectAlias("1", dificuldade, "easy");
        expectAlias("0", dificuldade, "peaceful");
        expectAlias("3", dificuldade, "hard");

        expectNoAlias("1", java.util.Arrays.asList("minecraft:stone", "minecraft:dirt"));

        section("Nome de entidade e de item que mudou");
        expectAlias("zombie_pigman", java.util.Arrays.asList("minecraft:zombified_piglin", "minecraft:zombie"),
                "minecraft:zombified_piglin");
        expectAlias("snowman", java.util.Arrays.asList("minecraft:snow_golem"), "minecraft:snow_golem");
        expectAlias("evocation_illager", java.util.Arrays.asList("minecraft:evoker"), "minecraft:evoker");
        expectAlias("record_cat", java.util.Arrays.asList("minecraft:music_disc_cat"), "minecraft:music_disc_cat");

        expectNoAlias("zombified_piglin", java.util.Arrays.asList("minecraft:zombie_pigman"));
        expectNoAlias("music_disc_cat", java.util.Arrays.asList("minecraft:record_cat"));
        expectAlias("web", java.util.Arrays.asList("minecraft:cobweb"), "minecraft:cobweb");
        expectAlias("toggledownfall", java.util.Arrays.asList("weather", "worldborder"), "weather");
        expectAlias("achievement", java.util.Arrays.asList("advancement", "attribute"), "advancement");

        section("Gamerules renomeadas na 1.21.11 (todas mudaram de nome de uma vez)");

        List<String> regras1219 = java.util.Arrays.asList(
                "allowEnteringNetherUsingPortals", "allowFireTicksAwayFromPlayer", "announceAdvancements",
                "blockExplosionDropDecay", "commandBlockOutput", "commandBlocksEnabled",
                "commandModificationBlockLimit", "disableElytraMovementCheck", "disablePlayerMovementCheck",
                "disableRaids", "doDaylightCycle", "doEntityDrops", "doFireTick", "doImmediateRespawn",
                "doInsomnia", "doLimitedCrafting", "doMobLoot", "doMobSpawning", "doPatrolSpawning", "doTileDrops",
                "doTraderSpawning", "doVinesSpread", "doWardenSpawning", "doWeatherCycle", "drowningDamage",
                "enderPearlsVanishOnDeath", "fallDamage", "fireDamage", "forgiveDeadPlayers", "freezeDamage",
                "globalSoundEvents", "keepInventory", "lavaSourceConversion", "locatorBar", "logAdminCommands",
                "maxCommandChainLength", "maxCommandForkCount", "maxEntityCramming", "minecartMaxSpeed",
                "mobExplosionDropDecay", "mobGriefing", "naturalRegeneration", "playersNetherPortalCreativeDelay",
                "playersNetherPortalDefaultDelay", "playersSleepingPercentage", "projectilesCanBreakBlocks", "pvp",
                "randomTickSpeed", "reducedDebugInfo", "sendCommandFeedback", "showDeathMessages",
                "snowAccumulationHeight", "spawnMonsters", "spawnRadius", "spawnerBlocksEnabled",
                "spectatorsGenerateChunks", "tntExplodes", "tntExplosionDropDecay", "universalAnger",
                "waterSourceConversion");

        List<String> regras12111 = java.util.Arrays.asList(
                "advance_time", "advance_weather", "allow_entering_nether_using_portals", "block_drops",
                "block_explosion_drop_decay", "command_block_output", "command_blocks_work", "drowning_damage",
                "elytra_movement_check", "ender_pearls_vanish_on_death", "entity_drops", "fall_damage",
                "fire_damage", "fire_spread_radius_around_player", "forgive_dead_players", "freeze_damage",
                "global_sound_events", "immediate_respawn", "keep_inventory", "lava_source_conversion",
                "limited_crafting", "locator_bar", "log_admin_commands", "max_block_modifications",
                "max_command_forks", "max_command_sequence_length", "max_entity_cramming", "max_minecart_speed",
                "max_snow_accumulation_height", "mob_drops", "mob_explosion_drop_decay", "mob_griefing",
                "natural_health_regeneration", "player_movement_check", "players_nether_portal_creative_delay",
                "players_nether_portal_default_delay", "players_sleeping_percentage",
                "projectiles_can_break_blocks", "pvp", "raids", "random_tick_speed", "reduced_debug_info",
                "respawn_radius", "send_command_feedback", "show_advancement_messages", "show_death_messages",
                "spawn_mobs", "spawn_monsters", "spawn_patrols", "spawn_phantoms", "spawn_wandering_traders",
                "spawn_wardens", "spawner_blocks_work", "spectators_generate_chunks", "spread_vines",
                "tnt_explodes", "tnt_explosion_drop_decay", "universal_anger", "water_source_conversion");

        for (String velha : regras1219) {
            if (!regras12111.contains(velha)) {
                expectAnyAlias(velha, regras12111);
            }
        }
        for (String nova : regras12111) {
            if (!regras1219.contains(nova)) {
                expectAnyAlias(nova, regras1219);
            }
        }

        expectAlias("doDaylightCycle", regras12111, "advance_time");
        expectAlias("doWeatherCycle", regras12111, "advance_weather");
        expectAlias("doMobLoot", regras12111, "mob_drops");
        expectAlias("doTileDrops", regras12111, "block_drops");
        expectAlias("spawnRadius", regras12111, "respawn_radius");
        expectAlias("snowAccumulationHeight", regras12111, "max_snow_accumulation_height");
        expectAlias("commandModificationBlockLimit", regras12111, "max_block_modifications");
        expectAlias("maxCommandChainLength", regras12111, "max_command_sequence_length");
        expectAlias("minecartMaxSpeed", regras12111, "max_minecart_speed");
        expectAlias("commandBlocksEnabled", regras12111, "command_blocks_work");
        expectAlias("spawnerBlocksEnabled", regras12111, "spawner_blocks_work");
        expectAlias("locatorBar", regras12111, "locator_bar");
        expectAlias("tntExplodes", regras12111, "tnt_explodes");
        expectAlias("spawnMonsters", regras12111, "spawn_monsters");

        expectAlias("disableRaids", regras12111, "raids");
        expectAlias("disableElytraMovementCheck", regras12111, "elytra_movement_check");
        expectAlias("disablePlayerMovementCheck", regras12111, "player_movement_check");

        expectAlias("doFireTick", regras12111, "fire_spread_radius_around_player");
        expectAlias("allowFireTicksAwayFromPlayer", regras12111, "fire_spread_radius_around_player");
        expectCount("fire_spread_radius_around_player", regras1219, 2);

        expectAlias("advance_time", regras1219, "doDaylightCycle");
        expectAlias("mob_drops", regras1219, "doMobLoot");
        expectAlias("respawn_radius", regras1219, "spawnRadius");
        expectAlias("show_advancement_messages", regras1219, "announceAdvancements");

        expectNoAlias("pvp", regras12111);

        expectAlias("dodaylightcycle", regras12111, "advance_time");
        expectAlias("DODAYLIGHTCYCLE", regras12111, "advance_time");

        expectNoAlias("doDaylightCycle", regras1219);

        section("Nome pela metade tambem puxa o equivalente");

        expectAlias2("doDayl", regras12111, "advance_time");
        expectAlias2("keepInv", regras12111, "keep_inventory");
        expectAlias2("mobGrief", regras12111, "mob_griefing");
        expectAlias2("advance_ti", regras1219, "doDaylightCycle");
        expectAlias2("record_c", java.util.Arrays.asList("minecraft:music_disc_cat"), "minecraft:music_disc_cat");

        expectCount2("advance", regras1219, 2);

        expectNoAlias2("do", regras12111);
        expectNoAlias2("ke", regras12111);

        expectNoAlias2("doDayl", regras1219);
        expectNoAlias2("keep_inv", regras12111);

        expectNoAlias2("advance_time", regras1219);

        expectNoAlias2("record_c", java.util.Arrays.asList("modx:record_c", "minecraft:music_disc_cat"));

        section("Entrada estranha nao pode derrubar o jogo");

        List<String> comNulo = new ArrayList<>();
        comNulo.add(null);
        comNulo.add("minecraft:keep_inventory");
        comNulo.add("");
        expectAlias("keepInventory", comNulo, "minecraft:keep_inventory");
        expectNoAlias(":", comNulo);
        expectNoAlias("minecraft:", comNulo);
        expectNoAlias("a:b:c", comNulo);
        expectNoAlias("   ", comNulo);

        expectCount("keepInventory", java.util.Arrays.asList("keep_inventory", "keep_inventory"), 1);

        section("Numero solto numa lista grande nao vira sugestao");

        List<String> muitosItens = new ArrayList<>();
        for (int i = 0; i < 1300; i++) {
            muitosItens.add("minecraft:item_" + i);
        }
        expectNoAlias("1", muitosItens);

        expectNoAlias("1", java.util.Arrays.asList("survival", "creative", "minecraft:stone"));
        expectNoAlias("0", muitosItens);
        expectNoAlias("3", muitosItens);

        section("A sugestao nunca sai de fora do que a versao aceita");

        expectNoAlias("zombie_pigman", java.util.Arrays.asList("minecraft:zombie", "minecraft:creeper"));
        expectNoAlias("toggledownfall", java.util.Arrays.asList("time", "weather2"));

        expectNoAlias("creative", gamemode);
        expectNoAlias("minecraft:zombie", java.util.Arrays.asList("minecraft:zombie"));
    }

    private static void expectAlias(String word, List<String> pool, String expected) {
        checks++;
        List<String> got = CommandAliases.matches(word, pool);
        report(!got.isEmpty() && got.get(0).equals(expected), word, expected + " em 1o lugar", got);
    }

    private static void expectAlias2(String word, List<String> pool, String expected) {
        checks++;
        List<String> got = CommandAliases.starting(word, pool);
        report(!got.isEmpty() && got.get(0).equals(expected), word, expected + " em 1o lugar", got);
    }

    private static void expectNoAlias2(String word, List<String> pool) {
        checks++;
        List<String> got = CommandAliases.starting(word, pool);
        report(got.isEmpty(), word, "nenhuma equivalencia por prefixo", got);
    }

    private static void expectCount2(String word, List<String> pool, int expected) {
        checks++;
        List<String> got = CommandAliases.starting(word, pool);
        report(got.size() == expected, word, expected + " equivalencias por prefixo", got);
    }

    private static void expectAnyAlias(String word, List<String> pool) {
        checks++;
        List<String> got = CommandAliases.matches(word, pool);
        report(!got.isEmpty(), word, "alguma equivalencia", got);
    }

    private static void expectCount(String word, List<String> pool, int expected) {
        checks++;
        List<String> got = CommandAliases.matches(word, pool);
        report(got.size() == expected, word, expected + " equivalencias", got);
    }

    private static void expectNoAlias(String word, List<String> pool) {
        checks++;
        List<String> got = CommandAliases.matches(word, pool);
        report(got.isEmpty(), word, "nenhuma equivalencia", got);
    }

    private static void regressoes() {
        section("Regressao: distancia de prefixo nao pode descartar o melhor achado");
        FuzzyMatcher.Scratch scratch = new FuzzyMatcher.Scratch();

        expectDistance("apel", "apple", 1, 1, scratch);
        expectDistance("butao", "button", 1, 1, scratch);
        expectDistance("netherot", "netheritschwert", 1, 1, scratch);
        expectDistance("aple", "apple", 1, 1, scratch);

        section("Regressao: substring com espaco nao pode virar tier de compact");
        MatchPolicy policy = MatchPolicy.of(new SearchSettings(), true);
        expectTier("Diamond Sword", "iamond", FuzzyMatcher.TIER_SUBSTRING, policy, scratch);
        expectTier("Diamond", "iamond", FuzzyMatcher.TIER_SUBSTRING, policy, scratch);

        expectTier("Diamond Sword", "diamondsw", FuzzyMatcher.TIER_COMPACT, policy, scratch);

        section("Regressao: normalizacao devolve tudo em minusculo");

        expectNormalized("Ultra Sword\u2122", "ultra swordtm");
        expectNormalized("Service\u2120", "servicesm");
        expectNormalized("Ba\u00fa", "bau");

        section("Regressao: 5 edicoes nao podem esconder a sugestao certa");
        expectFixAmong("stdltmout", java.util.Arrays.asList("setidletimeout", "stdltmoot"), "setidletimeout");
    }

    private static void expectDistance(String token, String target, int max, int expected,
                                       FuzzyMatcher.Scratch scratch) {
        checks++;
        int got = FuzzyMatcher.prefixDistance(token, target, 0, target.length(), max, scratch);
        report(got == expected, token + " ~ " + target, "distancia " + expected, java.util.Arrays.asList(String.valueOf(got)));
    }

    private static void expectTier(String text, String token, int expected,
                                   MatchPolicy policy, FuzzyMatcher.Scratch scratch) {
        checks++;
        SearchField field = new SearchField(TextNormalizer.normalize(text), SearchField.SOURCE_NATIVE);
        int got = FuzzyMatcher.matchToken(field, token, TextNormalizer.charMask(token), 1, policy, scratch);
        report(got == expected, text + " / " + token, "tier " + expected, java.util.Arrays.asList(String.valueOf(got)));
    }

    private static void expectNormalized(String input, String expected) {
        checks++;
        String got = TextNormalizer.normalize(input);
        report(expected.equals(got), input, '"' + expected + '"', java.util.Arrays.asList(got));
    }

    private static void expectFixAmong(String word, List<String> pool, String expected) {
        checks++;
        List<String> got = CommandFuzzy.best(word, pool);
        report(got.contains(expected), word, "contem " + expected, got);
    }

    private static void expectFix(String word, List<String> pool, String expected) {
        checks++;
        List<String> got = CommandFuzzy.best(word, pool);
        report(!got.isEmpty() && got.get(0).equals(expected), word, expected + " em 1o lugar", got);
    }

    private static void expectNoFix(String word, List<String> pool) {
        checks++;
        List<String> got = CommandFuzzy.best(word, pool);
        report(got.isEmpty(), word, "nenhuma sugestao", got);
    }

    private static void expectOneFallback(String word, List<String> pool) {
        checks++;
        List<String> got = CommandFuzzy.best(word, pool);
        report(got.size() == 1, word, "exatamente 1 (a mais proxima)", got);
    }

    private static void expectAtLeast(String word, List<String> pool, int minimum) {
        checks++;
        List<String> got = CommandFuzzy.best(word, pool);
        report(got.size() >= minimum, word, minimum + " opcoes ou mais", got);
    }

    private static void expectSpan(String input, int at, int start, int end) {
        checks++;
        int gotStart = CommandFuzzy.wordStart(input, at);
        int gotEnd = CommandFuzzy.wordEnd(input, gotStart);
        if (gotStart != start || gotEnd != end) {
            fail("recorte de \"" + input + "\" em " + at + ": esperado [" + start + "," + end
                    + "], veio [" + gotStart + "," + gotEnd + "]");
        }
    }

    private static final class Item {
        private final String id;
        private final String modId;
        private final String nativeName;
        private final Map<String, String> translations;
        private final List<String> tooltip;

        Item(String id, String modId, String nativeName, Map<String, String> translations, List<String> tooltip) {
            this.id = id;
            this.modId = modId;
            this.nativeName = nativeName;
            this.translations = translations;
            this.tooltip = tooltip;
        }

        String id() { return id; }
        String modId() { return modId; }
        String nativeName() { return nativeName; }
        Map<String, String> translations() { return translations; }
        List<String> tooltip() { return tooltip; }
    }

    private static Map<String, String> t(String... codeThenName) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < codeThenName.length; i += 2) {
            map.put(codeThenName[i], codeThenName[i + 1]);
        }
        return map;
    }

    private static final List<Item> ITEMS = java.util.Arrays.asList(
            new Item("apple", "minecraft", "Maçã",
                    t("en_us", "Apple", "fr_fr", "Pomme", "de_de", "Apfel", "es_es", "Manzana",
                            "zh_cn", "苹果", "ja_jp", "りんご", "it_it", "Mela"), java.util.Arrays.asList()),
            new Item("golden_apple", "minecraft", "Maçã Dourada",
                    t("en_us", "Golden Apple", "fr_fr", "Pomme dorée", "de_de", "Goldener Apfel",
                            "es_es", "Manzana dorada", "zh_cn", "金苹果"), java.util.Arrays.asList()),

            new Item("potato", "minecraft", "Batata",
                    t("en_us", "Potato", "fr_fr", "Pomme de terre", "de_de", "Kartoffel"), java.util.Arrays.asList()),
            new Item("chest", "minecraft", "Baú",
                    t("en_us", "Chest", "fr_fr", "Coffre", "de_de", "Truhe", "es_es", "Cofre",
                            "zh_cn", "箱子"), java.util.Arrays.asList()),
            new Item("trapped_chest", "minecraft", "Baú Armadilha",
                    t("en_us", "Trapped Chest", "fr_fr", "Coffre piégé", "de_de", "Redstone-Truhe"), java.util.Arrays.asList()),
            new Item("crafting_table", "minecraft", "Bancada de Trabalho",
                    t("en_us", "Crafting Table", "fr_fr", "Établi", "de_de", "Werkbank",
                            "es_es", "Mesa de trabajo", "zh_cn", "工作台", "ja_jp", "作業台"), java.util.Arrays.asList()),
            new Item("netherite_sword", "minecraft", "Espada de Netherite",
                    t("en_us", "Netherite Sword", "fr_fr", "Épée en Netherite",
                            "de_de", "Netheritschwert"), java.util.Arrays.asList()),
            new Item("diamond_sword", "minecraft", "Espada de Diamante",
                    t("en_us", "Diamond Sword", "fr_fr", "Épée en diamant",
                            "de_de", "Diamantschwert", "zh_cn", "钻石剑"), java.util.Arrays.asList()),
            new Item("diamond", "minecraft", "Diamante",
                    t("en_us", "Diamond", "fr_fr", "Diamant", "zh_cn", "钻石"), java.util.Arrays.asList()),
            new Item("sugar", "minecraft", "Açúcar",
                    t("en_us", "Sugar", "fr_fr", "Sucre", "de_de", "Zucker", "es_es", "Azúcar"), java.util.Arrays.asList()),
            new Item("ender_pearl", "minecraft", "Pérola do Ender",
                    t("en_us", "Ender Pearl", "fr_fr", "Perle de l'Ender", "de_de", "Enderperle"), java.util.Arrays.asList()),
            new Item("stone", "minecraft", "Pedra",
                    t("en_us", "Stone", "fr_fr", "Pierre", "de_de", "Stein", "es_es", "Piedra"), java.util.Arrays.asList()),
            new Item("oak_planks", "minecraft", "Tábuas de Carvalho",
                    t("en_us", "Oak Planks", "fr_fr", "Planches de chêne",
                            "de_de", "Eichenholzbretter"), java.util.Arrays.asList()),
            new Item("enchanted_book", "minecraft", "Livro Encantado",
                    t("en_us", "Enchanted Book", "fr_fr", "Livre enchanté",
                            "de_de", "Verzaubertes Buch"),
                    java.util.Arrays.asList("Afiação IV", "Sharpness IV")),
            new Item("create:cogwheel", "create", "Engrenagem",
                    t("en_us", "Cogwheel", "fr_fr", "Roue dentée", "de_de", "Zahnrad"), java.util.Arrays.asList()),
            new Item("create:shaft", "create", "Eixo",
                    t("en_us", "Shaft", "fr_fr", "Arbre", "de_de", "Welle"), java.util.Arrays.asList()));

    private static SearchIndex<String> buildIndex(SearchSettings settings) {
        List<SearchIndex.Entry<String>> entries = new ArrayList<>();
        for (Item item : ITEMS) {
            EntryBuilder<String> b = new EntryBuilder<>(item.id());
            b.modId(item.modId());
            b.add(item.nativeName(), SearchField.SOURCE_NATIVE);
            for (Map.Entry<String, String> translation : item.translations().entrySet()) {
                if (!settings.indexesLanguage(translation.getKey())) {
                    continue;
                }
                b.add(translation.getValue(), translation.getKey().equals("en_us")
                        ? SearchField.SOURCE_ENGLISH
                        : SearchField.SOURCE_FOREIGN);
            }
            b.add(item.id().contains(":") ? item.id() : "minecraft:" + item.id(), SearchField.SOURCE_ID);
            for (String line : item.tooltip()) {
                b.add(line, SearchField.SOURCE_TOOLTIP);
            }
            entries.add(b.build());
        }
        return new SearchIndex<>(entries);
    }

    private static List<String> run(SearchIndex<String> index, SearchSettings settings, String query) {
        return index.search(SearchQuery.parse(query, settings), settings);
    }

    private static void auditRegressions() {
        section("Auditoria: isolamento, limites, concorrencia e oraculo de distancia");
        SearchSettings settings = new SearchSettings();
        SearchIndex<String> index = buildIndex(settings);
        expectTrue(SearchQuery.parse(null, settings).isEmpty(), "consulta nula e vazia");
        SearchSettings limited = settings.copy();
        limited.maxResults = 1;
        expectCount(index, limited, "", 1);
        expectCount(index, limited, "@minecraft", 1);
        expectCount(index, limited, "@create", 1);
        expectNormalized("\u00a7aDiamond \u00a7lSword\u00a7r", "diamond sword");
        expectNormalized("\u00a7x\u00a7f\u00a7f\u00a70\u00a70\u00a70\u00a70Diamond", "diamond");

        SearchField[] suppliedFields = { new SearchField("diamond", SearchField.SOURCE_NATIVE) };
        SearchIndex.Entry<String> supplied = new SearchIndex.Entry<>("diamond", suppliedFields, "minecraft", "");
        List<SearchIndex.Entry<String>> suppliedEntries = new ArrayList<>();
        suppliedEntries.add(supplied);
        SearchIndex<String> isolated = new SearchIndex<>(suppliedEntries);
        suppliedEntries.clear();
        suppliedFields[0] = new SearchField("stone", SearchField.SOURCE_NATIVE);
        supplied.fields()[0] = new SearchField("stone", SearchField.SOURCE_NATIVE);
        expectFirst(isolated, settings, "diamond", "diamond");
        try {
            isolated.entries().clear();
            expectTrue(false, "lista do indice deve ser imutavel");
        } catch (UnsupportedOperationException expected) {
            expectTrue(true, "lista do indice imutavel");
        }

        EntryBuilder<String> nativeAfterForeign = new EntryBuilder<>("diamond");
        nativeAfterForeign.add("diamond", SearchField.SOURCE_FOREIGN);
        nativeAfterForeign.add("diamond", SearchField.SOURCE_NATIVE);
        expectFirst(new SearchIndex<>(java.util.Arrays.asList(nativeAfterForeign.build())), settings,
                "dimaond", "diamond");
        EntryBuilder<String> nativeAfterTooltip = new EntryBuilder<>("diamond");
        nativeAfterTooltip.add("diamond", SearchField.SOURCE_TOOLTIP);
        nativeAfterTooltip.add("diamond", SearchField.SOURCE_NATIVE);
        SearchSettings noTooltip = settings.copy();
        noTooltip.searchTooltips = false;
        expectFirst(new SearchIndex<>(java.util.Arrays.asList(nativeAfterTooltip.build())), noTooltip,
                "diamond", "diamond");

        SearchSettings nullable = new SearchSettings();
        nullable.languages = null;
        expectTrue(nullable.copy().languages.equals(SearchSettings.DEFAULT_LANGUAGES), "copia recupera idiomas nulos");
        expectTrue(nullable.indexesLanguage("en_us"), "idiomas nulos usam padrao");
        expectTrue(nullable.toString().contains("languages=null"), "diagnostico aceita idiomas nulos");
        copySettingsAudit();
        distanceOracleAudit();
        commandTopKAudit();
        concurrentSearchAudit(index, settings);
    }

    private static void copySettingsAudit() {
        try {
            for (java.lang.reflect.Field field : SearchSettings.class.getFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                SearchSettings original = new SearchSettings();
                SearchSettings changed = original.copy();
                if (field.getType() == boolean.class) {
                    field.setBoolean(changed, !field.getBoolean(original));
                } else if (field.getType() == int.class) {
                    field.setInt(changed, field.getInt(original) + 1);
                } else if (field.getType() == List.class) {
                    changed.languages = new ArrayList<>(java.util.Arrays.asList("test_language"));
                } else {
                    throw new AssertionError("Novo tipo de configuracao sem teste: " + field.getName());
                }
                SearchSettings copy = changed.copy();
                SearchSettings target = new SearchSettings();
                target.copyFrom(changed);
                expectTrue(!changed.equals(original), field.getName() + " participa de equals");
                expectTrue(changed.equals(copy) && changed.equals(target)
                                && changed.hashCode() == copy.hashCode(), field.getName() + " e copiado");
                expectTrue(copy.languages != changed.languages && target.languages != changed.languages,
                        field.getName() + " preserva isolamento da lista");
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void distanceOracleAudit() {
        java.util.Random random = new java.util.Random(17012026L);
        FuzzyMatcher.Scratch scratch = new FuzzyMatcher.Scratch();
        try {
            java.lang.reflect.Method commandDistance = CommandFuzzy.class.getDeclaredMethod(
                    "distance", String.class, String.class, int.class);
            commandDistance.setAccessible(true);
            for (int test = 0; test < 5000; test++) {
                String token = randomWord(random, random.nextInt(21));
                String target = randomWord(random, random.nextInt(21));
                int max = random.nextInt(5);
                int[][] reference = referenceDistance(token, target);
                int expectedPrefix = token.length();
                for (int end = 0; end <= target.length(); end++) {
                    expectedPrefix = Math.min(expectedPrefix, reference[token.length()][end]);
                }
                int prefix = FuzzyMatcher.prefixDistance(token, "xx" + target + "yy", 2,
                        target.length() + 2, max, scratch);
                checks++;
                if (expectedPrefix <= max ? prefix != expectedPrefix : prefix <= max) {
                    fail("prefixDistance divergiu do oraculo para " + token + " / " + target + " / " + max);
                }
                int expectedFull = reference[token.length()][target.length()];
                int full = (Integer) commandDistance.invoke(null, token, target, max);
                checks++;
                if (full != (expectedFull <= max ? expectedFull : -1)) {
                    fail("CommandFuzzy.distance divergiu do oraculo para " + token + " / " + target);
                }
            }
            expectTrue(true, "10.000 comparacoes com matriz OSA independente");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static String randomWord(java.util.Random random, int length) {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append((char) ('a' + random.nextInt(4)));
        }
        return text.toString();
    }

    private static int[][] referenceDistance(String token, String target) {
        int[][] distance = new int[token.length() + 1][target.length() + 1];
        for (int i = 0; i <= token.length(); i++) {
            distance[i][0] = i;
        }
        for (int j = 0; j <= target.length(); j++) {
            distance[0][j] = j;
        }
        for (int i = 1; i <= token.length(); i++) {
            for (int j = 1; j <= target.length(); j++) {
                int cost = token.charAt(i - 1) == target.charAt(j - 1) ? 0 : 1;
                int best = Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1);
                best = Math.min(best, distance[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && token.charAt(i - 1) == target.charAt(j - 2)
                        && token.charAt(i - 2) == target.charAt(j - 1)) {
                    best = Math.min(best, distance[i - 2][j - 2] + 1);
                }
                distance[i][j] = best;
            }
        }
        return distance;
    }

    private static void commandTopKAudit() {
        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            candidates.add("minecraft:diamond_sword_" + i);
        }
        candidates.add("minecraft:diamond_sword");
        candidates.add("diamond");
        candidates.add("diamond");
        List<String> complete = CommandFuzzy.best("diamond", candidates, candidates.size());
        expectTrue(new java.util.HashSet<>(complete).size() == complete.size(), "sugestoes sem duplicatas");
        for (int limit : new int[] { 1, 2, 12, 100, 600 }) {
            List<String> limited = CommandFuzzy.best("diamond", candidates, limit);
            expectTrue(limited.equals(complete.subList(0, Math.min(limit, complete.size()))),
                    "top K preserva ranking com limite " + limit);
        }
    }

    private static void concurrentSearchAudit(SearchIndex<String> index, SearchSettings settings) {
        String[] queries = { "diamond", "dimaond", "pomme dourada", "zzzzqqq", "@create", "" };
        List<List<String>> expected = new ArrayList<>();
        for (String query : queries) {
            expected.add(run(index, settings, query));
        }
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        List<java.util.concurrent.Future<?>> pending = new ArrayList<>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                pending.add(executor.submit(() -> {
                    for (int repeat = 0; repeat < 150; repeat++) {
                        for (int i = 0; i < queries.length; i++) {
                            if (!run(index, settings, queries[i]).equals(expected.get(i))) {
                                throw new AssertionError("Busca concorrente alterou " + queries[i]);
                            }
                        }
                    }
                }));
            }
            for (java.util.concurrent.Future<?> future : pending) {
                future.get(20, java.util.concurrent.TimeUnit.SECONDS);
            }
            expectTrue(true, "3.600 buscas concorrentes preservam resultados");
        } catch (Exception failure) {
            throw new AssertionError(failure);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    private static void expectFirst(SearchIndex<String> index, SearchSettings s, String query, String expected) {
        checks++;
        List<String> got = run(index, s, query);
        boolean ok = !got.isEmpty() && got.get(0).equals(expected);
        report(ok, query, expected + " em 1o lugar", got);
    }

    private static void expectContains(SearchIndex<String> index, SearchSettings s, String query, String... expected) {
        checks++;
        List<String> got = run(index, s, query);
        boolean ok = true;
        for (String e : expected) {
            ok &= got.contains(e);
        }
        report(ok, query, "contem " + String.join(", ", expected), got);
    }

    private static void expectNotContains(SearchIndex<String> index, SearchSettings s, String query, String unwanted) {
        checks++;
        List<String> got = run(index, s, query);
        report(!got.contains(unwanted), query, "nao contem " + unwanted, got);
    }

    private static void expectTogether(SearchIndex<String> index, SearchSettings s, String query,
                                       String... members) {
        checks++;
        List<String> got = run(index, s, query);
        int first = got.indexOf(members[0]);
        boolean ok = first >= 0 && first + members.length <= got.size();
        if (ok) {
            for (int i = 0; i < members.length; i++) {
                ok &= got.get(first + i).equals(members[i]);
            }
        }
        report(ok, query, "colados: " + String.join(" ", members), got);
    }

    private static SearchIndex.Entry<String> comIdiomas(String id, String ingles, String pt, String fr) {
        EntryBuilder<String> builder = new EntryBuilder<>(id);
        builder.modId("minecraft");
        builder.family(id);
        builder.add(ingles, SearchField.SOURCE_NATIVE);
        builder.add(ingles, SearchField.SOURCE_ENGLISH);
        builder.add(pt, SearchField.SOURCE_FOREIGN);
        builder.add(fr, SearchField.SOURCE_FOREIGN);
        return builder.build();
    }

    private static void expectNothing(SearchIndex<String> index, SearchSettings s, String query) {
        checks++;
        List<String> got = run(index, s, query);
        report(got.isEmpty(), query, "nenhum resultado", got);
    }

    private static SearchIndex.Entry<String> equipEntry(String id) {
        int colon = id.indexOf(':');
        String path = id.substring(colon + 1);
        EntryBuilder<String> builder = new EntryBuilder<>(id);
        builder.modId(id.substring(0, colon));
        builder.family(path);
        builder.add("Equip " + path.replace('_', ' '), SearchField.SOURCE_NATIVE);
        return builder.build();
    }

    private static SearchIndex.Entry<String> pecaEntry(String id, String nome) {
        int colon = id.indexOf(':');
        EntryBuilder<String> builder = new EntryBuilder<>(id);
        builder.modId(id.substring(0, colon));
        builder.family(id.substring(colon + 1));
        builder.add(nome, SearchField.SOURCE_NATIVE);
        return builder.build();
    }

    private static void expectKind(String family, String kind, int order) {
        checks++;
        String gotKind = ItemKinds.kindOf(family);
        int gotOrder = ItemKinds.orderOf(family);
        report(kind.equals(gotKind) && order == gotOrder, family,
                kind + " na posicao " + order, java.util.Arrays.asList(gotKind + " " + gotOrder));
    }

    private static SearchIndex.Entry<String> velaEntry(String id, String nome) {
        EntryBuilder<String> builder = new EntryBuilder<>(id);
        builder.modId("minecraft");
        builder.family(id);
        builder.add(nome, SearchField.SOURCE_NATIVE);
        return builder.build();
    }

    private static void expectSameSet(SearchIndex<String> a, SearchIndex<String> b,
                                      SearchSettings s, String query) {
        checks++;
        List<String> um = run(a, s, query);
        List<String> outro = run(b, s, query);
        boolean ok = um.size() == outro.size() && new java.util.HashSet<>(um).equals(new java.util.HashSet<>(outro));
        report(ok, query, "mesmo conjunto nos dois indices", um);
    }

    private static void expectDifferentOrder(SearchIndex<String> a, SearchIndex<String> b,
                                             SearchSettings s, String query) {
        checks++;
        List<String> um = run(a, s, query);
        List<String> outro = run(b, s, query);
        report(!um.equals(outro), query, "ordem diferente com e sem agrupamento", um);
    }

    private static String nomeBonito(String bruto) {
        StringBuilder out = new StringBuilder();
        for (String pedaco : bruto.split("_")) {
            out.append(Character.toUpperCase(pedaco.charAt(0))).append(pedaco.substring(1)).append(' ');
        }
        return out.toString().trim();
    }

    private static void expectFamily(String registryPath, String expected) {
        checks++;
        String got = new EntryBuilder<String>("x").family(registryPath).build().family;
        report(expected.equals(got), registryPath, "familia " + expected, java.util.Arrays.asList(got));
    }

    private static void expectOrder(SearchIndex<String> index, SearchSettings s, String query, String before, String after) {
        checks++;
        List<String> got = run(index, s, query);
        int i = got.indexOf(before);
        int j = got.indexOf(after);
        report(i >= 0 && j >= 0 && i < j, query, before + " antes de " + after, got);
    }

    private static void expectTrue(boolean value, String what) {
        checks++;
        report(value, what, "verdadeiro", java.util.Arrays.asList());
    }

    private static void expectFalse(boolean value, String what) {
        checks++;
        report(!value, what, "falso", java.util.Arrays.asList());
    }

    private static void expectLoose(SearchSettings s, String text, String query, boolean expected) {
        checks++;
        boolean got = com.rivalzin.bettersearch.core.QuickMatcher.matches(text, query, s);
        report(got == expected, query,
                (expected ? "casa com " : "nao casa com ") + '"' + text + '"',
                got ? java.util.Arrays.asList(text) : java.util.Arrays.asList());
    }

    private static void expectCount(SearchIndex<String> index, SearchSettings s, String query, int expected) {
        checks++;
        List<String> got = run(index, s, query);
        report(got.size() == expected, query, expected + " resultado(s)", got);
    }

    private static void expectEmpty(SearchIndex<String> index, SearchSettings s, String query) {
        checks++;
        List<String> got = run(index, s, query);
        report(got.isEmpty(), query, "nenhum resultado", got);
    }

    private static void expectOnlyMod(SearchIndex<String> index, SearchSettings s, String query, String modId) {
        checks++;
        List<String> got = run(index, s, query);
        boolean ok = !got.isEmpty();
        for (String id : got) {
            ok &= id.startsWith(modId + ":");
        }
        report(ok, query, "so itens de " + modId, got);
    }

    private static void fail(String message) {
        failures++;
        System.out.println("FALHA  " + message);
    }

    private static void report(boolean ok, String query, String expectation, List<String> got) {
        if (!ok) {
            failures++;
        }
        System.out.printf("%s  %-24s -> %-34s %s%n",
                ok ? "  ok" : "FALHA", '"' + query + '"', expectation, preview(got));
    }

    private static String preview(List<String> got) {
        int n = Math.min(4, got.size());
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(got.get(i));
        }
        if (got.size() > n) {
            sb.append(", +").append(got.size() - n);
        }
        return sb.append(']').toString();
    }

    private static void benchmark(SearchSettings settings) {
        section("Desempenho (indice sintetico de 20.000 itens x 6 idiomas)");
        List<SearchIndex.Entry<String>> entries = new ArrayList<>(20_000);
        String[] parts = {"diamond", "netherite", "copper", "amethyst", "deepslate", "cherry",
                "sculk", "prismarine", "blackstone", "warped", "crimson", "bamboo"};
        String[] kinds = {"sword", "block", "stairs", "slab", "door", "trapdoor", "button",
                "pressure plate", "fence", "wall", "helmet", "chestplate"};
        for (int i = 0; i < 20_000; i++) {
            String en = parts[i % parts.length] + " " + kinds[(i / parts.length) % kinds.length] + " " + i;
            EntryBuilder<String> b = new EntryBuilder<>("item" + i);
            b.modId("testmod");
            b.add(en, SearchField.SOURCE_NATIVE);
            b.add("bloco de teste " + i, SearchField.SOURCE_ENGLISH);
            b.add("bloc de test " + i, SearchField.SOURCE_FOREIGN);
            b.add("testblock " + i, SearchField.SOURCE_FOREIGN);
            b.add("测试方块 " + i, SearchField.SOURCE_FOREIGN);
            b.add("testmod:item_" + i, SearchField.SOURCE_ID);
            entries.add(b.build());
        }
        SearchIndex<String> big = new SearchIndex<>(entries);

        String[] queries = {"d", "di", "dia", "diam", "diamo", "diamond", "diamond s",
                "diamond sword", "netherrite", "xyzabc", "deepslat stiars"};
        for (String q : queries) {
            SearchQuery parsed = SearchQuery.parse(q, settings);
            for (int i = 0; i < 3; i++) {
                big.search(parsed, settings);
            }
            long start = System.nanoTime();
            int size = 0;
            for (int i = 0; i < 10; i++) {
                size = big.search(parsed, settings).size();
            }
            double ms = (System.nanoTime() - start) / 10.0 / 1_000_000.0;
            System.out.printf("  %-18s %6.2f ms  (%d resultados)%n", '"' + q + '"', ms, size);
        }
    }
}
