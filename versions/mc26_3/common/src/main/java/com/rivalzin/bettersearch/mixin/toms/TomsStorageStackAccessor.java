package com.rivalzin.bettersearch.mixin.toms;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "com.tom.storagemod.inventory.StoredItemStack", remap = false)
public interface TomsStorageStackAccessor {
    @Invoker(value = "getStack", remap = false)
    ItemStack bettersearch$getStack();
}
