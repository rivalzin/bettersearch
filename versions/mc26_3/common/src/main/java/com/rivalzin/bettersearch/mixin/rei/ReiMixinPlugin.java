package com.rivalzin.bettersearch.mixin.rei;

import com.rivalzin.bettersearch.mixin.ModPresencePlugin;

public class ReiMixinPlugin extends ModPresencePlugin {
    public ReiMixinPlugin() {
        super("roughlyenoughitems", "me/shedaniel/rei/impl/client/search/SearchProviderImpl.class");
    }
}
