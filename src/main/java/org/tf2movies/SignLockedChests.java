package org.tf2movies;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tf2movies.proxy.CommonProxy;

@Mod(modid = "SignLockedChests", name = "Sign Locked Chests", version = Tags.VERSION)
public class SignLockedChests {
    public static final Logger LOGGER = LogManager.getLogger("SignLockedChests");

    @SidedProxy(
        clientSide = "org.tf2movies.proxy.ClientProxy",
        serverSide = "org.tf2movies.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void init(FMLInitializationEvent ev) {
        proxy.init();
    }
}
