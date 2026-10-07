package com.rivalzin.bettersearch.mixin.chestsearchbar;

import com.rivalzin.bettersearch.mixin.ModPresencePlugin;

public final class ChestSearchBarMixinPlugin extends ModPresencePlugin {
    public ChestSearchBarMixinPlugin() {
        super("chestsearchbar", "cgcm/chestsearchbar/search/ContainerSearcher.class");
    }
}
