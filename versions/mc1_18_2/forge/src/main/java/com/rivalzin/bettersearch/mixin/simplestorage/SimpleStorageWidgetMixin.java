package com.rivalzin.bettersearch.mixin.simplestorage;

import com.rivalzin.bettersearch.client.simplestorage.SimpleStorageSearch;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.lothrazar.storagenetwork.gui.NetworkWidget", remap = false)
public abstract class SimpleStorageWidgetMixin {
    @Shadow(remap = false) public List<?> stacks;
    @Shadow(remap = false) private EditBox searchBar;

    @Inject(method = "applySearchTextToSlots", at = @At("HEAD"), remap = false)
    private void bettersearch$begin(CallbackInfo ci) {
        SimpleStorageSearch.begin(this, stacks, searchBar);
    }

    @Inject(method = "doesStackMatchSearch", at = @At("RETURN"), cancellable = true, remap = false)
    private void bettersearch$match(@Coerce Object stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) cir.setReturnValue(SimpleStorageSearch.matches(false, this, stack));
    }

    @Inject(method = "setStacks", at = @At("HEAD"), remap = false, require = 0)
    private void bettersearch$changed(List<?> stacks, CallbackInfo ci) {
        SimpleStorageSearch.changed(this);
    }
}
