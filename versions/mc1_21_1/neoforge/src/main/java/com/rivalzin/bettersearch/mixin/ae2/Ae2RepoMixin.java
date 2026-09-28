package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.ae2.Ae2RepoAccess;
import com.rivalzin.bettersearch.client.ae2.Ae2SearchAccess;
import com.rivalzin.bettersearch.client.ae2.Ae2StorageSearch;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.common.Repo", remap = false)
public abstract class Ae2RepoMixin implements Ae2RepoAccess {
    @Shadow
    public abstract Set<?> getAllEntries();
    @Shadow
    public abstract String getSearchString();
    @Shadow
    public abstract void updateView();
    @Unique
    private Screen bettersearch$owner;
    @Unique
    private Ae2StorageSearch bettersearch$storage;
    @Unique
    private Ae2SearchAccess bettersearch$nativeSearch;
    @Unique
    private boolean bettersearch$pending;

    @Override
    public void bettersearch$open(Screen owner) {
        bettersearch$owner = owner;
        if (bettersearch$storage == null) {
            bettersearch$storage = new Ae2StorageSearch(owner, () -> bettersearch$pending = true);
            if (bettersearch$nativeSearch != null) {
                bettersearch$nativeSearch.bettersearch$bind(bettersearch$storage);
            }
            bettersearch$pending = true;
        }
    }

    @Override
    public void bettersearch$tick() {
        if (bettersearch$storage != null
                && (bettersearch$pending || bettersearch$storage.needsRefresh())) {
            bettersearch$pending = false;
            if (bettersearch$nativeSearch != null) {
                bettersearch$nativeSearch.bettersearch$clearCache();
            }
            updateView();
        }
    }

    @Override
    public void bettersearch$close() {
        if (bettersearch$storage != null) {
            bettersearch$storage.close();
            bettersearch$storage = null;
        }
        bettersearch$owner = null;
        bettersearch$pending = false;
        if (bettersearch$nativeSearch != null) {
            bettersearch$nativeSearch.bettersearch$bind(null);
        }
    }

    @Redirect(method = "setSearchString", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/me/search/RepoSearch;setSearchString(Ljava/lang/String;)V"),
            require = 1, allow = 1)
    private void bettersearch$bindSearch(@Coerce Object search, String text) {
        bettersearch$nativeSearch = (Ae2SearchAccess) search;
        if (bettersearch$storage == null && bettersearch$owner != null) {
            bettersearch$open(bettersearch$owner);
        }
        bettersearch$nativeSearch.bettersearch$bind(bettersearch$storage);
        bettersearch$nativeSearch.setSearchString(text);
    }

    @Inject(method = "updateView", at = @At("HEAD"), require = 1, allow = 1)
    private void bettersearch$prepareSearch(CallbackInfo ci) {
        if (bettersearch$storage != null && bettersearch$storage.update(getAllEntries(), getSearchString())
                && bettersearch$nativeSearch != null) {
            bettersearch$nativeSearch.bettersearch$clearCache();
        }
    }

    @Inject(method = "handleUpdate(ZLjava/util/List;)V", at = @At("HEAD"), require = 1, allow = 1)
    private void bettersearch$inventoryChanged(CallbackInfo ci) {
        if (bettersearch$storage != null) {
            bettersearch$storage.dirty();
        }
    }

    @Inject(method = "clear", at = @At("HEAD"), require = 1, allow = 1)
    private void bettersearch$inventoryCleared(CallbackInfo ci) {
        if (bettersearch$storage != null) {
            bettersearch$storage.dirty();
        }
        if (bettersearch$nativeSearch != null) {
            bettersearch$nativeSearch.bettersearch$clearCache();
        }
    }
}
