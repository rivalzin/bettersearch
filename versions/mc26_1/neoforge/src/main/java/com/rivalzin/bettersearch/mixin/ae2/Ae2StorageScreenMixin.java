package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.ae2.Ae2RepoAccess;
import com.rivalzin.bettersearch.client.ae2.Ae2ScreenAccess;
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
@Mixin(targets = "appeng.client.gui.me.common.MEStorageScreen", remap = false)
public abstract class Ae2StorageScreenMixin implements Ae2ScreenAccess {
    @Unique
    private Ae2RepoAccess bettersearch$repo;

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/me/common/Repo;setUpdateViewListener(Ljava/lang/Runnable;)V"),
            require = 1, allow = 1)
    private void bettersearch$bindRepo(@Coerce Object repo, Runnable listener) {
        bettersearch$repo = (Ae2RepoAccess) repo;
        bettersearch$repo.bettersearch$open((Screen) (Object) this);
        bettersearch$repo.setUpdateViewListener(listener);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"), require = 1, allow = 1)
    private void bettersearch$updateSearch(CallbackInfo ci) {
        if (bettersearch$repo != null) {
            bettersearch$repo.bettersearch$open((Screen) (Object) this);
            bettersearch$repo.bettersearch$tick();
        }
    }

    @Override
    public void bettersearch$closeAe2() {
        if (bettersearch$repo != null) {
            bettersearch$repo.bettersearch$close();
        }
    }
}
