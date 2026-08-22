package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

public abstract class OptionRowsScreen extends GuiScreen {
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
        final String title;
        final String description;
        final GuiButton control;
        final GuiButton reset;
        final BooleanSupplier modified;
        ResourceLocation preview;
        int y;

        Row(String title, String description, GuiButton control,
            GuiButton reset, BooleanSupplier modified) {
            this.title = title;
            this.description = description;
            this.control = control;
            this.reset = reset;
            this.modified = modified;
        }

        public Row preview(ResourceLocation texture) {
            this.preview = texture;
            return this;
        }
    }

    protected final GuiScreen parent;

    protected final String screenTitle;

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
    private boolean rebuildQueued;

    protected OptionRowsScreen(String title, GuiScreen parent) {
        this.screenTitle = title;
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

    protected abstract String panelDefaultTitle();

    protected abstract String panelDefaultDescription();

    protected void rebuildWidgets() {
        rebuildQueued = true;
    }

    private void runQueuedRebuild() {
        if (rebuildQueued) {
            rebuildQueued = false;
            if (this.mc != null) {
                this.setWorldAndResolution(this.mc, this.width, this.height);
            }
        }
    }

    @Override
    public void initGui() {
        rows.clear();
        hoveredRow = null;

        int tabs = tabsHeight();
        contentTop = MARGIN + (tabs > 0 ? tabs + 4 : 0);
        contentBottom = this.height - MARGIN;

        panelWidth = MathHelper.clamp((this.width - 3 * MARGIN) * 36 / 100, 110, 224);
        panelX = this.width - MARGIN - panelWidth;
        listX = MARGIN;
        listWidth = panelX - MARGIN - listX;
        barWidth = listWidth - RESET_SIZE - 4;
        sliderWidth = MathHelper.clamp(barWidth * 45 / 100, 60, SLIDER_WIDTH_MAX);
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
            int lines = this.fontRenderer.listFormattedStringToWidth(row.title, textWidth).size()
                    + this.fontRenderer.listFormattedStringToWidth(row.description, textWidth).size();
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

    protected static ResourceLocation previewOf(String key) {
        return new ResourceLocation("bettersearch", "textures/gui/options/" + key + ".png");
    }

    private static void attachTip(GuiButton control, String key) {
        Tips.set(control, ComponentCompat.translatable(KEY_PREFIX + key + ".tip"));
    }

    protected final Row addToggle(String key, BooleanSupplier getter, Consumer<Boolean> setter, boolean defaultValue) {
        String title = ComponentCompat.translatable(KEY_PREFIX + key);
        ToggleSwitch control = new ToggleSwitch(controlX(ToggleSwitch.WIDTH), 0,
                getter.getAsBoolean(), title, setter);
        attachTip(control, key);
        return addRow(title, ComponentCompat.translatable(KEY_PREFIX + key + ".desc"), control,
                () -> setter.accept(defaultValue),
                () -> getter.getAsBoolean() != defaultValue);
    }

    protected final Row addSlider(String key, int min, int max, int step,
                                  IntSupplier getter, IntConsumer setter, int defaultValue,
                                  IntFunction<String> valueLabel) {
        String title = ComponentCompat.translatable(KEY_PREFIX + key);
        IntSlider control = new IntSlider(controlX(sliderWidth), 0, sliderWidth, CONTROL_HEIGHT,
                min, max, step, getter.getAsInt(), valueLabel, setter);
        attachTip(control, key);
        return addRow(title, ComponentCompat.translatable(KEY_PREFIX + key + ".desc"), control,
                () -> setter.accept(defaultValue),
                () -> getter.getAsInt() != defaultValue);
    }

    protected final Row addAction(String key, String buttonLabel, Runnable action) {
        String title = ComponentCompat.translatable(KEY_PREFIX + key);
        GuiButton control = ButtonCompat.builder(buttonLabel, b -> action.run())
                .bounds(controlX(sliderWidth), 0, sliderWidth, CONTROL_HEIGHT)
                .build();
        attachTip(control, key);
        return addRow(title, ComponentCompat.translatable(KEY_PREFIX + key + ".desc"), control, null, null);
    }

    protected final Row addSwitchRow(String title, String description,
                                     BooleanSupplier getter, Consumer<Boolean> setter) {
        ToggleSwitch control = new ToggleSwitch(controlX(ToggleSwitch.WIDTH), 0,
                getter.getAsBoolean(), title, setter);
        return addRow(title, description, control, null, null);
    }

    private Row addRow(String title, String description, GuiButton control,
                       Runnable onReset, BooleanSupplier modified) {
        GuiButton reset = null;
        if (onReset != null) {
            reset = ButtonCompat.builder(ComponentCompat.literal("↺"), b -> {
                onReset.run();
                rebuildWidgets();
            }).bounds(listX + barWidth + 4, 0, RESET_SIZE, RESET_SIZE)
                    .tooltip((ComponentCompat.translatable(KEY_PREFIX + "reset_option")))
                    .build();
        }
        Row row = new Row(title, description, control, reset, modified);
        rows.add(row);
        addButton(control);
        if (reset != null) {
            addButton(reset);
        }
        return row;
    }

    protected final <T extends GuiButton> T addFixed(T widget) {
        return addButton(widget);
    }

    protected final int panelFooterTop() {
        return contentBottom - panelFooterHeight();
    }

    protected final void resetScroll() {
        scrollRow = 0;
    }

    private void layoutRows() {
        int maxScroll = Math.max(0, rows.size() - visibleRows);
        scrollRow = MathHelper.clamp(scrollRow, 0, maxScroll);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int slot = i - scrollRow;
            boolean shown = slot >= 0 && slot < visibleRows;
            row.y = contentTop + slot * ROW_HEIGHT;
            row.control.visible = shown;
            row.control.y = row.y + (ROW_HEIGHT - row.control.height) / 2;
            if (row.reset != null) {
                row.reset.visible = shown;
                row.reset.y = row.y + (ROW_HEIGHT - RESET_SIZE) / 2;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button instanceof Pressable) {
            ((Pressable) button).onPress();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            onClose();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int delta = Mouse.getEventDWheel();
        if (delta != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            mouseScrolled(mouseX, mouseY, Integer.signum(delta));
        }
    }

    private void mouseScrolled(int mouseX, int mouseY, int direction) {
        boolean overList = mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= contentTop && mouseY <= listBottom;
        if (overList && rows.size() > visibleRows && direction != 0) {
            scrollRow -= direction;
            layoutRows();
        }
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

    private boolean overScrollbar(int mouseX, int mouseY) {
        int right = scrollbarRight();
        return maxScroll() > 0
                && mouseX >= right - SCROLLBAR_WIDTH_HELD - SCROLLBAR_GRAB
                && mouseX <= right + SCROLLBAR_GRAB
                && mouseY >= scrollbarTop() && mouseY <= scrollbarBottom();
    }

    private boolean beginScrollbarDrag(int mouseX, int mouseY, int button) {
        if (button != 0 || !overScrollbar(mouseX, mouseY)) {
            return false;
        }
        int thumb = thumbHeight();
        int top = thumbTop();

        scrollbarGrabOffset = (mouseY >= top && mouseY < top + thumb) ? (mouseY - top) : thumb / 2;
        scrollbarHeld = true;
        updateScrollbarDrag(mouseY);
        return true;
    }

    private boolean updateScrollbarDrag(int mouseY) {
        if (!scrollbarHeld) {
            return false;
        }
        int max = maxScroll();
        int usable = (scrollbarBottom() - scrollbarTop()) - thumbHeight();
        if (max > 0 && usable > 0) {
            double travelled = mouseY - scrollbarGrabOffset - scrollbarTop();
            scrollRow = MathHelper.clamp((int) Math.round(travelled * max / usable), 0, max);
            layoutRows();
        }
        return true;
    }

    private void endScrollbarDrag() {
        scrollbarHeld = false;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (beginScrollbarDrag(mouseX, mouseY, mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (updateScrollbarDrag(mouseY)) {
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        endScrollbarDrag();
        super.mouseReleased(mouseX, mouseY, state);
    }

    public void onClose() {
        this.mc.displayGuiScreen(this.parent);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        runQueuedRebuild();
        updateHoveredRow(mouseX, mouseY);
        for (Row row : rows) {
            if (row.reset != null && row.modified != null) {
                row.reset.enabled = row.modified.getAsBoolean();
            }
        }
        updateFooterState();

        drawDefaultBackground();
        renderRows();
        renderPanel();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawExtraWidgets(mouseX, mouseY, partialTicks);
        renderScrollbar(mouseX, mouseY);
        renderTip(mouseX, mouseY);
    }

    protected void drawExtraWidgets(int mouseX, int mouseY, float partialTicks) {
    }

    private void renderTip(int mouseX, int mouseY) {
        for (GuiButton widget : this.buttonList) {
            if (!widget.visible) {
                continue;
            }
            boolean over = mouseX >= widget.x && mouseY >= widget.y
                    && mouseX < widget.x + widget.width && mouseY < widget.y + widget.height;
            if (!over) {
                continue;
            }
            String tip = Tips.of(widget);
            if (tip != null) {
                drawHoveringText(this.fontRenderer.listFormattedStringToWidth(
                        tip, Math.max(this.width / 2, 170)), mouseX, mouseY);
            }
            return;
        }
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

    private void renderRows() {
        int labelLimit = barWidth - 14 - Math.max(ToggleSwitch.WIDTH, sliderWidth);
        for (Row row : rows) {
            if (!row.control.visible) {
                continue;
            }
            int top = row.y;
            int bottom = top + ROW_HEIGHT - 2;
            boolean hovered = row == hoveredRow;
            Gui.drawRect(listX, top, listX + barWidth, bottom, hovered ? Theme.ROW_BG_HOVER : Theme.ROW_BG);
            if (hovered) {
                Gui.drawRect(listX, top, listX + 2, bottom, Theme.ACCENT);
            }

            this.fontRenderer.drawStringWithShadow(ellipsize(row.title, labelLimit),
                    listX + 8, top + (ROW_HEIGHT - 2 - 8) / 2, hovered ? Theme.TITLE : Theme.TEXT);
        }
    }

    private void renderPanel() {
        Gui.drawRect(panelX, contentTop, panelX + panelWidth, contentBottom, Theme.PANEL_BG);
        Gui.drawRect(panelX, contentTop, panelX + panelWidth, contentTop + 1, Theme.ACCENT);

        int textX = panelX + 8;
        int textWidth = panelWidth - 16;
        int y = contentTop + 8;

        String title = hoveredRow != null ? hoveredRow.title : panelDefaultTitle();
        String description = hoveredRow != null ? hoveredRow.description : panelDefaultDescription();
        ResourceLocation preview = hoveredRow != null ? hoveredRow.preview : null;

        int textLimit = panelFooterTop() - 6;
        if (preview != null) {
            int frameTop = renderPreview(preview);
            if (frameTop > 0) {
                textLimit = frameTop - 4;
            }
        }

        for (String line : this.fontRenderer.listFormattedStringToWidth(title, textWidth)) {
            this.fontRenderer.drawString(line, textX, y, Theme.ACCENT);
            y += 10;
        }
        y += 4;
        for (String line : this.fontRenderer.listFormattedStringToWidth(description, textWidth)) {
            if (y + 9 > textLimit) {
                break;
            }
            this.fontRenderer.drawString(line, textX, y, Theme.TEXT);
            y += 10;
        }
    }

    private int renderPreview(ResourceLocation texture) {
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

        Gui.drawRect(left, top, right, bottom, Theme.FRAME_BG);
        Gui.drawRect(left, top, right, top + 1, Theme.FRAME_BORDER);
        Gui.drawRect(left, bottom - 1, right, bottom, Theme.FRAME_BORDER);
        Gui.drawRect(left, top, left + 1, bottom, Theme.FRAME_BORDER);
        Gui.drawRect(right - 1, top, right, bottom, Theme.FRAME_BORDER);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        this.mc.getTextureManager().bindTexture(texture);
        Gui.drawScaledCustomSizeModalRect(imageX, imageY, 0.0F, 0.0F, PREVIEW_WIDTH, PREVIEW_HEIGHT,
                drawWidth, drawHeight, PREVIEW_WIDTH, PREVIEW_HEIGHT);
        GlStateManager.disableBlend();
        return top;
    }

    // hand rolled: the vanilla scrollbar widget only exists from 1.20 on
    private void renderScrollbar(int mouseX, int mouseY) {
        if (maxScroll() <= 0) {
            return;
        }
        int top = scrollbarTop();
        int bottom = scrollbarBottom();
        int right = scrollbarRight();
        boolean active = scrollbarHeld || overScrollbar(mouseX, mouseY);

        Gui.drawRect(right - SCROLLBAR_WIDTH, top, right, bottom, Theme.SCROLL_TRACK);

        int width = scrollbarHeld ? SCROLLBAR_WIDTH_HELD
                : (active ? SCROLLBAR_WIDTH_HOVER : SCROLLBAR_WIDTH);
        int thumb = thumbHeight();
        int thumbY = thumbTop();
        Gui.drawRect(right - width, thumbY, right, thumbY + thumb,
                active ? Theme.KNOB_HOVER : Theme.SCROLL_THUMB);
    }

    private String ellipsize(String text, int maxWidth) {
        int limit = Math.max(16, maxWidth);
        if (this.fontRenderer.getStringWidth(text) <= limit) {
            return text;
        }
        return this.fontRenderer.trimStringToWidth(text, limit - this.fontRenderer.getStringWidth("...")) + "...";
    }
}
