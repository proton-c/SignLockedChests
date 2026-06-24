package org.tf2movies.proxy;

import net.minecraftforge.common.MinecraftForge;
import org.tf2movies.handlers.BlockEvents;

public class CommonProxy {
    public void init() {
        MinecraftForge.EVENT_BUS.register(new BlockEvents());
    }
}
