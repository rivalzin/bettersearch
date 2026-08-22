package com.rivalzin.bettersearch.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import com.rivalzin.bettersearch.client.gui.ComponentCompat;
import net.minecraft.network.chat.Component;

final class AltKeyMapping extends KeyMapping {
    AltKeyMapping(String name, int key, String category) {
        super(name, InputConstants.Type.KEYSYM, key, category);
    }

    @Override
    public Component getTranslatedKeyMessage() {
        Component key = super.getTranslatedKeyMessage();

        return isUnbound() ? key : ComponentCompat.translatable("bettersearch.key.alt", key);
    }
}
