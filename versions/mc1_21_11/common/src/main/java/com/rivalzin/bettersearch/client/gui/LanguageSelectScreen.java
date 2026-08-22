package com.rivalzin.bettersearch.client.gui;

import com.rivalzin.bettersearch.client.LanguageCatalog;
import com.rivalzin.bettersearch.core.SearchSettings;
import com.rivalzin.bettersearch.core.TextNormalizer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// the list is what the resource packs actually ship, not a hardcoded table
public final class LanguageSelectScreen extends OptionRowsScreen {
    private final SearchSettings settings;
    private final List<LanguageCatalog.Entry> languages;
    private final String currentCode;

    private EditBox searchBox;
    private String filter = "";
    private boolean needsRebuild;
    private int shownCount;

    public LanguageSelectScreen(Screen parent, SearchSettings settings) {
        super(Component.translatable("bettersearch.config.languages.title"), parent);
        this.settings = settings;
        this.languages = LanguageCatalog.available();
        this.currentCode = LanguageCatalog.currentCode();
    }

    @Override
    protected int panelFooterHeight() {
        return 3 * BUTTON_GAP + 4;
    }

    @Override
    protected void buildRows() {
        shownCount = 0;
        for (LanguageCatalog.Entry language : languages) {
            if (!matchesFilter(language)) {
                continue;
            }
            shownCount++;
            String code = language.code();
            boolean isCurrent = code.equals(currentCode);
            Component title = Component.literal(language.displayName());
            Component description = isCurrent
                    ? Component.translatable("bettersearch.config.languages.entry.current", code)
                    : Component.translatable("bettersearch.config.languages.entry", code);
            addSwitchRow(title, description, () -> isEnabled(code), value -> setEnabled(code, value));
        }
    }

    private boolean matchesFilter(LanguageCatalog.Entry language) {
        if (filter.isEmpty()) {
            return true;
        }
        return TextNormalizer.normalize(language.displayName()).contains(filter)
                || language.code().contains(filter);
    }

    @Override
    protected void buildPanelFooter() {
        int x = panelX() + 6;
        int width = panelWidth() - 12;
        int y = panelFooterTop() + 4;

        if (searchBox == null) {
            searchBox = new EditBox(this.font, x, y, width, BUTTON_HEIGHT,
                    Component.translatable("bettersearch.config.languages.search"));
            searchBox.setMaxLength(32);
            searchBox.setHint(Component.translatable("bettersearch.config.languages.search"));
            searchBox.setResponder(value -> {
                String normalized = TextNormalizer.normalize(value);
                if (!normalized.equals(filter)) {
                    filter = normalized;
                    resetScroll();
                    needsRebuild = true;
                }
            });
        } else {
            searchBox.setX(x);
            searchBox.setY(y);
            searchBox.setWidth(width);
        }
        addFixed(searchBox);
        setInitialFocus(searchBox);

        int third = (width - 4) / 3;
        addFixed(Button.builder(Component.translatable("bettersearch.config.select_all"), b -> {
            settings.languages = allCodes();
            rebuildWidgets();
        }).bounds(x, y + BUTTON_GAP, third, BUTTON_HEIGHT).build());

        addFixed(Button.builder(Component.translatable("bettersearch.config.select_none"), b -> {
            settings.languages = new ArrayList<>();
            rebuildWidgets();
        }).bounds(x + third + 2, y + BUTTON_GAP, third, BUTTON_HEIGHT).build());

        addFixed(Button.builder(Component.translatable("bettersearch.config.select_default"), b -> {
            settings.languages = new ArrayList<>(SearchSettings.DEFAULT_LANGUAGES);
            rebuildWidgets();
        }).bounds(x + 2 * (third + 2), y + BUTTON_GAP, width - 2 * (third + 2), BUTTON_HEIGHT).build());

        addFixed(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(x, y + 2 * BUTTON_GAP, width, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (needsRebuild) {
            needsRebuild = false;
            rebuildWidgets();
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected Component panelDefaultTitle() {
        return Component.translatable("bettersearch.config.languages.title");
    }

    @Override
    protected Component panelDefaultDescription() {
        if (shownCount == 0) {
            return Component.translatable("bettersearch.config.languages.none_found");
        }
        return Component.translatable("bettersearch.config.languages.summary",
                enabledCount(), languages.size(), currentCode);
    }

    private boolean isEnabled(String code) {
        return settings.indexesAllLanguages() || settings.languages.contains(code);
    }

    private int enabledCount() {
        int count = 0;
        for (LanguageCatalog.Entry language : languages) {
            if (isEnabled(language.code())) {
                count++;
            }
        }
        return count;
    }

    private void setEnabled(String code, boolean enabled) {
        if (settings.indexesAllLanguages()) {
            settings.languages = allCodes();
        }
        if (enabled) {
            if (!settings.languages.contains(code)) {
                settings.languages.add(code);
            }
        } else {
            settings.languages.remove(code);
        }
    }

    private List<String> allCodes() {
        List<String> all = new ArrayList<>(languages.size());
        for (LanguageCatalog.Entry language : languages) {
            all.add(language.code());
        }
        return all;
    }
}
