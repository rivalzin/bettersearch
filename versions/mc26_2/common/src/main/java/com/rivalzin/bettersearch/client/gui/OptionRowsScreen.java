package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

public abstract class OptionRowsScreen extends Screen {
    protected static final String KEY_PREFIX = "bettersearch.config.";

    protected static final int ROW_HEIGHT = 24;
    protected static final int CONTROL_HEIGHT = 20;
    protected static final int SLIDER_WIDTH_MAX = 96;
    protected static final int RESET_SIZE = 20;
    protected static final int MARGIN = 6;
    protected static final int BUTTON_HEIGHT = 20;
    protected static final int BUTTON_GAP = 22;

    protected static final int PREVIEW_WIDTH = 200;
    protected static final int PREVIEW_HEIGHT = 104;

    private static final int PREVIEW_PADDING = 3;

    private static final float PREVIEW_MIN_SCALE = 0.45F;

    protected static final class Row {
        final Component title;
        final Component description;
        final AbstractWidget control;
        final Button reset;
        final BooleanSupplier modified;
        Identifier preview;
        int y;

        Row(Component title, Component description, AbstractWidget control,
            Button reset, BooleanSupplier modified) {
            this.title = title;
            this.description = description;
            this.control = control;
            this.reset = reset;
            this.modified = modified;
        }

        public Row preview(Identifier texture) {
            this.preview = texture;
            return this;
        }
    }

    protected final Screen parent;

    private final List<Row> rows = new ArrayList<>();
    private int scrollRow;
    private int visibleRows = 1;

    private int contentTop;
    private int contentBottom;
    private int listBottom;
    private int listX;
    private int listWidth;
    private int barWidth;
    private int panelX;
    private int panelWidth;
    private int sliderWidth = SLIDER_WIDTH_MAX;
    private float previewScale;
    private int previewTop;
    private Row hoveredRow;

    protected OptionRowsScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected abstract int panelFooterHeight();

    protected int listBottomInset() {
        return 0;
    }

    protected void buildTabs() {
    }

    protected int tabsHeight() {
        return 0;
    }

    protected abstract void buildRows();

    // footer is pinned to the bottom, the list scrolls under it
    protected abstract void buildPanelFooter();

    protected abstract Component panelDefaultTitle();

    protected abstract Component panelDefaultDescription();

    @Override
    protected void init() {
        rows.clear();
        hoveredRow = null;

        int tabs = tabsHeight();
        contentTop = MARGIN + (tabs > 0 ? tabs + 4 : 0);
        contentBottom = this.height - MARGIN;

        panelWidth = Mth.clamp((this.width - 3 * MARGIN) * 36 / 100, 110, 224);
        panelX = this.width - MARGIN - panelWidth;
        listX = MARGIN;
        listWidth = panelX - MARGIN - listX;
        barWidth = listWidth - RESET_SIZE - 4;
        sliderWidth = Mth.clamp(barWidth * 45 / 100, 60, SLIDER_WIDTH_MAX);
        listBottom = contentBottom - listBottomInset();
        visibleRows = Math.max(1, (listBottom - contentTop) / ROW_HEIGHT);

        buildTabs();
        buildRows();
        buildPanelFooter();
        layoutRows();
        measurePreview();
    }

    private void measurePreview() {
        previewScale = 0.0F;
        previewTop = 0;
        int textWidth = panelWidth - 16;
        int textNeeded = 0;
        for (Row row : rows) {
            if (row.preview == null) {
                continue;
            }
            int lines = this.font.split(row.title, textWidth).size()
                    + this.font.split(row.description, textWidth).size();
            textNeeded = Math.max(textNeeded, 8 + 10 * lines + 4);
        }
        if (textNeeded == 0) {
            return;
        }

        int top = contentTop + textNeeded + 4;
        int maxWidth = panelWidth - 16 - 2 * PREVIEW_PADDING;
        int maxHeight = (panelFooterTop() - 6) - top - 2 * PREVIEW_PADDING;
        float scale = Math.min(1.0F, Math.min(maxWidth / (float) PREVIEW_WIDTH,
                maxHeight / (float) PREVIEW_HEIGHT));
        if (scale >= PREVIEW_MIN_SCALE) {
            previewScale = scale;
            previewTop = top;
        }
    }

