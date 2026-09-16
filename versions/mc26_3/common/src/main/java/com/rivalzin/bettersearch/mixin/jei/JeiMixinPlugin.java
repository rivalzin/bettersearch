package com.rivalzin.bettersearch.mixin.jei;

import com.rivalzin.bettersearch.mixin.ModPresencePlugin;

public class JeiMixinPlugin extends ModPresencePlugin {
    public JeiMixinPlugin() {
        super("jei", "mezz/jei/gui/ingredients/IngredientFilter.class");
    }
}
