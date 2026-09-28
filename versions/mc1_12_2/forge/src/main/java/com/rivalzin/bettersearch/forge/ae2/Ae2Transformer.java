package com.rivalzin.bettersearch.forge.ae2;

import java.util.Map;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class Ae2Transformer implements IClassTransformer {
    private static final String BRIDGE = "com/rivalzin/bettersearch/client/Ae2Search";
    private static final String ITEM = "appeng.client.me.ItemRepo";
    private static final String FLUID = "appeng.client.me.FluidRepo";
    private static final String TERMINAL = "appeng.client.gui.implementations.GuiInterfaceTerminal";

    @Override
    public byte[] transform(String name, String transformedName, byte[] original) {
        String target = transformedName == null ? name : transformedName;
        if (original == null || !(ITEM.equals(target) || FLUID.equals(target) || TERMINAL.equals(target))) {
            return original;
        }
        try {
            ClassNode node = new ClassNode();
            new ClassReader(original).accept(node, 0);
            for (Object value : node.methods) {
                MethodNode method = (MethodNode) value;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode && BRIDGE.equals(((MethodInsnNode) instruction).owner)) {
                        return original;
                    }
                }
            }
            boolean changed = TERMINAL.equals(target) ? terminal(node) : repository(node);
            if (!changed) {
                System.err.println("[Better Search] AE2 integration skipped incompatible class " + target);
                return original;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (RuntimeException | LinkageError error) {
            System.err.println("[Better Search] AE2 integration could not transform " + target + ": " + error);
            return original;
        }
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (name.equals(method.name) && (descriptor == null || descriptor.equals(method.desc))) {
                return method;
            }
        }
        return null;
    }

    private static boolean repository(ClassNode node) {
        MethodNode view = method(node, "updateView", "()V");
        if (view == null) {
            return false;
        }
        MethodInsnNode match = null;
        int candidate = -1;
        int displayCalls = 0;
        int matches = 0;
        for (AbstractInsnNode instruction : view.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if ("appeng/util/Platform".equals(call.owner)
                    && ("getItemDisplayName".equals(call.name) || "getFluidDisplayName".equals(call.name))) {
                displayCalls++;
                AbstractInsnNode previous = call.getPrevious();
                while (previous != null && previous.getOpcode() < 0) {
                    previous = previous.getPrevious();
                }
                if (previous instanceof VarInsnNode && previous.getOpcode() == Opcodes.ALOAD) {
                    candidate = ((VarInsnNode) previous).var;
                }
            }
            if ("java/util/regex/Matcher".equals(call.owner) && "find".equals(call.name) && "()Z".equals(call.desc)) {
                if (matches++ == 0 && candidate >= 0) {
                    match = call;
                }
            }
        }
        if (displayCalls != 1 || matches != 2 || candidate < 0 || match == null) {
            return false;
        }
        InsnList begin = new InsnList();
        begin.add(new VarInsnNode(Opcodes.ALOAD, 0));
        begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "beginRepository", "(Ljava/lang/Object;)V", false));
        view.instructions.insert(begin);
        InsnList extension = new InsnList();
        extension.add(new VarInsnNode(Opcodes.ALOAD, 0));
        extension.add(new VarInsnNode(Opcodes.ALOAD, candidate));
        extension.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "repositoryMatch", "(ZLjava/lang/Object;Ljava/lang/Object;)Z", false));
        view.instructions.insert(match, extension);
        return true;
    }

    private static boolean terminal(ClassNode node) {
        MethodNode refresh = method(node, "refreshList", "()V");
        MethodNode patterns = method(node, "itemStackMatchesSearchTerm", "(Lnet/minecraft/item/ItemStack;Ljava/lang/String;)Z");
        MethodNode cache = method(node, "getCacheForSearchTerm", "(Ljava/lang/String;)Ljava/util/Set;");
        if (refresh == null || patterns == null || cache == null) {
            return false;
        }
        MethodInsnNode contains = null;
        int count = 0;
        int returns = 0;
        for (AbstractInsnNode instruction : refresh.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if ("java/lang/String".equals(call.owner) && "contains".equals(call.name)
                        && "(Ljava/lang/CharSequence;)Z".equals(call.desc)) {
                    count++;
                    contains = call;
                }
            }
        }
        for (AbstractInsnNode instruction : patterns.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.IRETURN) {
                returns++;
            }
        }
        if (count != 1 || returns != 4) {
            return false;
        }
        InsnList begin = new InsnList();
        begin.add(new VarInsnNode(Opcodes.ALOAD, 0));
        begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "beginInterface", "(Ljava/lang/Object;)V", false));
        refresh.instructions.insert(begin);
        refresh.instructions.insertBefore(contains, new VarInsnNode(Opcodes.ALOAD, 0));
        refresh.instructions.set(contains, new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "contains",
                "(Ljava/lang/String;Ljava/lang/CharSequence;Ljava/lang/Object;)Z", false));
        for (AbstractInsnNode instruction : patterns.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.IRETURN) {
                InsnList extension = new InsnList();
                extension.add(new VarInsnNode(Opcodes.ALOAD, 0));
                extension.add(new VarInsnNode(Opcodes.ALOAD, 1));
                extension.add(new VarInsnNode(Opcodes.ALOAD, 2));
                extension.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "patternMatch",
                        "(ZLjava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Z", false));
                patterns.instructions.insertBefore(instruction, extension);
            }
        }
        return true;
    }
}