    protected final int listX() {
        return listX;
    }

    protected final int listWidth() {
        return listWidth;
    }

    protected final int panelX() {
        return panelX;
    }

    protected final int panelWidth() {
        return panelWidth;
    }

    protected final int contentTop() {
        return contentTop;
    }

    protected final int contentBottom() {
        return contentBottom;
    }

    protected final int listBottom() {
        return listBottom;
    }

    private int controlX(int width) {
        return listX + barWidth - 6 - width;
    }

    protected static Identifier previewOf(String key) {
        return Identifier.fromNamespaceAndPath("bettersearch", "textures/gui/options/" + key + ".png");
    }

    private static void attachTip(AbstractWidget control, String key) {
        control.setTooltip(Tooltip.create(Component.translatable(KEY_PREFIX + key + ".tip")));
    }

    protected final Row addToggle(String key, BooleanSupplier getter, Consumer<Boolean> setter, boolean defaultValue) {
        Component title = Component.translatable(KEY_PREFIX + key);
        ToggleSwitch control = new ToggleSwitch(controlX(ToggleSwitch.WIDTH), 0,
                getter.getAsBoolean(), title, setter);
        attachTip(control, key);
        return addRow(title, Component.translatable(KEY_PREFIX + key + ".desc"), control,
                () -> setter.accept(defaultValue),
                () -> getter.getAsBoolean() != defaultValue);
    }

    protected final Row addSlider(String key, int min, int max, int step,
                                  IntSupplier getter, IntConsumer setter, int defaultValue,
                                  IntFunction<Component> valueLabel) {
        Component title = Component.translatable(KEY_PREFIX + key);
        IntSlider control = new IntSlider(controlX(sliderWidth), 0, sliderWidth, CONTROL_HEIGHT,
                min, max, step, getter.getAsInt(), valueLabel, setter);
        attachTip(control, key);
        return addRow(title, Component.translatable(KEY_PREFIX + key + ".desc"), control,
                () -> setter.accept(defaultValue),
                () -> getter.getAsInt() != defaultValue);
    }

    protected final Row addAction(String key, Component buttonLabel, Runnable action) {
        Component title = Component.translatable(KEY_PREFIX + key);
        Button control = Button.builder(buttonLabel, b -> action.run())
                .bounds(controlX(sliderWidth), 0, sliderWidth, CONTROL_HEIGHT)
                .build();
        attachTip(control, key);
        return addRow(title, Component.translatable(KEY_PREFIX + key + ".desc"), control, null, null);
    }

    protected final Row addSwitchRow(Component title, Component description,
                                     BooleanSupplier getter, Consumer<Boolean> setter) {
        ToggleSwitch control = new ToggleSwitch(controlX(ToggleSwitch.WIDTH), 0,
                getter.getAsBoolean(), title, setter);
        return addRow(title, description, control, null, null);
    }

    private Row addRow(Component title, Component description, AbstractWidget control,
                       Runnable onReset, BooleanSupplier modified) {
        Button reset = null;
        if (onReset != null) {
            reset = Button.builder(Component.literal("↺"), b -> {
                onReset.run();
                rebuildWidgets();
            }).bounds(listX + barWidth + 4, 0, RESET_SIZE, RESET_SIZE)
                    .tooltip(Tooltip.create(Component.translatable(KEY_PREFIX + "reset_option")))
                    .build();
        }
        Row row = new Row(title, description, control, reset, modified);
        rows.add(row);
        addRenderableWidget(control);
        if (reset != null) {
            addRenderableWidget(reset);
        }
        return row;
    }

    protected final <T extends AbstractWidget> T addFixed(T widget) {
        return addRenderableWidget(widget);
    }

    protected final int panelFooterTop() {
        return contentBottom - panelFooterHeight();
    }

    protected final void resetScroll() {
        scrollRow = 0;
    }

