package com.rivalzin.bettersearch.client.gui;

import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.LanguageCatalog;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class BetterSearchConfigScreen extends OptionRowsScreen {
    private static final String YOUTUBE_URL = "https://www.youtube.com/@Rivalzln";
    private static final String KOFI_URL = "https://ko-fi.com/rivalzin";

    private static final SearchSettings DEFAULTS = new SearchSettings();

    private enum Tab {
        GENERAL("general"),
        MATCHING("matching"),
        LANGUAGES("languages"),
        ADVANCED("advanced");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private final SearchSettings settings;
    private final SearchSettings opened;
    private Tab tab = Tab.GENERAL;

    private Button defaultsButton;
    private Button undoButton;

    public BetterSearchConfigScreen(Screen parent) {
        super(Component.translatable("bettersearch.config.title"), parent);
        this.settings = BetterSearchClient.settings().copy();
        this.opened = this.settings.copy();
    }

    @Override
    protected int tabsHeight() {
        return TabButton.HEIGHT;
    }

    @Override
    protected int panelFooterHeight() {
        return 2 * BUTTON_GAP + 4;
    }

    @Override
    protected int listBottomInset() {
        return BUTTON_GAP + 2;
    }

    @Override
    protected void buildTabs() {
        Tab[] tabs = Tab.values();
        int available = this.width - 2 * MARGIN - (tabs.length - 1) * 2;
        int each = available / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab value = tabs[i];
            int x = MARGIN + i * (each + 2);
            int width = i == tabs.length - 1 ? this.width - MARGIN - x : each;
            addFixed(new TabButton(x, MARGIN, width,
                    Component.translatable("bettersearch.config.tab." + value.key),
                    () -> tab == value,
                    () -> select(value)));
        }
    }

    private void select(Tab value) {
        if (tab != value) {
            tab = value;
            resetScroll();
            rebuildWidgets();
        }
    }

    @Override
    protected void buildRows() {
        switch (tab) {
            case GENERAL -> {
                addToggle("enabled", () -> settings.enabled,
                        v -> settings.enabled = v, DEFAULTS.enabled);
                addToggle("sort_by_relevance", () -> settings.sortByRelevance,
                        v -> settings.sortByRelevance = v, DEFAULTS.sortByRelevance);
                addSlider("max_results", 0, 2000, 50, () -> settings.maxResults,
                        v -> settings.maxResults = v, DEFAULTS.maxResults,
                        BetterSearchConfigScreen::resultCountLabel);
            }
            case MATCHING -> {
                addSlider("typo_tolerance", 0, 3, 1, () -> settings.typoTolerance,
                        v -> settings.typoTolerance = v, DEFAULTS.typoTolerance,
                        BetterSearchConfigScreen::typoToleranceLabel);
                addSlider("min_typo_length", 3, 10, 1, () -> settings.minTypoLength,
                        v -> settings.minTypoLength = v, DEFAULTS.minTypoLength,
                        value -> Component.translatable("bettersearch.config.value.letters", value));
                addToggle("match_initials", () -> settings.matchInitials,
                        v -> settings.matchInitials = v, DEFAULTS.matchInitials)
                        .preview(previewOf("match_initials"));
                addToggle("ignore_spaces", () -> settings.ignoreSpaces,
                        v -> settings.ignoreSpaces = v, DEFAULTS.ignoreSpaces)
                        .preview(previewOf("ignore_spaces"));
                addToggle("search_tooltips", () -> settings.searchTooltips,
                        v -> settings.searchTooltips = v, DEFAULTS.searchTooltips)
                        .preview(previewOf("search_tooltips"));
                addToggle("search_ids", () -> settings.searchItemIds,
                        v -> settings.searchItemIds = v, DEFAULTS.searchItemIds)
                        .preview(previewOf("search_ids"));
                addToggle("search_mods", () -> settings.searchModIds,
                        v -> settings.searchModIds = v, DEFAULTS.searchModIds)
                        .preview(previewOf("search_mods"));
                addSlider("fuzzy_threshold", 0, EFFORT_STEPS, 1,
                        () -> effortLevel(FUZZY_LEVELS, settings.fuzzyThreshold),
                        v -> settings.fuzzyThreshold = FUZZY_LEVELS[Mth.clamp(v, 0, EFFORT_STEPS)],
                        effortLevel(FUZZY_LEVELS, DEFAULTS.fuzzyThreshold),
                        BetterSearchConfigScreen::effortLabel);
                addSlider("cross_field_threshold", 0, EFFORT_STEPS, 1,
                        () -> effortLevel(CROSS_FIELD_LEVELS, settings.crossFieldThreshold),
                        v -> settings.crossFieldThreshold = CROSS_FIELD_LEVELS[Mth.clamp(v, 0, EFFORT_STEPS)],
                        effortLevel(CROSS_FIELD_LEVELS, DEFAULTS.crossFieldThreshold),
                        BetterSearchConfigScreen::effortLabel);
            }
            case LANGUAGES -> {
                addToggle("cross_language", () -> settings.crossLanguage,
                        v -> settings.crossLanguage = v, DEFAULTS.crossLanguage)
                        .preview(previewOf("cross_language"));
                addToggle("foreign_strict", () -> settings.foreignStrictOnly,
                        v -> settings.foreignStrictOnly = v, DEFAULTS.foreignStrictOnly)
                        .preview(previewOf("foreign_strict"));
                addToggle("cross_field", () -> settings.crossFieldMatching,
                        v -> settings.crossFieldMatching = v, DEFAULTS.crossFieldMatching)
                        .preview(previewOf("cross_field"));
                addAction("enabled_languages", enabledLanguagesLabel(),
                        () -> this.minecraft.setScreen(new LanguageSelectScreen(this, settings)));
            }
            case ADVANCED -> {
                addToggle("search_creative", () -> settings.searchCreative,
                        v -> settings.searchCreative = v, DEFAULTS.searchCreative)
                        .preview(previewOf("search_creative"));
                addToggle("search_recipe_book", () -> settings.searchRecipeBook,
                        v -> settings.searchRecipeBook = v, DEFAULTS.searchRecipeBook)
                        .preview(previewOf("search_recipe_book"));
                addToggle("fix_commands", () -> settings.fixCommandErrors,
                        v -> settings.fixCommandErrors = v, DEFAULTS.fixCommandErrors)
                        .preview(previewOf("fix_commands"));
                addToggle("search_player_names", () -> settings.searchPlayerNames,
                        v -> settings.searchPlayerNames = v, DEFAULTS.searchPlayerNames)
                        .preview(previewOf("search_player_names"));
                addToggle("search_command_items", () -> settings.searchCommandItems,
                        v -> settings.searchCommandItems = v, DEFAULTS.searchCommandItems)
                        .preview(previewOf("search_command_items"));
                addToggle("search_jei", () -> settings.searchJei,
                        v -> settings.searchJei = v, DEFAULTS.searchJei);
                addToggle("search_emi", () -> settings.searchEmi,
                        v -> settings.searchEmi = v, DEFAULTS.searchEmi);
                addToggle("search_rei", () -> settings.searchRei,
                        v -> settings.searchRei = v, DEFAULTS.searchRei);
                addSlider("command_suggestion_limit", 1, SUGGESTION_LIMIT_MAX, 1,
                        () -> settings.commandSuggestionLimit,
                        v -> settings.commandSuggestionLimit = v, DEFAULTS.commandSuggestionLimit,
                        BetterSearchConfigScreen::suggestionLimitLabel);
            }
        }
    }

    private Component enabledLanguagesLabel() {
        int total = LanguageCatalog.available().size();
        int enabled = settings.indexesAllLanguages() ? total : settings.languages.size();
        return Component.translatable("bettersearch.config.value.ratio", enabled, total);
    }

    @Override
    protected void buildPanelFooter() {
        int x = panelX() + 6;
        int width = panelWidth() - 12;
        int y = panelFooterTop() + 4;

        buildCornerLinks();

        int half = (width - 2) / 2;
        defaultsButton = addFixed(Button.builder(Component.translatable("bettersearch.config.restore_defaults"),
                        b -> {
                            copyInto(DEFAULTS, settings);
                            rebuildWidgets();
                        })
                .bounds(x, y, half, BUTTON_HEIGHT).build());

        undoButton = addFixed(Button.builder(Component.translatable("bettersearch.config.undo"),
                        b -> {
                            copyInto(opened, settings);
                            rebuildWidgets();
                        })
                .bounds(x + half + 2, y, width - half - 2, BUTTON_HEIGHT).build());

        addFixed(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(x, y + BUTTON_GAP, width, BUTTON_HEIGHT).build());
        updateFooterState();
    }

    private void buildCornerLinks() {
        Component youtube = Component.translatable("bettersearch.config.youtube");
        Component kofi = Component.translatable("bettersearch.config.kofi");
        int y = this.height - MARGIN - BUTTON_HEIGHT;
        int available = listWidth();
        int youtubeWidth = Math.min(FlatButton.widthFor(youtube), (available - 4) / 2);
        int kofiWidth = Math.min(FlatButton.widthFor(kofi), available - 4 - youtubeWidth);

        addFixed(new FlatButton(listX(), y, youtubeWidth, BUTTON_HEIGHT, youtube,
                () -> openLink(YOUTUBE_URL)));
        addFixed(new FlatButton(listX() + youtubeWidth + 4, y, kofiWidth, BUTTON_HEIGHT, kofi,
                () -> openLink(KOFI_URL)));
    }

    @Override
    protected void updateFooterState() {
        if (defaultsButton != null) {
            defaultsButton.active = !settings.equals(DEFAULTS);
        }
        if (undoButton != null) {
            undoButton.active = !settings.equals(opened);
        }
    }

    private static void copyInto(SearchSettings source, SearchSettings target) {
        SearchSettings copy = source.copy();
        target.enabled = copy.enabled;
        target.searchCreative = copy.searchCreative;
        target.searchRecipeBook = copy.searchRecipeBook;
        target.searchPlayerNames = copy.searchPlayerNames;
        target.searchCommandItems = copy.searchCommandItems;
        target.fixCommandErrors = copy.fixCommandErrors;
        target.commandSuggestionLimit = copy.commandSuggestionLimit;
        target.typoTolerance = copy.typoTolerance;
        target.minTypoLength = copy.minTypoLength;
        target.matchInitials = copy.matchInitials;
        target.ignoreSpaces = copy.ignoreSpaces;
        target.crossLanguage = copy.crossLanguage;
        target.languages = copy.languages;
        target.foreignStrictOnly = copy.foreignStrictOnly;
        target.sortByRelevance = copy.sortByRelevance;
        target.searchTooltips = copy.searchTooltips;
        target.searchItemIds = copy.searchItemIds;
        target.searchModIds = copy.searchModIds;
        target.fuzzyThreshold = copy.fuzzyThreshold;
        target.crossFieldMatching = copy.crossFieldMatching;
        target.crossFieldThreshold = copy.crossFieldThreshold;
        target.maxResults = copy.maxResults;
    }

    @Override
    protected Component panelDefaultTitle() {
        return Component.translatable("bettersearch.config.title");
    }

    @Override
    protected Component panelDefaultDescription() {
        return Component.translatable("bettersearch.config.tagline");
    }

    private void openLink(String url) {
        this.minecraft.setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                net.minecraft.Util.getPlatform().openUri(url);
            }
            this.minecraft.setScreen(this);
        }, url, true));
    }

    @Override
    public void onClose() {
        // saved on close, not on every click
        BetterSearchClient.applyAndSave(settings);
        super.onClose();
    }

    private static Component typoToleranceLabel(int value) {
        return switch (value) {
            case 0 -> Component.translatable("bettersearch.config.value.off");
            case 1 -> Component.translatable("bettersearch.config.value.low");
            case 2 -> Component.translatable("bettersearch.config.value.normal");
            default -> Component.translatable("bettersearch.config.value.high");
        };
    }

    private static final int[] FUZZY_LEVELS = {0, 20, 60, 150, 100_000};
    private static final int[] CROSS_FIELD_LEVELS = {0, 8, 20, 60, 100_000};
    private static final int EFFORT_STEPS = FUZZY_LEVELS.length - 1;

    private static int effortLevel(int[] levels, int value) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < levels.length; i++) {
            int distance = Math.abs(levels[i] - value);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static Component effortLabel(int level) {
        return Component.translatable(switch (level) {
            case 0 -> "bettersearch.config.value.never";
            case 1 -> "bettersearch.config.value.rarely";
            case 2 -> "bettersearch.config.value.balanced";
            case 3 -> "bettersearch.config.value.often";
            default -> "bettersearch.config.value.always";
        });
    }

    private static Component resultCountLabel(int value) {
        return value == 0
                ? Component.translatable("bettersearch.config.value.unlimited")
                : Component.literal(Integer.toString(value));
    }

    private static final int SUGGESTION_LIMIT_MAX = 30;

    private static Component suggestionLimitLabel(int value) {
        return value >= SUGGESTION_LIMIT_MAX
                ? Component.translatable("bettersearch.config.value.too_many")
                : Component.literal(Integer.toString(value));
    }
}
