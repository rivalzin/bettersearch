package com.rivalzin.bettersearch.mixin;

import com.rivalzin.bettersearch.client.CommandSearch;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    protected EditBox input;

    // Enter always gets here: the suggestion list only takes Tab, Esc and the arrows.
    // Nothing is cancelled, the line is only rewritten when it is a name this version renamed.
    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"))
    private void bettersearch$fixNamesBeforeSending(KeyEvent event,
                                                    CallbackInfoReturnable<Boolean> cir) {
        int key = event.key();
        if (key != 257 && key != 335) {
            return;
        }
        String typed = this.input.getValue().trim();
        String fixed = CommandSearch.rewriteOnSend(typed);
        if (!fixed.equals(typed)) {
            this.input.setValue(fixed);
        }
    }
}
