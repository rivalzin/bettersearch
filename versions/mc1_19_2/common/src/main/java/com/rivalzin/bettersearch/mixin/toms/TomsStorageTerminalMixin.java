package com.rivalzin.bettersearch.mixin.toms;

import com.rivalzin.bettersearch.client.TomsStorageScreenAccess;
import com.rivalzin.bettersearch.client.TomsStorageSearch;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.regex.Matcher;

@Pseudo
@Mixin(targets = "com.tom.storagemod.gui.AbstractStorageTerminalScreen", remap = false)
public abstract class TomsStorageTerminalMixin implements TomsStorageScreenAccess {
    @Shadow(remap = false)
    private boolean refreshItemList;

    @Unique
    private TomsStorageSearch bettersearch$search;

    @Unique
    private boolean bettersearch$indexRefresh;

    @Unique
    private Object bettersearch$currentItem;

    @Unique
    private String bettersearch$query;

    @ModifyVariable(method = "updateSearch", at = @At(value = "STORE", ordinal = 0),
            ordinal = 0, remap = false, require = 1, allow = 1)
    private String bettersearch$prepareSearch(String query) {
        if (bettersearch$search == null) {
            bettersearch$search = new TomsStorageSearch((Screen) (Object) this,
                    () -> bettersearch$indexRefresh = true);
        }
        Object menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        boolean changed = bettersearch$search.update(
                ((TomsStorageMenuAccessor) menu).bettersearch$getItems(),
                refreshItemList, query);
        refreshItemList |= changed || bettersearch$indexRefresh;
        bettersearch$indexRefresh = false;
        bettersearch$currentItem = null;
        bettersearch$query = query;
        return query;
    }

    @Redirect(method = "updateSearch",
            at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 0),
            remap = false)
    private Object bettersearch$captureItem(List<?> items, int index) {
        Object item = items.get(index);
        bettersearch$currentItem = item;
        return item;
    }

    @Redirect(method = "updateSearch",
            at = @At(value = "INVOKE", target = "Ljava/util/regex/Matcher;find()Z", ordinal = 0),
            remap = false)
    private boolean bettersearch$matchItem(Matcher matcher) {
        return matcher.find() || bettersearch$search != null
                && bettersearch$search.matches(bettersearch$currentItem, bettersearch$query);
    }

    @Inject(method = "updateSearch", at = @At("RETURN"), remap = false)
    private void bettersearch$releaseItem(CallbackInfo ci) {
        bettersearch$currentItem = null;
    }

    @Override
    public void bettersearch$closeSearch() {
        if (bettersearch$search != null) {
            bettersearch$search.close();
            bettersearch$search = null;
        }
        bettersearch$indexRefresh = false;
        bettersearch$currentItem = null;
        bettersearch$query = null;
    }
}
