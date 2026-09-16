package com.rivalzin.bettersearch.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BetterSearchPort263Test {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/";
    private static final String PACKAGE = "com/rivalzin/bettersearch/";
    private static final Map<String, ClassNode> CLASSES = new HashMap<>();
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(new KeyEvent(InputConstants.KEY_RETURN, 0, 0).isConfirmation(), "Enter must confirm");
        check(new KeyEvent(InputConstants.KEY_NUMPADENTER, 0, 0).isConfirmation(), "Numpad Enter must confirm");
        check(!new KeyEvent(InputConstants.KEY_SPACE, 0, 0).isConfirmation(), "Space must not send chat");
        check(!new KeyEvent(InputConstants.KEY_O, 0, 0).isConfirmation(), "O must not send chat");
        check(InputConstants.Type.valueOf("KEYBOARD") != null, "SDL keyboard mapping type");
        check(InputConstants.MOUSE_BUTTON_LEFT == 1, "SDL left mouse button");
        check(InputConstants.KEY_RETURN == 40 && InputConstants.KEY_NUMPADENTER == 88, "SDL Enter scancodes");
        check(InputConstants.KEY_O == 18, "SDL shortcut scancode");

        JsonObject config;
        try (InputStream stream = resource("bettersearch.mixins.json")) {
            config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String prefix = config.get("package").getAsString().replace('.', '/') + "/";
        for (var element : config.getAsJsonArray("client")) {
            verifyMixin(read(prefix + element.getAsString()));
        }

        ClassNode chat = read(PACKAGE + "mixin/ChatScreenMixin");
        check(calls(chat, "isConfirmation", "()Z"), "Chat must use the game confirmation predicate");
        ClassNode screen = read(PACKAGE + "client/gui/BetterSearchConfigScreen");
        check(calls(screen, "confirmLinkNow", "(Lnet/minecraft/client/gui/screens/Screen;Ljava/net/URI;Z)V"),
                "Links must use the supported URI confirmation API");
        ClassNode rows = read(PACKAGE + "client/gui/OptionRowsScreen");
        MethodNode drag = findMethod(rows, "beginScrollbarDrag", "(DDI)Z");
        boolean leftButton = false;
        for (AbstractInsnNode instruction : drag.instructions) {
            if (instruction.getOpcode() == Opcodes.ICONST_1 && instruction.getNext() != null
                    && instruction.getNext().getOpcode() == Opcodes.IF_ICMPNE) {
                leftButton = true;
            }
        }
        check(leftButton, "Scrollbar must accept the SDL left button");
        String keys = args[0].equals("fabric") ? "fabric/BetterSearchFabricKeys" : "neoforge/BetterSearchKeys";
        check(noGlfw(read(PACKAGE + keys)), "Shortcut registration must not reference GLFW");
        if (args[0].equals("fabric")) {
            check(keyboardType(read(PACKAGE + "fabric/AltKeyMapping")), "Fabric shortcut uses KEYBOARD");
        } else {
            check(keyboardType(read(PACKAGE + keys)), "NeoForge shortcut uses KEYBOARD");
        }
        try (InputStream stream = resource("pack.mcmeta")) {
            JsonObject pack = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("pack");
            check(pack.getAsJsonArray("min_format").get(0).getAsInt() == 97, "Resource format major");
            check(pack.getAsJsonArray("min_format").get(1).getAsInt() == 1, "Resource format minor");
        }
        System.out.println("BetterSearch 26.3 " + args[0] + ": " + checks + " verification checks passed");
    }

    private static void verifyMixin(ClassNode mixin) throws Exception {
        AnnotationNode declaration = annotation(mixin.visibleAnnotations, mixin.invisibleAnnotations, MIXIN + "Mixin;");
        check(declaration != null, "Mixin declaration: " + mixin.name);
        List<?> targets = (List<?>) value(declaration, "value");
        check(targets != null && targets.size() == 1, "Single vanilla target: " + mixin.name);
        ClassNode target = read(((Type) targets.get(0)).getInternalName());
        for (FieldNode field : mixin.fields) {
            if (annotation(field.visibleAnnotations, field.invisibleAnnotations, MIXIN + "Shadow;") != null) {
                FieldNode actual = findField(target, field.name, field.desc);
                check(actual != null, "Shadow field: " + target.name + "." + field.name + field.desc);
                check((actual.access & Opcodes.ACC_STATIC) == (field.access & Opcodes.ACC_STATIC), "Shadow field staticness");
            }
        }
        for (MethodNode handler : mixin.methods) {
            if (annotation(handler.visibleAnnotations, handler.invisibleAnnotations, MIXIN + "Shadow;") != null) {
                check(findMethod(target, handler.name, handler.desc) != null, "Shadow method: " + handler.name + handler.desc);
            }
            AnnotationNode accessor = annotation(handler.visibleAnnotations, handler.invisibleAnnotations, MIXIN + "gen/Accessor;");
            if (accessor != null) {
                check(findField(target, (String) value(accessor, "value"), Type.getReturnType(handler.desc).getDescriptor()) != null,
                        "Accessor: " + handler.name);
            }
            AnnotationNode invoker = annotation(handler.visibleAnnotations, handler.invisibleAnnotations, MIXIN + "gen/Invoker;");
            if (invoker != null) {
                check(findMethod(target, (String) value(invoker, "value"), handler.desc) != null, "Invoker: " + handler.name);
            }
            for (String kind : List.of("Inject", "Redirect")) {
                AnnotationNode injection = annotation(handler.visibleAnnotations, handler.invisibleAnnotations,
                        MIXIN + "injection/" + kind + ";");
                if (injection == null) {
                    continue;
                }
                for (Object selector : (List<?>) value(injection, "method")) {
                    String signature = (String) selector;
                    int descriptor = signature.indexOf('(');
                    String name = descriptor < 0 ? signature : signature.substring(0, descriptor);
                    String desc = descriptor < 0 ? null : signature.substring(descriptor);
                    List<MethodNode> methods = findMethods(target, name, desc);
                    check(!methods.isEmpty(), "Injection target: " + target.name + "." + signature);
                    if (kind.equals("Inject")) {
                        check(methods.stream().anyMatch(method -> validCallback(handler, method)),
                                "Injection callback parameters: " + handler.name);
                    }
                    Object locations = value(injection, "at");
                    List<?> points = locations instanceof List<?> list ? list : List.of(locations);
                    for (Object point : points) {
                        AnnotationNode at = (AnnotationNode) point;
                        String referenced = (String) value(at, "target");
                        if (referenced != null) {
                            check(methods.stream().anyMatch(method -> references(method, referenced)),
                                    "Injection instruction: " + target.name + "." + name + " -> " + referenced);
                        }
                    }
                }
            }
        }
    }

    private static boolean validCallback(MethodNode handler, MethodNode target) {
        Type[] actual = Type.getArgumentTypes(handler.desc);
        int callback = -1;
        for (int i = 0; i < actual.length; i++) {
            if (actual[i].getDescriptor().startsWith(MIXIN + "injection/callback/CallbackInfo")) {
                callback = i;
                break;
            }
        }
        if (callback < 0) {
            return false;
        }
        return callback == 0 || Arrays.equals(Arrays.copyOf(actual, callback), Type.getArgumentTypes(target.desc));
    }

    private static boolean references(MethodNode method, String referenced) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && referenced.equals("L" + call.owner + ";" + call.name + call.desc)) {
                return true;
            }
            if (instruction instanceof FieldInsnNode field
                    && referenced.equals("L" + field.owner + ";" + field.name + ":" + field.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean calls(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals(name) && call.desc.equals(desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean noGlfw(ClassNode node) {
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.startsWith("org/lwjgl/glfw/")) {
                    return false;
                }
                if (instruction instanceof FieldInsnNode field && field.owner.startsWith("org/lwjgl/glfw/")) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean keyboardType(ClassNode node) {
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.name.equals("KEYBOARD")
                        && field.owner.equals("com/mojang/blaze3d/platform/InputConstants$Type")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<MethodNode> findMethods(ClassNode node, String name, String desc) throws Exception {
        List<MethodNode> found = new ArrayList<>();
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && (desc == null || method.desc.equals(desc))) {
                found.add(method);
            }
        }
        if (found.isEmpty() && node.superName != null) {
            return findMethods(read(node.superName), name, desc);
        }
        return found;
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) throws Exception {
        List<MethodNode> found = findMethods(node, name, desc);
        return found.isEmpty() ? null : found.get(0);
    }

    private static FieldNode findField(ClassNode node, String name, String desc) throws Exception {
        for (FieldNode field : node.fields) {
            if (field.name.equals(name) && field.desc.equals(desc)) {
                return field;
            }
        }
        return node.superName == null ? null : findField(read(node.superName), name, desc);
    }

    private static AnnotationNode annotation(List<AnnotationNode> visible, List<AnnotationNode> invisible, String desc) {
        for (List<AnnotationNode> annotations : Arrays.asList(visible, invisible)) {
            if (annotations != null) {
                for (AnnotationNode annotation : annotations) {
                    if (annotation.desc.equals(desc)) {
                        return annotation;
                    }
                }
            }
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals(key)) {
                    return annotation.values.get(i + 1);
                }
            }
        }
        return null;
    }

    private static ClassNode read(String name) throws Exception {
        ClassNode cached = CLASSES.get(name);
        if (cached != null) {
            return cached;
        }
        ClassNode node = new ClassNode();
        try (InputStream stream = resource(name + ".class")) {
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        CLASSES.put(name, node);
        return node;
    }

    private static InputStream resource(String name) {
        InputStream stream = BetterSearchPort263Test.class.getClassLoader().getResourceAsStream(name);
        check(stream != null, "Classpath resource: " + name);
        return stream;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
