package com.rivalzin.bettersearch.mixin.jei;

import com.rivalzin.bettersearch.mixin.ModPresencePlugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableAnnotationNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class JeiMixinPlugin extends ModPresencePlugin {
    public JeiMixinPlugin() {
        super("jei", "mezz/jei/ingredients/IngredientFilter.class");
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
        if (!mixinClassName.endsWith(".IngredientFilterMixin")) {
            return;
        }
        for (MethodNode method : targetClass.methods) {
            if (!method.name.equals("getIngredientListUncached") || (method.access & Opcodes.ACC_STATIC) != 0
                    || !(method.desc.equals("(Ljava/lang/String;)Ljava/util/List;")
                    || method.desc.equals("(Ljava/lang/String;)Ljava/util/stream/Stream;"))) {
                continue;
            }
            boolean reused = false;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof VarInsnNode) {
                    VarInsnNode variable = (VarInsnNode) instruction;
                    reused |= variable.var == 1 && variable.getOpcode() >= Opcodes.ISTORE
                            && variable.getOpcode() <= Opcodes.ASTORE;
                } else if (instruction instanceof IincInsnNode) {
                    reused |= ((IincInsnNode) instruction).var == 1;
                }
            }
            if (!reused) {
                continue;
            }
            List<Object> locals = new ArrayList<Object>(Arrays.<Object>asList(targetClass.name, "java/lang/String"));
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof VarInsnNode) {
                    VarInsnNode variable = (VarInsnNode) instruction;
                    if (variable.var >= 1) {
                        variable.var++;
                    }
                } else if (instruction instanceof IincInsnNode) {
                    IincInsnNode increment = (IincInsnNode) instruction;
                    if (increment.var >= 1) {
                        increment.var++;
                    }
                } else if (instruction instanceof FrameNode) {
                    FrameNode frame = (FrameNode) instruction;
                    if (frame.type == Opcodes.F_NEW || frame.type == Opcodes.F_FULL) {
                        locals = new ArrayList<Object>(frame.local);
                    } else if (frame.type == Opcodes.F_APPEND) {
                        locals.addAll(frame.local);
                    } else if (frame.type == Opcodes.F_CHOP) {
                        locals.subList(locals.size() - frame.local.size(), locals.size()).clear();
                    } else if (frame.type != Opcodes.F_SAME && frame.type != Opcodes.F_SAME1) {
                        throw new IllegalStateException("Unsupported JEI stack map frame: " + frame.type);
                    }
                    List<Object> preserved = new ArrayList<Object>(locals);
                    if (preserved.isEmpty()) {
                        preserved.add(targetClass.name);
                    }
                    preserved.add(1, "java/lang/String");
                    frame.type = Opcodes.F_NEW;
                    frame.local = preserved;
                    if (frame.stack == null) {
                        frame.stack = new ArrayList<Object>();
                    }
                }
            }
            if (method.localVariables != null) {
                for (LocalVariableNode variable : method.localVariables) {
                    if (variable.index >= 1) {
                        variable.index++;
                    }
                }
            }
            shiftAnnotations(method.visibleLocalVariableAnnotations);
            shiftAnnotations(method.invisibleLocalVariableAnnotations);
            InsnList preserve = new InsnList();
            preserve.add(new VarInsnNode(Opcodes.ALOAD, 1));
            preserve.add(new VarInsnNode(Opcodes.ASTORE, 2));
            method.instructions.insert(preserve);
            method.maxLocals++;
            method.maxStack = Math.max(method.maxStack, 1);
        }
    }

    private static void shiftAnnotations(List<LocalVariableAnnotationNode> annotations) {
        if (annotations != null) {
            for (LocalVariableAnnotationNode annotation : annotations) {
                for (int index = 0; index < annotation.index.size(); index++) {
                    int local = annotation.index.get(index);
                    if (local >= 1) {
                        annotation.index.set(index, local + 1);
                    }
                }
            }
        }
    }
}
