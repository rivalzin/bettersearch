package com.rivalzin.bettersearch.forge.jei;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class HeiLookupTransformer implements IClassTransformer {
    private static final String FILTER = "mezz.jei.ingredients.IngredientFilter";
    private static final String SEARCH = "mezz/jei/search/IElementSearch";
    private static final String TOKEN = "mezz/jei/search/TokenInfo";
    private static final String SCOPE = "com/rivalzin/bettersearch/forge/jei/HeiLookupScope";
    private static final String LOOKUP = "bettersearch$nativeIngredientLookup";
    private static final String SEARCH_DESCRIPTOR = "(L" + TOKEN + ";)Ljava/util/Set;";
    private static final String LOOKUP_DESCRIPTOR = "(L" + SEARCH + ";L" + TOKEN + ";)Ljava/util/Set;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] original) {
        String target = transformedName == null ? name : transformedName;
        if (original == null || !FILTER.equals(target)) {
            return original;
        }
        try {
            ClassNode node = new ClassNode();
            new ClassReader(original).accept(node, 0);
            MethodNode lookup = null;
            for (Object value : node.methods) {
                MethodNode method = (MethodNode) value;
                if (LOOKUP.equals(method.name)) {
                    return original;
                }
                if ("findMatchingElements".equals(method.name)
                        && "(Lmezz/jei/gui/ingredients/IIngredientListElement;)Ljava/util/List;".equals(method.desc)) {
                    lookup = method;
                }
            }
            if (lookup == null) {
                return original;
            }
            MethodInsnNode match = null;
            for (AbstractInsnNode instruction : lookup.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.getOpcode() == Opcodes.INVOKEINTERFACE && SEARCH.equals(call.owner)
                            && "getSearchResults".equals(call.name) && SEARCH_DESCRIPTOR.equals(call.desc)) {
                        if (match != null) {
                            return original;
                        }
                        match = call;
                    }
                }
            }
            if (match == null) {
                return original;
            }
            lookup.instructions.set(match, new MethodInsnNode(Opcodes.INVOKESTATIC,
                    node.name, LOOKUP, LOOKUP_DESCRIPTOR, false));
            node.methods.add(nativeLookup());
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (RuntimeException | LinkageError error) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
            System.err.println("[Better Search] HEI native lookup integration unavailable: " + error);
            return original;
        }
    }

    private static MethodNode nativeLookup() {
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                LOOKUP, LOOKUP_DESCRIPTOR, null, null);
        Label begin = new Label();
        Label end = new Label();
        Label failure = new Label();
        method.visitCode();
        method.visitTryCatchBlock(begin, end, failure, null);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, SCOPE, "enter", "()V", false);
        method.visitLabel(begin);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, SEARCH, "getSearchResults", SEARCH_DESCRIPTOR, true);
        method.visitVarInsn(Opcodes.ASTORE, 2);
        method.visitLabel(end);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, SCOPE, "exit", "()V", false);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitInsn(Opcodes.ARETURN);
        method.visitLabel(failure);
        method.visitFrame(Opcodes.F_FULL, 2, new Object[]{SEARCH, TOKEN}, 1, new Object[]{"java/lang/Throwable"});
        method.visitVarInsn(Opcodes.ASTORE, 2);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, SCOPE, "exit", "()V", false);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitInsn(Opcodes.ATHROW);
        method.visitMaxs(2, 3);
        method.visitEnd();
        return method;
    }
}
