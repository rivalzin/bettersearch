package com.rivalzin.bettersearch.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeScreenAccessor {
    @Accessor("searchBox")
    EditBox bettersearch$searchBox();

    @Invoker("refreshSearchResults")
    void bettersearch$refreshSearch();
}
