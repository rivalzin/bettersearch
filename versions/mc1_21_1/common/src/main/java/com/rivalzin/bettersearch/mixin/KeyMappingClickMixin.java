package com.rivalzin.bettersearch.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.rivalzin.bettersearch.client.ShortcutWatcher;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the press lands here before the key is matched to a control, so it lands even when another
// mod owns that key. nothing is cancelled and nothing runs unless a listener is armed
@Mixin(KeyMapping.class)
public abstract class KeyMappingClickMixin {
    @Inject(method = "click", at = @At("HEAD"))
    private static void bettersearch$watchPress(InputConstants.Key key, CallbackInfo ci) {
        ShortcutWatcher.clicked(key.getName());
    }
}
