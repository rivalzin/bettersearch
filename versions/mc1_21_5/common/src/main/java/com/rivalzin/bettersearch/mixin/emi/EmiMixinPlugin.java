package com.rivalzin.bettersearch.mixin.emi;

import com.rivalzin.bettersearch.mixin.ModPresencePlugin;

public class EmiMixinPlugin extends ModPresencePlugin {
    public EmiMixinPlugin() {
        super("emi", "dev/emi/emi/search/EmiSearch.class");
    }
}
