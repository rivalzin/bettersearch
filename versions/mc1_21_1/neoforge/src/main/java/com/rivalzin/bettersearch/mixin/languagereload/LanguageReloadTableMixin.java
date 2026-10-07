package com.rivalzin.bettersearch.mixin.languagereload;

import com.rivalzin.bettersearch.client.compat.LanguageReloadTables;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientLanguage.class, priority = 1100)
public abstract class LanguageReloadTableMixin {
    @Inject(method = {"<init>(Ljava/util/Map;Z)V", "<init>(Ljava/util/Map;ZLjava/util/Map;)V"}, at = @At("RETURN"))
    private void bettersearch$initializeTranslationTable(CallbackInfo ci) {
        LanguageReloadTables.initialize(this, ClientLanguage.class);
    }

    @WrapMethod(method = "loadFrom")
    private static ClientLanguage bettersearch$releaseTranslationTable(ResourceManager resources,
            java.util.List<String> definitions, boolean rightToLeft,
            Operation<ClientLanguage> original) {
        try {
            return original.call(resources, definitions, rightToLeft);
        } finally {
            LanguageReloadTables.release(ClientLanguage.class);
        }
    }
}
