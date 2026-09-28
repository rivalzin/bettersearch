package com.rivalzin.bettersearch.mixin.refinedstorage;

import com.rivalzin.bettersearch.client.refinedstorage.RsRepositoryAccess;
import com.rivalzin.bettersearch.client.refinedstorage.RsApi;
import com.rivalzin.bettersearch.client.refinedstorage.RsStorageSearch;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryImpl", remap = false)
public abstract class RsRepositoryMixin implements RsRepositoryAccess {
    @Shadow
    private boolean preventSorting;
    @Shadow
    public abstract void sort();
    @Unique
    private RsStorageSearch bettersearch$rsSearch;
    @Unique
    private String bettersearch$rsQuery = "";
    @Unique
    private boolean bettersearch$rsPending;

    @Override
    public void bettersearch$rsOpen(Screen owner) {
        if (bettersearch$rsSearch == null) {
            bettersearch$rsSearch = new RsStorageSearch(owner, () -> bettersearch$rsPending = true);
            bettersearch$rsPending = true;
        }
    }

    @Override
    public void bettersearch$rsQuery(String query) {
        bettersearch$rsQuery = query == null ? "" : query;
    }

    @Override
    public void bettersearch$rsTick() {
        if (bettersearch$rsSearch != null && !preventSorting
                && (bettersearch$rsPending || bettersearch$rsSearch.needsRefresh())) {
            bettersearch$rsPending = false;
            sort();
        }
    }

    @Override
    public boolean bettersearch$rsMatches(Object resource, Object literal) {
        return bettersearch$rsSearch != null && bettersearch$rsSearch.matches(resource, literal);
    }

    @Override
    public void bettersearch$rsClose() {
        if (bettersearch$rsSearch != null) {
            bettersearch$rsSearch.close();
            bettersearch$rsSearch = null;
        }
        bettersearch$rsPending = false;
    }

    @Inject(method = "sort", at = @At("HEAD"), require = 1, allow = 1)
    private void bettersearch$rsPrepare(CallbackInfo ci) {
        if (bettersearch$rsSearch != null) {
            bettersearch$rsSearch.update(this, bettersearch$rsQuery);
        }
    }

    @Inject(method = "update", at = @At("HEAD"), require = 1, allow = 1)
    private void bettersearch$rsChanged(@Coerce Object resource, long amount, CallbackInfo ci) {
        if (bettersearch$rsSearch != null) {
            long previous = RsApi.amount(this, resource);
            if (previous == 0 && amount > 0 || previous > 0 && amount <= -previous) {
                bettersearch$rsSearch.dirty();
            }
        }
    }

    @Inject(method = "clear", at = @At("HEAD"), require = 1, allow = 1)
    private void bettersearch$rsCleared(CallbackInfo ci) {
        if (bettersearch$rsSearch != null) {
            bettersearch$rsSearch.dirty();
        }
    }
}
