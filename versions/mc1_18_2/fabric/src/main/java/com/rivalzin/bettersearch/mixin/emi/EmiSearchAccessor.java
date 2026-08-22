package com.rivalzin.bettersearch.mixin.emi;

import dev.emi.emi.search.EmiSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EmiSearch.class, remap = false)
public interface EmiSearchAccessor {
    @Accessor("query")
    static String bettersearch$query() {
        throw new AssertionError("substituido pelo Mixin");
    }
}
