package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.Ae2EntryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets = "appeng.api.stacks.GenericStack", remap = false)
public abstract class Ae2GenericStackMixin implements Ae2EntryAccess {
    @Unique
    private Object bettersearch$key;
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void bettersearch$capture(@Coerce Object what, long amount, CallbackInfo ci) {
        bettersearch$key = what;
    }
    @Override
    public Object bettersearch$key() {
        return bettersearch$key;
    }
}
