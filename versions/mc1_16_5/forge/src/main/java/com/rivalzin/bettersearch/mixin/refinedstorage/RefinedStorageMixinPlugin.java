package com.rivalzin.bettersearch.mixin.refinedstorage;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

public final class RefinedStorageMixinPlugin implements IMixinConfigPlugin {
    private boolean present;

    @Override
    public void onLoad(String mixinPackage) {
        present = method("com.refinedmods.refinedstorage.screen.grid.view.GridViewImpl",
                "getActiveFilters", "()Ljava/util/function/Predicate;")
                && method("com.refinedmods.refinedstorage.screen.grid.filtering.NameGridFilter",
                "test", "(Lcom/refinedmods/refinedstorage/screen/grid/stack/IGridStack;)Z")
                && method("com.refinedmods.refinedstorage.screen.grid.GridScreen", "tick", "(II)V");
    }

    private static boolean method(String target, String name, String descriptor) {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(target);
            for (MethodNode method : node.methods) {
                if (name.equals(method.name) && descriptor.equals(method.desc)) return true;
            }
        } catch (Exception | LinkageError error) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
        }
        return false;
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
