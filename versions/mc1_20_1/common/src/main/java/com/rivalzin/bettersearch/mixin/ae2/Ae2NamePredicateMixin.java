package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.Ae2NamePredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
@Pseudo
@Mixin(targets = "appeng.client.gui.me.search.NameSearchPredicate", remap = false)
public abstract class Ae2NamePredicateMixin implements Ae2NamePredicate {
    @Shadow(remap = false)
    private String term;
    @Override
    public String bettersearch$term() {
        return term;
    }
}
