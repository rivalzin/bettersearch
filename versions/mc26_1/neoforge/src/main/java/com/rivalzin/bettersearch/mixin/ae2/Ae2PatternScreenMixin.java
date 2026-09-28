package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.ae2.Ae2KeyAccess;
import com.rivalzin.bettersearch.client.ae2.Ae2PatternRecordAccess;
import com.rivalzin.bettersearch.client.ae2.Ae2PatternSearch;
import com.rivalzin.bettersearch.client.ae2.Ae2ScreenAccess;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.patternaccess.PatternAccessTermScreen", remap = false)
public abstract class Ae2PatternScreenMixin implements Ae2ScreenAccess {
    @Shadow
    @Final
    private HashMap<Long, Object> byId;
    @Shadow
    @Final
    private Map<String, Set<Object>> cachedSearches;
    @Shadow
    @Final
    private Map<ItemStack, String> patternSearchText;
    @Unique
    private Ae2PatternSearch bettersearch$patterns;
    @Unique
    private EditBox bettersearch$searchField;
    @Unique
    private boolean bettersearch$dirty = true;
    @Unique
    private boolean bettersearch$pending;
    @Unique
    private Object bettersearch$currentRecord;
    @Unique
    private List<Object> bettersearch$outputs;

    @Shadow
    private void refreshList() {
        throw new AssertionError();
    }

    @Shadow
    private String getPatternSearchText(ItemStack stack) {
        throw new AssertionError();
    }

    @Redirect(method = "refreshList", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/widgets/AETextField;getValue()Ljava/lang/String;"), require = 1, allow = 1)
    private String bettersearch$preparePatterns(@Coerce Object field) {
        bettersearch$searchField = (EditBox) field;
        String query = bettersearch$searchField.getValue();
        if (bettersearch$patterns == null) {
            bettersearch$patterns = new Ae2PatternSearch((Screen) (Object) this,
                    () -> bettersearch$pending = true, this::bettersearch$patternOutputs);
        }
        if (bettersearch$dirty) {
            bettersearch$patterns.dirty();
            bettersearch$dirty = false;
        }
        bettersearch$patterns.update(byId.values(), query.toLowerCase(java.util.Locale.ROOT));
        return query;
    }

    @Inject(method = "getCacheForSearchTerm", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bettersearch$avoidPrefixCache(String query, CallbackInfoReturnable<Set<Object>> cir) {
        if (bettersearch$patterns != null && bettersearch$patterns.isEnabled()) {
            cir.setReturnValue(new HashSet<>());
        }
    }

    @Redirect(method = "refreshList", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/me/patternaccess/PatternContainerRecord;getSearchName()Ljava/lang/String;"),
            require = 1, allow = 1)
    private String bettersearch$recordName(@Coerce Object record) {
        bettersearch$currentRecord = record;
        return ((Ae2PatternRecordAccess) record).getSearchName();
    }

    @Redirect(method = "refreshList", at = @At(value = "INVOKE",
            target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"), require = 1, allow = 1)
    private boolean bettersearch$matchRecord(String name, CharSequence query) {
        return name.contains(query) || bettersearch$patterns != null
                && bettersearch$patterns.matches(bettersearch$currentRecord);
    }

    @Redirect(method = "getPatternSearchText", at = @At(value = "INVOKE",
            target = "Lappeng/api/stacks/AEKey;getDisplayName()Lnet/minecraft/network/chat/Component;"),
            require = 1, allow = 1)
    private Component bettersearch$captureOutput(@Coerce Object key) {
        if (bettersearch$outputs != null) {
            bettersearch$outputs.add(key);
        }
        return ((Ae2KeyAccess) key).getDisplayName();
    }

    @Unique
    private List<Object> bettersearch$patternOutputs(ItemStack stack) {
        List<Object> previous = bettersearch$outputs;
        List<Object> outputs = new ArrayList<>();
        bettersearch$outputs = outputs;
        try {
            getPatternSearchText(stack);
        } finally {
            bettersearch$outputs = previous;
        }
        return outputs;
    }

    @Inject(method = {"clear", "postFullUpdate", "postIncrementalUpdate"}, at = @At("HEAD"), require = 3, allow = 3)
    private void bettersearch$patternsChanged(CallbackInfo ci) {
        bettersearch$dirty = true;
    }

    @Inject(method = "postIncrementalUpdate", at = @At("RETURN"), require = 1, allow = 2)
    private void bettersearch$refreshChanges(CallbackInfo ci) {
        bettersearch$pending = true;
    }

    @Inject(method = "updateBeforeRender", at = @At("HEAD"), require = 1, allow = 1)
    private void bettersearch$updatePatterns(CallbackInfo ci) {
        if (bettersearch$searchField != null && (bettersearch$patterns == null || bettersearch$pending
                || bettersearch$dirty || bettersearch$patterns.needsRefresh())) {
            bettersearch$pending = false;
            cachedSearches.clear();
            patternSearchText.clear();
            refreshList();
        }
    }

    @Override
    public void bettersearch$closeAe2() {
        if (bettersearch$patterns != null) {
            bettersearch$patterns.close();
            bettersearch$patterns = null;
        }
        bettersearch$dirty = true;
        bettersearch$pending = false;
        bettersearch$currentRecord = null;
        bettersearch$outputs = null;
        cachedSearches.clear();
        patternSearchText.clear();
    }
}
