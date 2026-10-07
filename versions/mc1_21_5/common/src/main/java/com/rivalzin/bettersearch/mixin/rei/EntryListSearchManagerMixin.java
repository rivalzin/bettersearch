package com.rivalzin.bettersearch.mixin.rei;

import com.rivalzin.bettersearch.client.ReiSearch;
import me.shedaniel.rei.impl.client.gui.widget.entrylist.EntryListSearchManager;
import me.shedaniel.rei.impl.client.search.AsyncSearchManager;
import me.shedaniel.rei.impl.common.util.HashedEntryStackWrapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = EntryListSearchManager.class, remap = false)
public abstract class EntryListSearchManagerMixin {
    @Shadow
    @Final
    private AsyncSearchManager searchManager;

    @Inject(method = "copyAndOrder", at = @At("RETURN"), cancellable = true, require = 0)
    private void bettersearch$order(List<HashedEntryStackWrapper> input,
                                    CallbackInfoReturnable<List<HashedEntryStackWrapper>> cir) {
        List<HashedEntryStackWrapper> ordered = cir.getReturnValue();
        if (ordered == null || searchManager == null) {
            return;
        }
        ReiSearch.rememberManager(searchManager);
        List<HashedEntryStackWrapper> ours = ReiSearch.reorder(
                searchManager.filter, ordered, HashedEntryStackWrapper::unwrap);
        if (ours != null) {
            cir.setReturnValue(ours);
        }
    }
}
