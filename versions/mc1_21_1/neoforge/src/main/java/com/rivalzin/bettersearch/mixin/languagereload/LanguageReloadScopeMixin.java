package com.rivalzin.bettersearch.mixin.languagereload;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rivalzin.bettersearch.client.compat.LanguageReloadTables;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SessionSearchTrees.class)
public abstract class LanguageReloadScopeMixin {
    @WrapMethod(method = "lambda$getTooltipLines$0")
    private static Stream<Component> bettersearch$restoreLanguageTarget(Item.TooltipContext context,
            TooltipFlag flag, ItemStack stack, Operation<Stream<Component>> original) {
        if (!(Language.getInstance() instanceof LanguageReloadTables.Target target)) {
            return original.call(context, flag, stack);
        }
        String previous = target.bettersearch$getTargetLanguage();
        try {
            return original.call(context, flag, stack);
        } finally {
            target.bettersearch$setTargetLanguage(previous);
        }
    }
}
