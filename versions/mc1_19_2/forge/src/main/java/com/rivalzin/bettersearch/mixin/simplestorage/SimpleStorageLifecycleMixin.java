package com.rivalzin.bettersearch.mixin.simplestorage;

import com.rivalzin.bettersearch.client.simplestorage.SimpleStorageSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class SimpleStorageLifecycleMixin {
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void bettersearch$screen(Screen screen, CallbackInfo ci) {
        SimpleStorageSearch.switchScreen(screen);
    }
}
