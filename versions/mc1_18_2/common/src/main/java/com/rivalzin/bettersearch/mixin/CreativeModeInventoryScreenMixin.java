package com.rivalzin.bettersearch.mixin;

import com.rivalzin.bettersearch.client.BetterSearchClient;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
    @Shadow
    private EditBox searchBox;

    @Shadow
    private static int selectedTab;

    @Unique
    private static List<ItemStack> bettersearch$pool;

    @Unique
    private static int bettersearch$poolRegistrySize = -1;

    // the descriptor is pinned: vanilla renamed this method twice already
    @Inject(method = "refreshSearchResults", at = @At("RETURN"))
    private void bettersearch$refreshSearchResults(CallbackInfo ci) {
        if (!BetterSearchClient.isEnabled()) {
            return;
        }
        EditBox box = this.searchBox;
        if (box == null || !box.isVisible()) {
            return;
        }

        if (selectedTab < 0 || selectedTab >= CreativeModeTab.TABS.length
                || CreativeModeTab.TABS[selectedTab] != CreativeModeTab.TAB_SEARCH) {
            return;
        }

        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        CreativeModeInventoryScreen.ItemPickerMenu menu = screen.getMenu();
        String query = box.getValue();

        if (query.isEmpty()) {
            bettersearch$remember(new ArrayList<>(menu.items));
            BetterSearchClient.prepare(bettersearch$pool);
            return;
        }
        if (query.charAt(0) == '#') {
            BetterSearchClient.prepare(bettersearch$pool());
            return;
        }

        List<ItemStack> results = BetterSearchClient.search(query, bettersearch$pool());
        if (results == null) {
            return;
        }

        menu.items.clear();
        menu.items.addAll(results);
        menu.scrollTo(0.0F);
    }

    @Unique
    @SuppressWarnings("deprecation")
    private static void bettersearch$remember(List<ItemStack> pool) {
        bettersearch$pool = pool;
        bettersearch$poolRegistrySize = Registry.ITEM.size();
    }

    @Unique
    @SuppressWarnings("deprecation")
    private static List<ItemStack> bettersearch$pool() {
        List<ItemStack> cached = bettersearch$pool;
        if (cached != null && bettersearch$poolRegistrySize == Registry.ITEM.size()) {
            return cached;
        }
        NonNullList<ItemStack> built = NonNullList.create();
        for (Item item : Registry.ITEM) {
            item.fillItemCategory(CreativeModeTab.TAB_SEARCH, built);
        }
        bettersearch$remember(built);
        return built;
    }
}
