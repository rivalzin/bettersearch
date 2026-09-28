package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.Ae2Search;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.interfaceterminal.InterfaceTerminalScreen", remap = false)
public abstract class Ae2InterfaceMixin {
    @Inject(method = "refreshList", at = @At("HEAD"), remap = false, require = 1, allow = 1)
    private void bettersearch$begin(CallbackInfo ci) {
        Ae2Search.beginInterface(this);
    }

    @Inject(method = "itemStackMatchesSearchTerm", at = @At("RETURN"), cancellable = true,
            remap = false, require = 1)
    private void bettersearch$pattern(ItemStack stack, String query, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Ae2Search.patternMatch(cir.getReturnValue(), this, stack, query));
    }

    @Redirect(method = "refreshList", at = @At(value = "INVOKE",
            target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
            remap = false, require = 1, allow = 1)
    private boolean bettersearch$name(String name, CharSequence query) {
        return Ae2Search.contains(name, query, this);
    }
}
