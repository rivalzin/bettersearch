package com.rivalzin.bettersearch.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.ShortcutWatcher;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyboardHandler.class)
public abstract class MinecraftFriendsKeyMixin {
    @Redirect(method = "keyPress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;handleGlobalKeyPress(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)Z"),
            require = 1)
    private boolean bettersearch$altWinsOverGlobalKeys(Minecraft minecraft, InputConstants.Key key,
                                                       boolean controlDown, long window, int action,
                                                       KeyEvent event) {
        if (ShortcutWatcher.claims(event)) {
            if (minecraft.gui.screen() == null) {
                BetterSearchClient.openConfigScreen();
                return true;
            }
            return false;
        }
        return minecraft.handleGlobalKeyPress(key, controlDown);
    }
}
