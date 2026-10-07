package com.rivalzin.bettersearch.mixin.simplestorage;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

public final class SimpleStorageMixinPlugin implements IMixinConfigPlugin {
    private boolean present;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode("com.lothrazar.storagenetwork.gui.NetworkWidget");
            boolean filter = false, search = false, stacks = false, field = false;
            for (MethodNode method : node.methods) {
                search |= "applySearchTextToSlots".equals(method.name) && "()V".equals(method.desc);
                filter |= "doesStackMatchSearch".equals(method.name)
                        && ("(Lnet/minecraft/world/item/ItemStack;)Z".equals(method.desc)
                        || "(Lnet/minecraft/item/ItemStack;)Z".equals(method.desc));
            }
            for (FieldNode value : node.fields) {
                stacks |= "stacks".equals(value.name) && "Ljava/util/List;".equals(value.desc);
                field |= "searchBar".equals(value.name)
                        && ("Lnet/minecraft/client/gui/components/EditBox;".equals(value.desc)
                        || "Lnet/minecraft/client/gui/widget/TextFieldWidget;".equals(value.desc));
            }
            present = search && filter && stacks && field;
        } catch (Exception | LinkageError error) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return present; }
    @Override
    public String getRefMapperConfig() { return null; }
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override
    public List<String> getMixins() { return null; }
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
