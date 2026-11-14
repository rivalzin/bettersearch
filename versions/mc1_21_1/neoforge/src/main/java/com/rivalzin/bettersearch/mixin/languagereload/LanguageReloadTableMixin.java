package com.rivalzin.bettersearch.mixin.languagereload;

import com.rivalzin.bettersearch.client.compat.LanguageReloadTables;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientLanguage.class, priority = 1100)
public abstract class LanguageReloadTableMixin implements LanguageReloadTables.Target {
    @Unique
    private ThreadLocal<String> bettersearch$targetLanguage;

    @Inject(method = {"<init>(Ljava/util/Map;Z)V", "<init>(Ljava/util/Map;ZLjava/util/Map;)V"}, at = @At("RETURN"))
    private void bettersearch$initializeTranslationTable(CallbackInfo ci) {
        if (bettersearch$targetLanguage == null) {
            bettersearch$targetLanguage = new ThreadLocal<>();
        }
        LanguageReloadTables.initialize(this, ClientLanguage.class);
    }

    @Override
    public String bettersearch$getTargetLanguage() {
        return bettersearch$targetLanguage.get();
    }

    @Override
    public void bettersearch$setTargetLanguage(String language) {
        if (language == null) {
            bettersearch$targetLanguage.remove();
        } else {
            bettersearch$targetLanguage.set(language);
        }
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
