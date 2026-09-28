package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.Ae2Search;
import java.util.regex.Pattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.items.ItemRepo", remap = false)
public abstract class Ae2ItemSearchMixin {
    @Inject(method = "matchesSearch(Lappeng/client/gui/me/common/Repo$SearchMode;Ljava/util/regex/Pattern;Lappeng/api/storage/data/IAEItemStack;)Z",
            at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private void bettersearch$match(@Coerce Object mode, Pattern pattern, @Coerce Object stack,
                                    CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Ae2Search.repositoryMatch(cir.getReturnValue(), this, stack));
    }
}
