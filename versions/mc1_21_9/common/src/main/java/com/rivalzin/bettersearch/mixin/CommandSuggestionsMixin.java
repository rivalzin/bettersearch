package com.rivalzin.bettersearch.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.rivalzin.bettersearch.client.CommandSearch;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Style;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow
    @Final
    EditBox input;

    @Shadow
    private ParseResults<ClientSuggestionProvider> currentParse;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    private void updateUsageInfo() {
    }

    @Unique
    private CompletableFuture<Suggestions> bettersearch$lastAugmented;

    // vanilla builds the suggestion list here, we only add to it
    @Inject(method = "updateCommandInfo", at = @At("RETURN"))
    private void bettersearch$augmentSuggestions(CallbackInfo ci) {
        CompletableFuture<Suggestions> pending = this.pendingSuggestions;
        if (pending == null || pending == this.bettersearch$lastAugmented || !CommandSearch.isEnabled()) {
            return;
        }
        final ParseResults<ClientSuggestionProvider> parse = this.currentParse;
        final String text = this.input.getValue();
        final int cursor = this.input.getCursorPosition();

        this.pendingSuggestions = pending.thenCompose(suggestions -> parse != null
                ? CommandSearch.augmentCommandAsync(parse, cursor, suggestions)
                : CompletableFuture.completedFuture(CommandSearch.augmentChat(text, cursor, suggestions)));
        this.bettersearch$lastAugmented = this.pendingSuggestions;

        if (parse != null) {
            this.pendingSuggestions.thenRun(() -> {
                if (this.pendingSuggestions.isDone()) {
                    this.updateUsageInfo();
                }
            });
        }
    }

    @Redirect(
            method = "formatText",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/components/CommandSuggestions;"
                            + "UNPARSED_STYLE:Lnet/minecraft/network/chat/Style;",
                    opcode = Opcodes.GETSTATIC),
            require = 0)
    private static Style bettersearch$softenUnparsed() {
        return CommandSearch.unparsedStyle();
    }
}
