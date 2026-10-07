package com.rivalzin.bettersearch.mixin.chestsearchbar;

import com.rivalzin.bettersearch.client.ChestSearchBarScreenAccess;
import com.rivalzin.bettersearch.client.ChestSearchBarSearch;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ChestSearchBarScreenMixin implements ChestSearchBarScreenAccess {
    @Unique
    private ChestSearchBarSearch bettersearch$chestSearch;

    @Override
    public ChestSearchBarSearch bettersearch$chestSearch() {
        if (bettersearch$chestSearch == null) {
            bettersearch$chestSearch = new ChestSearchBarSearch();
        }
        return bettersearch$chestSearch;
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void bettersearch$closeChestSearch(CallbackInfo ci) {
        if (bettersearch$chestSearch != null) {
            bettersearch$chestSearch.close();
            bettersearch$chestSearch = null;
        }
    }
}
