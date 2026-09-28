package com.rivalzin.bettersearch.mixin.refinedstorage;

import com.rivalzin.bettersearch.mixin.ModPresencePlugin;

public final class RsMixinPlugin extends ModPresencePlugin {
    public RsMixinPlugin() {
        super("refinedstorage", "com/refinedmods/refinedstorage/common/grid/query/GridQueryParser.class");
    }
}
