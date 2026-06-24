package org.tf2movies.proxy;

import net.minecraftforge.common.MinecraftForge;
import org.tf2movies.handlers.ClientEvents;

public class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        super.init();
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
    }
}
