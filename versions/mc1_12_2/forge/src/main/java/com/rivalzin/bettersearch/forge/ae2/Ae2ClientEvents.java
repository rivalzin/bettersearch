package com.rivalzin.bettersearch.forge.ae2;

import com.rivalzin.bettersearch.client.Ae2Search;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class Ae2ClientEvents {
    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Ae2Search.tick();
            com.rivalzin.bettersearch.client.refinedstorage.RefinedStorageSearch.tick();
            if (com.rivalzin.bettersearch.client.IntegrationAvailability.available("simple_storage_network")) {
                com.rivalzin.bettersearch.client.simplestorage.SimpleStorageSearch.closeInactive();
            }
        }
    }
}
