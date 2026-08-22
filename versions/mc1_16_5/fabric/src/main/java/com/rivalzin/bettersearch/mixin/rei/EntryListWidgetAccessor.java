package com.rivalzin.bettersearch.mixin.rei;

import me.shedaniel.rei.api.EntryStack;
import me.shedaniel.rei.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = EntryListWidget.class, remap = false)
public interface EntryListWidgetAccessor {
    @Accessor("allStacks")
    void bettersearch$setAllStacks(List<EntryStack> value);
}
