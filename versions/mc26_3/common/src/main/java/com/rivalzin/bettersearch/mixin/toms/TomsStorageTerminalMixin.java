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
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

@Pseudo
@Mixin(targets = "com.tom.storagemod.screen.AbstractStorageTerminalScreen", remap = false)
public abstract class TomsStorageTerminalMixin implements TomsStorageScreenAccess {
    @Shadow(remap = false)
    private boolean refreshItemList;

    @Unique
    private TomsStorageSearch bettersearch$search;

    @Unique
    private boolean bettersearch$indexRefresh;

    @Unique
    private String bettersearch$term;

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
        bettersearch$term = null;
        return query;
    }

    @ModifyArg(method = "updateSearch",
            at = @At(value = "INVOKE",
                    target = "Lcom/tom/storagemod/screen/AbstractStorageTerminalScreen;buildPattern(Ljava/lang/String;)Ljava/util/regex/Pattern;",
                    ordinal = 1),
            index = 0, remap = false)
    private String bettersearch$captureTerm(String term) {
        bettersearch$term = term;
        return term;
    }

    @ModifyArg(method = "updateSearch",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/function/Predicate;and(Ljava/util/function/Predicate;)Ljava/util/function/Predicate;",
                    ordinal = 3),
            index = 0, remap = false)
    private Predicate<Object> bettersearch$extendTerm(Predicate<Object> original) {
        return bettersearch$search == null
                ? original : bettersearch$search.extend(bettersearch$term, original);
    }

    @Override
    public void bettersearch$closeSearch() {
        if (bettersearch$search != null) {
            bettersearch$search.close();
            bettersearch$search = null;
        }
        bettersearch$indexRefresh = false;
        bettersearch$term = null;
    }
}
