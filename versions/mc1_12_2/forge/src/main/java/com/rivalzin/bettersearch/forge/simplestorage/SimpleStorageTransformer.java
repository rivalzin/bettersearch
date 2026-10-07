package com.rivalzin.bettersearch.forge.simplestorage;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class SimpleStorageTransformer implements IClassTransformer {
    private static final String TARGET = "mrriegel.storagenetwork.gui.GuiContainerStorageInventory";
    private static final String FAST = "mrriegel.storagenetwork.gui.fb.GuiFastNetworkCrafter";
    private static final String BRIDGE = "com/rivalzin/bettersearch/client/simplestorage/SimpleStorageSearch";

    @Override
    public byte[] transform(String name, String transformedName, byte[] original) {
        String target = transformedName == null ? name : transformedName;
        if (original == null || !(TARGET.equals(target) || FAST.equals(target))) return original;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(original).accept(node, 0);
            boolean stacks = false;
            String searchBar = null;
            for (Object value : node.fields) {
                FieldNode field = (FieldNode) value;
                stacks |= "stacks".equals(field.name) && "Ljava/util/List;".equals(field.desc);
                if ("searchBar".equals(field.name) && "Lnet/minecraft/client/gui/GuiTextField;".equals(field.desc)) searchBar = field.desc;
            }
            MethodNode begin = null, match = null, changed = null;
            for (Object value : node.methods) {
                MethodNode method = (MethodNode) value;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode && BRIDGE.equals(((MethodInsnNode) instruction).owner)) return original;
                }
                if ("applySearchTextToSlots".equals(method.name) && "()Ljava/util/List;".equals(method.desc)) begin = method;
                if ("doesStackMatchSearch".equals(method.name) && "(Lnet/minecraft/item/ItemStack;)Z".equals(method.desc)) match = method;
                if ("setStacks".equals(method.name) && "(Ljava/util/List;)V".equals(method.desc)) changed = method;
            }
            if (!stacks || searchBar == null || begin == null || match == null || changed == null) return original;
            InsnList preparation = new InsnList();
            preparation.add(new VarInsnNode(Opcodes.ALOAD, 0));
            preparation.add(new VarInsnNode(Opcodes.ALOAD, 0));
            preparation.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "stacks", "Ljava/util/List;"));
            preparation.add(new VarInsnNode(Opcodes.ALOAD, 0));
            preparation.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "searchBar", searchBar));
            preparation.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "begin", "(Ljava/lang/Object;Ljava/util/List;Ljava/lang/Object;)V", false));
            begin.instructions.insert(preparation);
            for (AbstractInsnNode instruction : match.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.IRETURN) continue;
                InsnList extension = new InsnList();
                extension.add(new VarInsnNode(Opcodes.ALOAD, 0));
                extension.add(new VarInsnNode(Opcodes.ALOAD, 1));
                extension.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "matches", "(ZLjava/lang/Object;Ljava/lang/Object;)Z", false));
                match.instructions.insertBefore(instruction, extension);
            }
            InsnList invalidation = new InsnList();
            invalidation.add(new VarInsnNode(Opcodes.ALOAD, 0));
            invalidation.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "changed", "(Ljava/lang/Object;)V", false));
            changed.instructions.insert(invalidation);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (RuntimeException | LinkageError error) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
            System.err.println("[Better Search] Simple Storage Network integration skipped " + target + ": " + error);
            return original;
        }
    }
}
