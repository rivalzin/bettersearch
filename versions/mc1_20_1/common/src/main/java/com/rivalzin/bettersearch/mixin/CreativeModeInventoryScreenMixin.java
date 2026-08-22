package com.rivalzin.bettersearch.mixin;

import com.rivalzin.bettersearch.client.BetterSearchClient;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
    @Shadow
    private EditBox searchBox;

    @Shadow
    private static CreativeModeTab selectedTab;

    // the descriptor is pinned: vanilla renamed this method twice already
    @Inject(method = "refreshSearchResults", at = @At("RETURN"))
    private void bettersearch$refreshSearchResults(CallbackInfo ci) {
        if (!BetterSearchClient.isEnabled()) {
            return;
        }
        EditBox box = this.searchBox;
        CreativeModeTab tab = selectedTab;
        if (box == null || tab == null || !box.isVisible()) {
            return;
        }

        Collection<ItemStack> pool = tab.getDisplayItems();
        String query = box.getValue();

        if (query.isEmpty() || query.charAt(0) == '#') {
            BetterSearchClient.prepare(pool);
            return;
        }

        List<ItemStack> results = BetterSearchClient.search(query, pool);
        if (results == null) {
            return;
        }

        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        CreativeModeInventoryScreen.ItemPickerMenu menu = screen.getMenu();
        menu.items.clear();
        menu.items.addAll(results);
        menu.scrollTo(0.0F);
    }
}
