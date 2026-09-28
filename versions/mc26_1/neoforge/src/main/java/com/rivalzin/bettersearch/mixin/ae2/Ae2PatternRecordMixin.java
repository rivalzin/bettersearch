package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.ae2.Ae2PatternRecordAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.patternaccess.PatternContainerRecord", remap = false)
public abstract class Ae2PatternRecordMixin implements Ae2PatternRecordAccess {
    @Unique
    private Iterable<?> bettersearch$inventory = Collections.emptyList();

    @Inject(method = "getInventory", at = @At("RETURN"), require = 1, allow = 1)
    private void bettersearch$captureInventory(CallbackInfoReturnable<Object> cir) {
        bettersearch$inventory = (Iterable<?>) cir.getReturnValue();
    }

    @Override
    public Iterable<?> bettersearch$inventory() {
        return bettersearch$inventory;
    }
}
