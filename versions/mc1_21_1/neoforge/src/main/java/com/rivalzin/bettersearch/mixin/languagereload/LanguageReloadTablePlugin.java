package com.rivalzin.bettersearch.mixin.languagereload;

import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class LanguageReloadTablePlugin implements IMixinConfigPlugin {
    private boolean compatible;

    @Override
    public void onLoad(String mixinPackage) {
        String resource = "org/hiedacamellia/languagereload/core/mixin/TranslationStorageMixin.class";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                return;
            }
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            boolean pending = node.fields.stream().anyMatch(field -> field.name.equals("separateTranslationsOnLoad")
                    && field.desc.equals("Ljava/util/Map;") && (field.access & Opcodes.ACC_STATIC) != 0);
            boolean table = node.fields.stream().anyMatch(field -> field.name.equals("separateTranslations")
                    && field.desc.equals("Ljava/util/Map;") && (field.access & Opcodes.ACC_STATIC) == 0);
            compatible = pending && table;
        } catch (IOException | RuntimeException error) {
            compatible = false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return compatible;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
