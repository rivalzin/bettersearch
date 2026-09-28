package com.rivalzin.bettersearch.mixin.refinedstorage;

import com.rivalzin.bettersearch.client.refinedstorage.RsRepositoryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.common.grid.query.GridQueryParser", remap = false)
public abstract class RsQueryMixin {
    @Inject(method = {"lambda$parseLiteral$0", "lambda$parseLiteral$8"},
            at = @At("RETURN"), cancellable = true, require = 1)
    private static void bettersearch$rsLiteral(@Coerce Object literal, @Coerce Object repository,
                                               @Coerce Object resource, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && repository instanceof RsRepositoryAccess
                && ((RsRepositoryAccess) repository).bettersearch$rsMatches(resource, literal)) {
            cir.setReturnValue(true);
        }
    }
}
