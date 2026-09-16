package com.rivalzin.bettersearch.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SearchSettings {
    public static final List<String> DEFAULT_LANGUAGES = Collections.unmodifiableList(Arrays.asList(
            "en_us", "es_es", "es_mx", "pt_br", "pt_pt", "fr_fr", "de_de", "it_it",
            "nl_nl", "pl_pl", "ru_ru", "uk_ua", "tr_tr", "sv_se",
            "zh_cn", "zh_tw", "ja_jp", "ko_kr"));

    public boolean enabled = true;

    public boolean searchCreative = true;

    public boolean searchRecipeBook = true;

    public boolean searchPlayerNames = true;

    public boolean searchCommandItems = true;

    public boolean fixCommandErrors = true;

    public boolean fixVersionNames = true;

    public int commandSuggestionLimit = 12;

    public boolean searchJei = true;

    public boolean searchEmi = true;

    public boolean searchRei = true;

    public int typoTolerance = 2;

    public int minTypoLength = 4;

    public boolean matchInitials = true;

    public boolean ignoreSpaces = true;

    public boolean crossLanguage = true;

    public List<String> languages = new ArrayList<>(DEFAULT_LANGUAGES);

    public boolean foreignStrictOnly = true;

    public boolean sortByRelevance = true;

    public boolean searchTooltips = true;

    public boolean searchItemIds = true;

    public boolean searchModIds = true;

    public int fuzzyThreshold = 60;

    public boolean crossFieldMatching = true;

    public int crossFieldThreshold = 20;

    public int maxResults = 0;

    public SearchSettings copy() {
        SearchSettings copy = new SearchSettings();
        copy.copyFrom(this);
        return copy;
    }

    public void copyFrom(SearchSettings source) {
        java.util.Objects.requireNonNull(source, "source");
        enabled = source.enabled;
        searchCreative = source.searchCreative;
        searchRecipeBook = source.searchRecipeBook;
        searchPlayerNames = source.searchPlayerNames;
        searchCommandItems = source.searchCommandItems;
        fixCommandErrors = source.fixCommandErrors;
        fixVersionNames = source.fixVersionNames;
        commandSuggestionLimit = source.commandSuggestionLimit;
        searchJei = source.searchJei;
        searchEmi = source.searchEmi;
        searchRei = source.searchRei;
        typoTolerance = source.typoTolerance;
        minTypoLength = source.minTypoLength;
        matchInitials = source.matchInitials;
        ignoreSpaces = source.ignoreSpaces;
        crossLanguage = source.crossLanguage;
        languages = new ArrayList<>(source.languages == null ? DEFAULT_LANGUAGES : source.languages);
        foreignStrictOnly = source.foreignStrictOnly;
        sortByRelevance = source.sortByRelevance;
        searchTooltips = source.searchTooltips;
        searchItemIds = source.searchItemIds;
        searchModIds = source.searchModIds;
        fuzzyThreshold = source.fuzzyThreshold;
        crossFieldMatching = source.crossFieldMatching;
        crossFieldThreshold = source.crossFieldThreshold;
        maxResults = source.maxResults;
    }

    public void sanitize() {
        typoTolerance = clamp(typoTolerance, 0, 3);
        minTypoLength = clamp(minTypoLength, 3, 10);
        commandSuggestionLimit = clamp(commandSuggestionLimit, 1, 50);
        fuzzyThreshold = clamp(fuzzyThreshold, 0, 100_000);
        crossFieldThreshold = clamp(crossFieldThreshold, 0, 100_000);
        maxResults = Math.max(0, maxResults);
        if (languages == null) {
            languages = new ArrayList<>(DEFAULT_LANGUAGES);
        } else {
            List<String> cleaned = new ArrayList<>();
            for (String raw : languages) {
                if (raw == null) {
                    continue;
                }
                String code = raw.trim().toLowerCase(java.util.Locale.ROOT);
                if (!code.isEmpty() && !cleaned.contains(code)) {
                    cleaned.add(code);
                }
            }
            languages = cleaned;
        }
    }

    public boolean indexesAllLanguages() {
        return languages != null && languages.contains("*");
    }

    public boolean indexesLanguage(String code) {
        return crossLanguage && (indexesAllLanguages()
                || (languages == null ? DEFAULT_LANGUAGES : languages).contains(code));
    }

    public boolean affectsIndex(SearchSettings other) {
        return crossLanguage != other.crossLanguage
                || searchTooltips != other.searchTooltips
                || searchItemIds != other.searchItemIds
                || !sameLanguages(languages, other.languages);
    }

    public boolean affectsLanguageTable(SearchSettings other) {
        return crossLanguage != other.crossLanguage || !sameLanguages(languages, other.languages);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SearchSettings)) {
            return false;
        }
        SearchSettings s = (SearchSettings) other;
        return enabled == s.enabled
                && searchCreative == s.searchCreative
                && searchRecipeBook == s.searchRecipeBook
                && searchPlayerNames == s.searchPlayerNames
                && searchCommandItems == s.searchCommandItems
                && fixCommandErrors == s.fixCommandErrors
                && fixVersionNames == s.fixVersionNames
                && commandSuggestionLimit == s.commandSuggestionLimit
                && searchJei == s.searchJei
                && searchEmi == s.searchEmi
                && searchRei == s.searchRei
                && typoTolerance == s.typoTolerance
                && minTypoLength == s.minTypoLength
                && matchInitials == s.matchInitials
                && ignoreSpaces == s.ignoreSpaces
                && crossLanguage == s.crossLanguage
                && foreignStrictOnly == s.foreignStrictOnly
                && sortByRelevance == s.sortByRelevance
                && searchTooltips == s.searchTooltips
                && searchItemIds == s.searchItemIds
                && searchModIds == s.searchModIds
                && fuzzyThreshold == s.fuzzyThreshold
                && crossFieldMatching == s.crossFieldMatching
                && crossFieldThreshold == s.crossFieldThreshold
                && maxResults == s.maxResults
                && sameLanguages(languages, s.languages);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(enabled, searchCreative, searchRecipeBook,
                searchPlayerNames, searchCommandItems, fixCommandErrors, fixVersionNames,
                commandSuggestionLimit,
                searchJei, searchEmi, searchRei,
                typoTolerance, minTypoLength, matchInitials, ignoreSpaces,
                crossLanguage, foreignStrictOnly, sortByRelevance, searchTooltips, searchItemIds,
                searchModIds, fuzzyThreshold, crossFieldMatching, crossFieldThreshold, maxResults,
                languages == null ? null : new java.util.HashSet<>(languages));
    }

    private static boolean sameLanguages(java.util.List<String> a, java.util.List<String> b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return new java.util.HashSet<>(a).equals(new java.util.HashSet<>(b));
    }

    @Override
    public String toString() {
        return "SearchSettings{enabled=" + enabled
                + ", typoTolerance=" + typoTolerance
                + ", crossLanguage=" + crossLanguage
                + ", languages=" + languages
                + ", foreignStrictOnly=" + foreignStrictOnly
                + ", sortByRelevance=" + sortByRelevance
                + ", searchTooltips=" + searchTooltips
                + '}';
    }
}
