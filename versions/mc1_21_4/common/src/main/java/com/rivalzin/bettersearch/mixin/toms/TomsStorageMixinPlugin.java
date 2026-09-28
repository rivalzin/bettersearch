package com.rivalzin.bettersearch.mixin.toms;

import com.rivalzin.bettersearch.mixin.ModPresencePlugin;

public final class TomsStorageMixinPlugin extends ModPresencePlugin {
    public TomsStorageMixinPlugin() {
        super("toms_storage", "com/tom/storagemod/screen/AbstractStorageTerminalScreen.class");
    }
}
