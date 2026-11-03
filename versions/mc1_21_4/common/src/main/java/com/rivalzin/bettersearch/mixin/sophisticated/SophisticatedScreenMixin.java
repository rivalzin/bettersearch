package com.rivalzin.bettersearch.mixin.sophisticated;

import com.rivalzin.bettersearch.async.StorageSearchSession;
import com.rivalzin.bettersearch.client.SophisticatedScreenAccess;
import com.rivalzin.bettersearch.client.SophisticatedSearch;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Predicate;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase", remap = false)
public abstract class SophisticatedScreenMixin implements SophisticatedScreenAccess {
    @Shadow
    private Predicate<ItemStack> stackFilter;

    @Shadow
    private void onSearchPhraseChange(String phrase) {
        throw new AssertionError();
    }

    @Unique
    private SophisticatedSearch bettersearch$sophisticatedSearch;

    @Unique
    private String bettersearch$sophisticatedQuery;

    @Unique
    private boolean bettersearch$sophisticatedRefreshing;

    @Inject(method = "updateSearchFilter", at = @At("HEAD"), remap = false)
    private void bettersearch$captureSophisticatedQuery(String phrase, CallbackInfo ci) {
        bettersearch$sophisticatedQuery = phrase;
    }

    @Inject(method = "updateSearchFilter", at = @At("RETURN"), remap = false)
    private void bettersearch$extendSophisticatedSearch(CallbackInfo ci) {
        String phrase = bettersearch$sophisticatedQuery;
        if (!StorageSearchSession.isPlainTerm(phrase)) {
            if (bettersearch$sophisticatedSearch != null) {
                bettersearch$sophisticatedSearch.close();
            }
            return;
        }
        if (bettersearch$sophisticatedSearch == null) {
            bettersearch$sophisticatedSearch = new SophisticatedSearch();
        }
        stackFilter = bettersearch$sophisticatedSearch.extend(stackFilter, phrase, this);
    }

    @Override
    public void bettersearch$refreshSophisticatedSearch() {
        if (bettersearch$sophisticatedSearch != null && bettersearch$sophisticatedQuery != null
                && !bettersearch$sophisticatedRefreshing) {
            bettersearch$sophisticatedRefreshing = true;
            try {
                onSearchPhraseChange(bettersearch$sophisticatedQuery);
            } finally {
                bettersearch$sophisticatedRefreshing = false;
            }
        }
    }

    @Override
    public void bettersearch$closeSophisticatedSearch() {
        if (bettersearch$sophisticatedSearch != null) {
            bettersearch$sophisticatedSearch.close();
            bettersearch$sophisticatedSearch = null;
        }
    }
}
