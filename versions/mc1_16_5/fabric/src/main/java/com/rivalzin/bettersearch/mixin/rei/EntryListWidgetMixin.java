package com.rivalzin.bettersearch.mixin.rei;

import com.rivalzin.bettersearch.client.ReiSearchBridge;
import me.shedaniel.rei.api.EntryRegistry;
import me.shedaniel.rei.api.EntryStack;
import me.shedaniel.rei.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(value = EntryListWidget.class, remap = false)
public abstract class EntryListWidgetMixin {
    @Redirect(
            method = "updateSearch(Ljava/lang/String;Z)V",
            at = @At(value = "FIELD",
                    target = "Lme/shedaniel/rei/gui/widget/EntryListWidget;"
                            + "allStacks:Ljava/util/List;",
                    opcode = 181),
            require = 0)
    private void bettersearch$capture(EntryListWidget self, List<EntryStack> fromRei,
                                      String query, boolean skipLast) {
        List<EntryStack> source = EntryRegistry.getInstance().getPreFilteredList();
        List<EntryStack> ours = ReiSearchBridge.search(query, fromRei, source);
        ((EntryListWidgetAccessor) self).bettersearch$setAllStacks(ours == null ? fromRei : ours);
    }
}
