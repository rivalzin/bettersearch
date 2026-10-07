package com.rivalzin.bettersearch.mixin.chestsearchbar;

import com.rivalzin.bettersearch.client.ChestSearchBarScreenAccess;
import com.rivalzin.bettersearch.client.ChestSearchBarSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "cgcm.chestsearchbar.search.ContainerSearcher", remap = false)
public abstract class ChestSearchBarMixin {
    @Inject(method = "search", at = @At("HEAD"), remap = false)
    private static void bettersearch$beginChestSearch(String query, AbstractContainerMenu menu, int size,
                                                     CallbackInfoReturnable<List<Slot>> cir) {
        ChestSearchBarSearch search = bettersearch$search();
        if (search != null) {
            search.begin(query);
        }
    }

    @Inject(method = "search", at = @At("RETURN"), remap = false)
    private static void bettersearch$endChestSearch(String query, AbstractContainerMenu menu, int size,
                                                   CallbackInfoReturnable<List<Slot>> cir) {
        ChestSearchBarSearch search = bettersearch$search();
        if (search != null) {
            search.end();
        }
    }

    @Inject(method = "checkItem", at = @At("RETURN"), cancellable = true, remap = false)
    private static void bettersearch$extendChestSearch(ItemStack stack, String query,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            ChestSearchBarSearch search = bettersearch$search();
            if (search != null && search.matches(stack, query)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private static ChestSearchBarSearch bettersearch$search() {
        Object screen = Minecraft.getInstance().screen;
        return screen instanceof ChestSearchBarScreenAccess
                ? ((ChestSearchBarScreenAccess) screen).bettersearch$chestSearch() : null;
    }
}