    private void layoutRows() {
        int maxScroll = Math.max(0, rows.size() - visibleRows);
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int slot = i - scrollRow;
            boolean shown = slot >= 0 && slot < visibleRows;
            row.y = contentTop + slot * ROW_HEIGHT;
            row.control.visible = shown;
            row.control.setY(row.y + (ROW_HEIGHT - row.control.getHeight()) / 2);
            if (row.reset != null) {
                row.reset.visible = shown;
                row.reset.setY(row.y + (ROW_HEIGHT - RESET_SIZE) / 2);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean overList = mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= contentTop && mouseY <= listBottom;
        if (overList && rows.size() > visibleRows && scrollY != 0.0) {
            scrollRow -= (int) Math.signum(scrollY);
            layoutRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static final int SCROLLBAR_WIDTH = 2;
    private static final int SCROLLBAR_WIDTH_HOVER = 3;
    private static final int SCROLLBAR_WIDTH_HELD = 4;

    private static final int SCROLLBAR_GRAB = 3;

    private boolean scrollbarHeld;

    private int scrollbarGrabOffset;

    private int scrollbarRight() {
        return listX + listWidth;
    }

    private int scrollbarTop() {
        return contentTop;
    }

    private int scrollbarBottom() {
        return Math.min(listBottom, contentTop + visibleRows * ROW_HEIGHT - 2);
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - visibleRows);
    }

    private int thumbHeight() {
        int track = scrollbarBottom() - scrollbarTop();
        return Math.max(16, track * visibleRows / Math.max(1, rows.size()));
    }

    private int thumbTop() {
        int max = maxScroll();
        if (max <= 0) {
            return scrollbarTop();
        }
        int usable = Math.max(0, (scrollbarBottom() - scrollbarTop()) - thumbHeight());
        return scrollbarTop() + usable * scrollRow / max;
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        int right = scrollbarRight();
        return maxScroll() > 0
                && mouseX >= right - SCROLLBAR_WIDTH_HELD - SCROLLBAR_GRAB
                && mouseX <= right + SCROLLBAR_GRAB
                && mouseY >= scrollbarTop() && mouseY <= scrollbarBottom();
    }

    private boolean beginScrollbarDrag(double mouseX, double mouseY, int button) {
        if (button != 0 || !overScrollbar(mouseX, mouseY)) {
            return false;
        }
        int thumb = thumbHeight();
        int top = thumbTop();

        scrollbarGrabOffset = (mouseY >= top && mouseY < top + thumb) ? (int) (mouseY - top) : thumb / 2;
        scrollbarHeld = true;
        updateScrollbarDrag(mouseY);
        return true;
    }

    private boolean updateScrollbarDrag(double mouseY) {
        if (!scrollbarHeld) {
            return false;
        }
        int max = maxScroll();
        int usable = (scrollbarBottom() - scrollbarTop()) - thumbHeight();
        if (max > 0 && usable > 0) {
            double travelled = mouseY - scrollbarGrabOffset - scrollbarTop();
            scrollRow = Mth.clamp((int) Math.round(travelled * max / usable), 0, max);
            layoutRows();
        }
        return true;
    }

    private void endScrollbarDrag() {
        scrollbarHeld = false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (beginScrollbarDrag(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (updateScrollbarDrag(event.y())) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        endScrollbarDrag();
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateHoveredRow(mouseX, mouseY);
        for (Row row : rows) {
            if (row.reset != null && row.modified != null) {
                row.reset.active = row.modified.getAsBoolean();
            }
        }
        updateFooterState();

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        renderScrollbar(guiGraphics, mouseX, mouseY);
    }

    protected void updateFooterState() {
    }

    // hover drives the preview, so it is tracked even when nothing is clicked
    private void updateHoveredRow(int mouseX, int mouseY) {
        if (mouseX < listX || mouseX > listX + listWidth) {
            return;
        }
        for (Row row : rows) {
            if (row.control.visible && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT) {
                hoveredRow = row;
                return;
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        renderRows(guiGraphics);
        renderPanel(guiGraphics);
    }

    private void renderRows(GuiGraphicsExtractor guiGraphics) {
        int labelLimit = barWidth - 14 - Math.max(ToggleSwitch.WIDTH, sliderWidth);
        for (Row row : rows) {
            if (!row.control.visible) {
                continue;
            }
            int top = row.y;
            int bottom = top + ROW_HEIGHT - 2;
            boolean hovered = row == hoveredRow;
            guiGraphics.fill(listX, top, listX + barWidth, bottom, hovered ? Theme.ROW_BG_HOVER : Theme.ROW_BG);
            if (hovered) {
                guiGraphics.fill(listX, top, listX + 2, bottom, Theme.ACCENT);
            }
            guiGraphics.text(this.font, ellipsize(row.title, labelLimit),
                    listX + 8, top + (ROW_HEIGHT - 2 - 8) / 2, hovered ? Theme.TITLE : Theme.TEXT);
        }
    }

    private void renderPanel(GuiGraphicsExtractor guiGraphics) {
        guiGraphics.fill(panelX, contentTop, panelX + panelWidth, contentBottom, Theme.PANEL_BG);
        guiGraphics.fill(panelX, contentTop, panelX + panelWidth, contentTop + 1, Theme.ACCENT);

        int textX = panelX + 8;
        int textWidth = panelWidth - 16;
        int y = contentTop + 8;

        Component title = hoveredRow != null ? hoveredRow.title : panelDefaultTitle();
        Component description = hoveredRow != null ? hoveredRow.description : panelDefaultDescription();
        Identifier preview = hoveredRow != null ? hoveredRow.preview : null;

        int textLimit = panelFooterTop() - 6;
        if (preview != null) {
            int frameTop = renderPreview(guiGraphics, preview);
            if (frameTop > 0) {
                textLimit = frameTop - 4;
            }
        }

        for (FormattedCharSequence line : this.font.split(title, textWidth)) {
            guiGraphics.text(this.font, line, textX, y, Theme.ACCENT);
            y += 10;
        }
        y += 4;
        for (FormattedCharSequence line : this.font.split(description, textWidth)) {
            if (y + 9 > textLimit) {
                break;
            }
            guiGraphics.text(this.font, line, textX, y, Theme.TEXT);
            y += 10;
        }
    }

    private int renderPreview(GuiGraphicsExtractor guiGraphics, Identifier texture) {
        float scale = previewScale;
        if (scale <= 0.0F) {
            return 0;
        }

        int drawWidth = Math.round(PREVIEW_WIDTH * scale);
        int drawHeight = Math.round(PREVIEW_HEIGHT * scale);
        int imageX = panelX + (panelWidth - drawWidth) / 2;
        int imageY = previewTop + PREVIEW_PADDING;

        int left = imageX - PREVIEW_PADDING;
        int top = previewTop;
        int right = imageX + drawWidth + PREVIEW_PADDING;
        int bottom = imageY + drawHeight + PREVIEW_PADDING;

        guiGraphics.fill(left, top, right, bottom, Theme.FRAME_BG);
        guiGraphics.fill(left, top, right, top + 1, Theme.FRAME_BORDER);
        guiGraphics.fill(left, bottom - 1, right, bottom, Theme.FRAME_BORDER);
        guiGraphics.fill(left, top, left + 1, bottom, Theme.FRAME_BORDER);
        guiGraphics.fill(right - 1, top, right, bottom, Theme.FRAME_BORDER);

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, imageX, imageY,
                0.0F, 0.0F, drawWidth, drawHeight,
                PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_WIDTH, PREVIEW_HEIGHT);
        return top;
    }

    // hand rolled: the vanilla scrollbar widget only exists from 1.20 on
    private void renderScrollbar(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        if (maxScroll() <= 0) {
            return;
        }
        int top = scrollbarTop();
        int bottom = scrollbarBottom();
        int right = scrollbarRight();
        boolean active = scrollbarHeld || overScrollbar(mouseX, mouseY);

        guiGraphics.fill(right - SCROLLBAR_WIDTH, top, right, bottom, Theme.SCROLL_TRACK);

        int width = scrollbarHeld ? SCROLLBAR_WIDTH_HELD
                : (active ? SCROLLBAR_WIDTH_HOVER : SCROLLBAR_WIDTH);
        int thumb = thumbHeight();
        int thumbY = thumbTop();
        guiGraphics.fill(right - width, thumbY, right, thumbY + thumb,
                active ? Theme.KNOB_HOVER : Theme.SCROLL_THUMB);
    }

    private String ellipsize(Component text, int maxWidth) {
        String plain = text.getString();
        int limit = Math.max(16, maxWidth);
        if (this.font.width(plain) <= limit) {
            return plain;
        }
        return this.font.plainSubstrByWidth(plain, limit - this.font.width("...")) + "...";
    }
}
