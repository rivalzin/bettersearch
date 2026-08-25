package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

public final class Keybinds {
    private final KeyBinding openKey = new KeyBinding("key.bettersearch.open",
            Keyboard.KEY_O, "key.categories.bettersearch");

    public Keybinds() {
        ClientRegistry.registerKeyBinding(openKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        boolean official = openKey.isPressed();
        if (openKey.getKeyCode() != Keyboard.KEY_O) {
            if (official) {
                openScreen();
            }
            return;
        }
        if (altOPressed()) {
            openScreen();
        }
    }

    private static void openScreen() {
        Minecraft.getMinecraft().displayGuiScreen(new BetterSearchConfigScreen(null));
    }

    private static boolean altOPressed() {
        return Keyboard.getEventKey() == Keyboard.KEY_O
                && Keyboard.getEventKeyState()
                && (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU));
    }
}
