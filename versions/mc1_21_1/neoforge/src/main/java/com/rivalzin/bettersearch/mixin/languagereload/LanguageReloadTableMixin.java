package com.rivalzin.bettersearch.mixin.languagereload;

import com.rivalzin.bettersearch.client.compat.LanguageReloadTables;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientLanguage.class, priority = 1100)
public abstract class LanguageReloadTableMixin {
    @Inject(method = {"<init>(Ljava/util/Map;Z)V", "<init>(Ljava/util/Map;ZLjava/util/Map;)V"}, at = @At("RETURN"))
    private void bettersearch$initializeTranslationTable(CallbackInfo ci) {
        LanguageReloadTables.initialize(this, ClientLanguage.class);
    }

    @Inject(method = "loadFrom", at = @At("RETURN"))
    private static void bettersearch$releaseTranslationTable(ResourceManager resources,
            java.util.List<String> definitions, boolean rightToLeft,
            CallbackInfoReturnable<ClientLanguage> cir) {
        LanguageReloadTables.release(ClientLanguage.class);
    }
}
