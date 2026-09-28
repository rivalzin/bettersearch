package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.Ae2PatternRecordAccess;
import com.rivalzin.bettersearch.client.Ae2PatternSearch;
import com.rivalzin.bettersearch.client.Ae2ScreenAccess;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.interfaceterminal.InterfaceTerminalScreen", remap = false)
public abstract class Ae2PatternScreenMixin implements Ae2ScreenAccess {
    @Shadow(remap = false)
    private HashMap<Long, Object> byId;
    @Shadow(remap = false)
    private Map<String, Set<Object>> cachedSearches;
    @Shadow(remap = false)
    protected abstract void refreshList();
    @Unique
    private Ae2PatternSearch bettersearch$search;
    @Unique
    private Object bettersearch$record;

    @Unique
    private void bettersearch$ensure() {
        if (bettersearch$search == null) {
            bettersearch$search = new Ae2PatternSearch((Screen) (Object) this);
        }
    }

    @ModifyVariable(method = "refreshList", at = @At(value = "STORE", ordinal = 0),
            ordinal = 0, remap = false, require = 1, allow = 1)
    private String bettersearch$prepare(String query) {
        bettersearch$ensure();
        if (bettersearch$search.update(byId.values(), query)) {
            cachedSearches.clear();
        }
        return query;
    }

    @Inject(method = "postInventoryUpdate",
            at = @At("HEAD"), remap = false)
    private void bettersearch$changed(CallbackInfo ci) {
        bettersearch$ensure();
        bettersearch$search.changed();
        cachedSearches.clear();
    }

    @Redirect(method = "refreshList", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/me/interfaceterminal/InterfaceRecord;getSearchName()Ljava/lang/String;"),
            remap = false)
    private String bettersearch$capture(@Coerce Object record) {
        bettersearch$record = record;
        return ((Ae2PatternRecordAccess) record).getSearchName();
    }

    @Redirect(method = "refreshList", at = @At(value = "INVOKE",
            target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"), remap = false)
    private boolean bettersearch$match(String name, CharSequence query) {
        return name.contains(query) || bettersearch$search != null
                && bettersearch$search.matches(bettersearch$record);
    }

    @Inject(method = "refreshList", at = @At("RETURN"), remap = false)
    private void bettersearch$release(CallbackInfo ci) {
        bettersearch$record = null;
    }

    @Inject(method = "updateBeforeRender", at = @At("HEAD"), remap = false)
    private void bettersearch$tick(CallbackInfo ci) {
        bettersearch$ensure();
        if (bettersearch$search.needsRefresh()) {
            refreshList();
        }
    }

    @Override
    public void bettersearch$closeAe2() {
        if (bettersearch$search != null) {
            bettersearch$search.close();
            bettersearch$search = null;
        }
        bettersearch$record = null;
        cachedSearches.clear();
    }
}
