package com.rivalzin.bettersearch.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftFriendsKeyMixin {
    @Inject(method = "handleGlobalKeyPress", at = @At("HEAD"), cancellable = true, require = 0)
    private void bettersearch$altWinsOverGlobalKeys(InputConstants.Key key,
                                                    boolean controlDown,
                                                    CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (!minecraft.hasAltDown()) {
            return;
        }

        KeyMapping openConfig = KeyMapping.get("key.bettersearch.open_config");
        if (openConfig != null && openConfig.matches(key)) {
            cir.setReturnValue(false);
        }
    }
}
