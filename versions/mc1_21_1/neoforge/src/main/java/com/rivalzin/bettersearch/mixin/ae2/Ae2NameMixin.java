package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.ae2.Ae2NameAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.search.NameSearchPredicate", remap = false)
public abstract class Ae2NameMixin implements Ae2NameAccess {
    @Shadow
    @Final
    private String term;

    @Override
    public String bettersearch$term() {
        return term;
    }
}
