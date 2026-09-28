package com.rivalzin.bettersearch.forge.ae2;

import com.rivalzin.bettersearch.client.Ae2Search;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class Ae2ClientEvents {
    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) Ae2Search.tick();
    }
}
