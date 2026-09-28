package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.Ae2RepoAccess;
import com.rivalzin.bettersearch.client.Ae2Search;
import com.rivalzin.bettersearch.client.Ae2SearchAccess;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets = "appeng.client.gui.me.common.Repo", remap = false)
public abstract class Ae2RepoMixin implements Ae2RepoAccess {
    @Unique
    private Screen bettersearch$owner;
    @Unique
    private Ae2Search bettersearch$search;
    @Unique
    private Ae2SearchAccess bettersearch$native;
    @Override
    public void bettersearch$owner(Screen owner) {
        bettersearch$owner = owner;
    }
    @Unique
    private void bettersearch$ensure() {
        if (bettersearch$search == null && bettersearch$owner != null) {
            bettersearch$search = new Ae2Search(bettersearch$owner);
            if (bettersearch$native != null) {
                bettersearch$native.bettersearch$bind(bettersearch$search);
            }
        }
    }
    @Redirect(method = "setSearchString", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/me/search/RepoSearch;setSearchString(Ljava/lang/String;)V"), remap = false)
    private void bettersearch$bind(@Coerce Object search, String query) {
        bettersearch$native = (Ae2SearchAccess) search;
        bettersearch$ensure();
        bettersearch$native.bettersearch$bind(bettersearch$search);
        bettersearch$native.setSearchString(query);
    }
    @Inject(method = {"handleUpdate(ZLjava/util/List;)V", "clear"}, at = @At("HEAD"), remap = false)
    private void bettersearch$changed(CallbackInfo ci) {
        if (bettersearch$search != null) {
            bettersearch$search.changed();
        }
    }
    @Inject(method = "updateView", at = @At("HEAD"), remap = false)
    private void bettersearch$prepare(CallbackInfo ci) {
        bettersearch$ensure();
        if (bettersearch$search != null
                && bettersearch$search.update(getAllEntries(), getSearchString())
                && bettersearch$native != null) {
            bettersearch$native.bettersearch$invalidate();
        }
    }
    @Override
    public void bettersearch$tick() {
        bettersearch$ensure();
        if (bettersearch$search != null && bettersearch$search.needsRefresh()) {
            updateView();
        }
    }
    @Override
    public void bettersearch$close() {
        if (bettersearch$search != null) {
            bettersearch$search.close();
            bettersearch$search = null;
        }
        if (bettersearch$native != null) {
            bettersearch$native.bettersearch$bind(null);
        }
    }
}
