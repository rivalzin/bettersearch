package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public final class Keybinds {
    private final KeyBinding openKey = new KeyBinding("key.bettersearch.open",
            KeyConflictContext.IN_GAME, KeyModifier.ALT, Keyboard.KEY_O,
            "key.categories.bettersearch");

    public Keybinds() {
        // two buses here: gui events on MinecraftForge, ticks on FMLCommonHandler
        ClientRegistry.registerKeyBinding(openKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (openKey.isPressed() || altComboPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new BetterSearchConfigScreen(null));
        }
    }

    private boolean altComboPressed() {
        if (openKey.getKeyCode() != Keyboard.KEY_O || openKey.getKeyModifier() != KeyModifier.ALT) {
            return false;
        }
        return Keyboard.getEventKey() == Keyboard.KEY_O
                && Keyboard.getEventKeyState()
                && (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU));
    }
}
