package com.rivalzin.bettersearch.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

final class AltKeyMapping extends KeyMapping {
    AltKeyMapping(String name, int key, KeyMapping.Category category) {
        super(name, InputConstants.Type.KEYSYM, key, category);
    }

    @Override
    public Component getTranslatedKeyMessage() {
        Component key = super.getTranslatedKeyMessage();

        // the Alt belongs to the default key, so the label stops claiming it once moved
        return isDefault()
                ? Component.translatable("bettersearch.key.alt", key)
                : key;
    }
}
