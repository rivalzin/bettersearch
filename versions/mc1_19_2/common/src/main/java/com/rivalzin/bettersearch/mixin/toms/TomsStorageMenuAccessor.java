package com.rivalzin.bettersearch.mixin.toms;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Pseudo
@Mixin(targets = "com.tom.storagemod.gui.StorageTerminalMenu", remap = false)
public interface TomsStorageMenuAccessor {
    @Accessor(value = "itemListClient", remap = false)
    List<?> bettersearch$getItems();
}
