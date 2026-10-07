package com.rivalzin.bettersearch.mixin.languagereload;

import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class LanguageReloadTablePlugin implements IMixinConfigPlugin {
    private boolean compatible;

    @Override
    public void onLoad(String mixinPackage) {
        compatible = false;
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
        boolean capturing = false;
        for (var method : targetClass.methods) {
            if (!method.name.equals("appendFrom")
                    || !method.desc.equals("(Ljava/lang/String;Ljava/util/List;Ljava/util/Map;Ljava/util/Map;)V")) {
                continue;
            }
            for (var instruction : method.instructions) {
                if (instruction instanceof InvokeDynamicInsnNode factory
                        && factory.desc.equals("(Ljava/util/Map;)Ljava/util/function/BiConsumer;")
                        && factory.bsmArgs.length > 1 && factory.bsmArgs[1] instanceof Handle handle
                        && handle.getOwner().equals("java/util/Map") && handle.getName().equals("put")) {
                    InsnList capture = new InsnList();
                    capture.add(new LdcInsnNode(Type.getObjectType(targetClass.name)));
                    capture.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    capture.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "com/rivalzin/bettersearch/client/compat/LanguageReloadTables", "capture",
                            "(Ljava/util/function/BiConsumer;Ljava/lang/Class;Ljava/lang/String;)Ljava/util/function/BiConsumer;", false));
                    method.instructions.insert(factory, capture);
                    method.maxStack += 2;
                    capturing = true;
                    break;
                }
            }
        }
        for (var method : targetClass.methods) {
            if (!method.name.contains("onInternalLoad$saveSeparately")
                    || !method.desc.equals("(Ljava/io/InputStream;Ljava/util/function/BiConsumer;Ljava/util/function/BiConsumer;Ljava/lang/String;)V")
                    || (method.access & Opcodes.ACC_STATIC) == 0) {
                continue;
            }
            boolean changed = false;
            for (var instruction : method.instructions.toArray()) {
                if (capturing && instruction instanceof InvokeDynamicInsnNode factory
                        && factory.desc.equals("(Ljava/lang/String;)Ljava/util/function/BiConsumer;")
                        && factory.getPrevious() instanceof VarInsnNode code && code.getOpcode() == Opcodes.ALOAD && code.var == 3
                        && factory.getNext() instanceof MethodInsnNode chain && chain.owner.equals("java/util/function/BiConsumer")
                        && chain.name.equals("andThen")
                        && chain.desc.equals("(Ljava/util/function/BiConsumer;)Ljava/util/function/BiConsumer;")) {
                    method.instructions.remove(code);
                    method.instructions.remove(chain);
                    method.instructions.remove(factory);
                }
                if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.owner.equals("net/minecraft/locale/Language") && call.name.equals("loadFromJson")
                        && call.desc.equals("(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V")) {
                    method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 2));
                    call.desc = "(Ljava/io/InputStream;Ljava/util/function/BiConsumer;Ljava/util/function/BiConsumer;)V";
                    changed = true;
                }
            }
            if (changed) {
                method.maxStack++;
            }
        }
    }
}
