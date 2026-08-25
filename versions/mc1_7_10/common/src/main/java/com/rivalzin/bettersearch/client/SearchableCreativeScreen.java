package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.Slot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

public final class SearchableCreativeScreen extends GuiContainerCreative {
    private static Field searchBoxField;
    private static Field scrollField;
    private static Field itemListField;
    private static Method scrollToMethod;
    private static boolean failed;

    private boolean clearOnNextKey;

    public SearchableCreativeScreen(EntityPlayer player) {
        super(player);
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotId, int button, int type) {
        super.handleMouseClick(slot, slotId, button, type);
        this.clearOnNextKey = true;
    }

    @Override
    protected void keyTyped(char chr, int keyCode) {
        CreativeTabs tab = currentTab();
        if (failed || tab == null || !tab.hasSearchBar()) {
            super.keyTyped(chr, keyCode);
            return;
        }
        GuiTextField box = searchBox();
        if (box == null) {
            super.keyTyped(chr, keyCode);
            return;
        }
        if (this.clearOnNextKey) {
            this.clearOnNextKey = false;
            box.setText("");
        }
        if (!this.checkHotbarKeys(keyCode)) {
            if (box.textboxKeyTyped(chr, keyCode)) {
                search(box.getText());
            } else {
                super.keyTyped(chr, keyCode);
            }
        }
    }

    private void search(String text) {
        try {
            List<ItemStack> ours = currentTab() == CreativeTabs.tabAllSearch
                    ? CreativeSearch.search(text) : null;
            Container container = this.inventorySlots;
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) itemListField().get(container);
            list.clear();
            if (ours != null) {
                list.addAll(ours);
            } else {
                fillLikeVanilla(list, text);
            }
            scrollField().setFloat(this, 0.0F);
            scrollToMethod().invoke(container, Float.valueOf(0.0F));
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void fillLikeVanilla(List<Object> list, String text) {
        CreativeTabs tab = currentTab();
        if (tab != null && tab.hasSearchBar() && tab != CreativeTabs.tabAllSearch) {
            tab.displayAllReleventItems(list);
        } else {
            Iterator<?> it = Item.itemRegistry.iterator();
            while (it.hasNext()) {
                Item item = (Item) it.next();
                if (item != null && item.getCreativeTab() != null) {
                    item.getSubItems(item, (CreativeTabs) null, list);
                }
            }
            for (Enchantment enchantment : Enchantment.enchantmentsList) {
                if (enchantment != null && enchantment.type != null) {
                    Items.enchanted_book.func_92113_a(enchantment, list);
                }
            }
        }

        String needle = text.toLowerCase();
        Iterator<Object> it = list.iterator();
        while (it.hasNext()) {
            ItemStack stack = (ItemStack) it.next();
            boolean found = false;
            for (Object line : stack.getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips)) {
                if (String.valueOf(line).toLowerCase().contains(needle)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                it.remove();
            }
        }
    }

    private CreativeTabs currentTab() {
        int index = this.func_147056_g();
        CreativeTabs[] tabs = CreativeTabs.creativeTabArray;
        return index >= 0 && index < tabs.length ? tabs[index] : null;
    }

    private GuiTextField searchBox() {
        try {
            if (searchBoxField == null) {
                // the box is a private field and 1.7.10 has no accessor for it
                searchBoxField = Reflect.field(GuiContainerCreative.class, "searchField", "field_147062_A");
            }
            return (GuiTextField) searchBoxField.get(this);
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    private Field scrollField() {
        if (scrollField == null) {
            scrollField = Reflect.field(GuiContainerCreative.class, "currentScroll", "field_147067_x");
        }
        return scrollField;
    }

    private Field itemListField() {
        if (itemListField == null) {
            itemListField = Reflect.field(this.inventorySlots.getClass(), "itemList", "field_148330_a");
        }
        return itemListField;
    }

    private Method scrollToMethod() {
        if (scrollToMethod == null) {
            scrollToMethod = Reflect.method(this.inventorySlots.getClass(),
                    new String[]{"scrollTo", "func_148329_a"}, float.class);
        }
        return scrollToMethod;
    }

    private static void fail(Throwable t) {
        if (!failed) {
            failed = true;
            BetterSearch.LOGGER.warn("[{}] unexpected creative screen layout, "
                    + "keeping vanilla search: {}", BetterSearch.MOD_NAME, t.toString());
        }
    }
}
