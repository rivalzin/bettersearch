package com.rivalzin.bettersearch.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.rivalzin.bettersearch.client.CommandSearch;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.commands.SharedSuggestionProvider;
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
import java.util.function.BooleanSupplier;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow
    @Final
    EditBox input;

    @Shadow
    private ParseResults<SharedSuggestionProvider> currentParse;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    private void updateUsageInfo() {
    }

    @Unique
    private CompletableFuture<Suggestions> bettersearch$lastAugmented;

    @Unique
    private long bettersearch$requestVersion;

    @Inject(method = "updateCommandInfo", at = @At("RETURN"))
    private void bettersearch$augmentSuggestions(CallbackInfo ci) {
        CompletableFuture<Suggestions> pending = this.pendingSuggestions;
        if (pending == null || pending == this.bettersearch$lastAugmented || !CommandSearch.isEnabled()) {
            return;
        }
        final ParseResults<SharedSuggestionProvider> parse = this.currentParse;
        final String text = this.input.getValue();
        final int cursor = this.input.getCursorPosition();

        final long requestVersion = ++this.bettersearch$requestVersion;
        final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        final net.minecraft.client.gui.screens.Screen screen = minecraft.screen;
        BooleanSupplier isCurrent = () -> this.bettersearch$requestVersion == requestVersion
                && this.currentParse == parse && this.input.getValue().equals(text)
                && this.input.getCursorPosition() == cursor && minecraft.screen == screen
                && CommandSearch.isEnabled();
        CompletableFuture<Suggestions> augmented = pending.thenComposeAsync(suggestions -> {
            if (!isCurrent.getAsBoolean()) {
                return CompletableFuture.completedFuture(suggestions);
            }
            return parse != null
                    ? CommandSearch.augmentCommandAsync(parse, cursor, suggestions, isCurrent)
                    : CompletableFuture.completedFuture(CommandSearch.augmentChat(text, cursor, suggestions));
        }, minecraft);
        this.pendingSuggestions = augmented;
        this.bettersearch$lastAugmented = augmented;

        if (parse != null) {
            augmented.thenAcceptAsync(result -> {
                if (this.pendingSuggestions == augmented && isCurrent.getAsBoolean()) {
                    this.updateUsageInfo();
                }
            }, minecraft);
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
