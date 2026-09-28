package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.Ae2PatternRecordAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets = "appeng.client.gui.me.interfaceterminal.InterfaceRecord", remap = false)
public abstract class Ae2PatternRecordMixin implements Ae2PatternRecordAccess {
    @Unique
    private Component bettersearch$name;
    @Unique
    private Iterable<ItemStack> bettersearch$inventory;
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void bettersearch$capture(long id, int slots, long order, Component name, CallbackInfo ci) {
        bettersearch$name = name;
    }
    @Inject(method = "getInventory", at = @At("RETURN"), remap = false)
    private void bettersearch$captureInventory(CallbackInfoReturnable<Object> cir) {
        bettersearch$inventory = (Iterable<ItemStack>) cir.getReturnValue();
    }
    @Override
    public Component bettersearch$name() {
        return bettersearch$name;
    }
    @Override
    public Iterable<ItemStack> bettersearch$inventory() {
        return bettersearch$inventory;
    }
}
