package com.rivalzin.bettersearch.forge.refinedstorage;

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

public final class RefinedStorageTransformer implements IClassTransformer {
    private static final String PREFIX = "com.raoulvdberge.refinedstorage.gui.grid.";
    private static final String VIEW = PREFIX + "view.GridViewBase";
    private static final String FILTER = PREFIX + "filtering.GridFilterName";
    private static final String BRIDGE = "com/rivalzin/bettersearch/client/refinedstorage/RefinedStorageSearch";

    @Override
    public byte[] transform(String name, String transformedName, byte[] original) {
        String target = transformedName == null ? name : transformedName;
        if (original == null || !(VIEW.equals(target) || FILTER.equals(target))) return original;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(original).accept(node, 0);
            for (Object entry : node.methods) {
                for (AbstractInsnNode instruction : ((MethodNode) entry).instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode
                            && BRIDGE.equals(((MethodInsnNode) instruction).owner)) return original;
                }
            }
            boolean changed = VIEW.equals(target) ? view(node) : filter(node);
            if (!changed) return original;
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (RuntimeException | LinkageError error) {
            System.err.println("[Better Search] Refined Storage integration skipped " + target + ": " + error);
            return original;
        }
    }

    private static MethodNode find(ClassNode node, String name, String descriptor) {
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        }
        return null;
    }

    private static boolean view(ClassNode node) {
        MethodNode method = find(node, "sort", "()V");
        if (method == null) return false;
        MethodInsnNode match = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if ("java/util/function/Predicate".equals(call.owner) && "test".equals(call.name)
                    && "(Ljava/lang/Object;)Z".equals(call.desc)) {
                if (match != null) return false;
                match = call;
            }
        }
        if (match == null) return false;
        InsnList begin = new InsnList();
        begin.add(new VarInsnNode(Opcodes.ALOAD, 0));
        begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "begin", "(Ljava/lang/Object;)V", false));
        method.instructions.insert(begin);
        method.instructions.insertBefore(match, new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.set(match, new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "test",
                "(Ljava/util/function/Predicate;Ljava/lang/Object;Ljava/lang/Object;)Z", false));
        return true;
    }

    private static boolean filter(ClassNode node) {
        boolean nameField = false;
        for (Object entry : node.fields) {
            FieldNode field = (FieldNode) entry;
            nameField |= "name".equals(field.name) && "Ljava/lang/String;".equals(field.desc);
        }
        if (!nameField) return false;
        MethodNode method = find(node, "test",
                "(Lcom/raoulvdberge/refinedstorage/gui/grid/stack/IGridStack;)Z");
        if (method == null) return false;
        int returns = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.IRETURN) returns++;
        }
        if (returns != 1) return false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.IRETURN) continue;
            InsnList extension = new InsnList();
            extension.add(new VarInsnNode(Opcodes.ALOAD, 0));
            extension.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "name", "Ljava/lang/String;"));
            extension.add(new VarInsnNode(Opcodes.ALOAD, 1));
            extension.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "matches",
                    "(ZLjava/lang/String;Ljava/lang/Object;)Z", false));
            method.instructions.insertBefore(instruction, extension);
        }
        return true;
    }
}
