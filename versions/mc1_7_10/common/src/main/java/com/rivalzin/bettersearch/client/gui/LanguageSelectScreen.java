package com.rivalzin.bettersearch.client.gui;

import com.rivalzin.bettersearch.client.LanguageCatalog;
import com.rivalzin.bettersearch.core.SearchSettings;
import com.rivalzin.bettersearch.core.TextNormalizer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// the list is what the resource packs actually ship, not a hardcoded table
public final class LanguageSelectScreen extends OptionRowsScreen {
    private final SearchSettings settings;
    private final List<LanguageCatalog.Entry> languages;
    private final String currentCode;

    private GuiTextField searchBox;
    private String filter = "";
    private int shownCount;

    public LanguageSelectScreen(GuiScreen parent, SearchSettings settings) {
        super(ComponentCompat.translatable("bettersearch.config.languages.title"), parent);
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
            final String code = language.code();
            boolean isCurrent = code.equalsIgnoreCase(currentCode);
            String title = ComponentCompat.literal(language.displayName());
            String description = isCurrent
                    ? ComponentCompat.translatable("bettersearch.config.languages.entry.current", code)
                    : ComponentCompat.translatable("bettersearch.config.languages.entry", code);
            addSwitchRow(title, description, () -> isEnabled(code), value -> setEnabled(code, value));
        }
    }

    private boolean matchesFilter(LanguageCatalog.Entry language) {
        if (filter.isEmpty()) {
            return true;
        }
        return TextNormalizer.normalize(language.displayName()).contains(filter)
                || language.code().toLowerCase().contains(filter);
    }

    @Override
    protected void buildPanelFooter() {
        int x = panelX() + 6;
        int width = panelWidth() - 12;
        int y = panelFooterTop() + 4;

        if (searchBox == null) {
            searchBox = new GuiTextField(this.fontRendererObj, x, y, width, BUTTON_HEIGHT);
            searchBox.setMaxStringLength(32);
            searchBox.setFocused(true);
        } else {
            searchBox.xPosition = x;
            searchBox.yPosition = y;
            searchBox.width = width;
        }

        int third = (width - 4) / 3;
        addFixed(ButtonCompat.builder(ComponentCompat.translatable("bettersearch.config.select_all"), b -> {
            settings.languages = allCodes();
            rebuildWidgets();
        }).bounds(x, y + BUTTON_GAP, third, BUTTON_HEIGHT).build());

        addFixed(ButtonCompat.builder(ComponentCompat.translatable("bettersearch.config.select_none"), b -> {
            settings.languages = new ArrayList<String>();
            rebuildWidgets();
        }).bounds(x + third + 2, y + BUTTON_GAP, third, BUTTON_HEIGHT).build());

        addFixed(ButtonCompat.builder(ComponentCompat.translatable("bettersearch.config.select_default"), b -> {
            settings.languages = new ArrayList<String>(SearchSettings.DEFAULT_LANGUAGES);
            rebuildWidgets();
        }).bounds(x + 2 * (third + 2), y + BUTTON_GAP, width - 2 * (third + 2), BUTTON_HEIGHT).build());

        addFixed(ButtonCompat.builder(ComponentCompat.translatable("gui.done"), b -> onClose())
                .bounds(x, y + 2 * BUTTON_GAP, width, BUTTON_HEIGHT).build());
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (searchBox != null && searchBox.textboxKeyTyped(typedChar, keyCode)) {
            String normalized = TextNormalizer.normalize(searchBox.getText());
            if (!normalized.equals(filter)) {
                filter = normalized;
                resetScroll();

                rebuildWidgets();
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (searchBox != null) {
            searchBox.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (searchBox != null) {
            searchBox.updateCursorCounter();
        }
    }

    @Override
    protected void drawExtraWidgets(int mouseX, int mouseY, float partialTicks) {
        if (searchBox == null) {
            return;
        }
        searchBox.drawTextBox();

        if (searchBox.getText().isEmpty()) {
            this.fontRendererObj.drawString(
                    ComponentCompat.translatable("bettersearch.config.languages.search"),
                    searchBox.xPosition + 4, searchBox.yPosition + (BUTTON_HEIGHT - 8) / 2, 0xFF808080);
        }
    }

    @Override
    protected String panelDefaultTitle() {
        return ComponentCompat.translatable("bettersearch.config.languages.title");
    }

    @Override
    protected String panelDefaultDescription() {
        if (shownCount == 0) {
            return ComponentCompat.translatable("bettersearch.config.languages.none_found");
        }
        return ComponentCompat.translatable("bettersearch.config.languages.summary",
                enabledCount(), languages.size(), currentCode);
    }

    private boolean isEnabled(String code) {
        return settings.indexesAllLanguages() || LanguageCatalog.contains(settings.languages, code);
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
            if (!LanguageCatalog.contains(settings.languages, code)) {
                settings.languages.add(code);
            }
        } else {
            Iterator<String> it = settings.languages.iterator();
            while (it.hasNext()) {
                if (it.next().equalsIgnoreCase(code)) {
                    it.remove();
                }
            }
        }
    }

    private List<String> allCodes() {
        List<String> all = new ArrayList<String>(languages.size());
        for (LanguageCatalog.Entry language : languages) {
            all.add(language.code());
        }
        return all;
    }
}
