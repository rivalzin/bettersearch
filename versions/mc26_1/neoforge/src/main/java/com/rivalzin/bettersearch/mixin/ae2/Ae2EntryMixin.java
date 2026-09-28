package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.ae2.Ae2EntryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "appeng.menu.me.common.GridInventoryEntry", remap = false)
public abstract class Ae2EntryMixin implements Ae2EntryAccess {
    @Unique
    private Object bettersearch$key;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1, allow = 1)
    private void bettersearch$captureKey(long serial, @Coerce Object key, long stored, long requestable,
                                         boolean craftable, CallbackInfo ci) {
        bettersearch$key = key;
    }

    @Override
    public Object bettersearch$key() {
        return bettersearch$key;
    }
}
