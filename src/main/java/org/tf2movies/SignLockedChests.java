package org.tf2movies;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tf2movies.handlers.BlockEvents;

@Mod(modid = "SignLockedChests", name = "Sign Locked Chests", version = Tags.VERSION, acceptableRemoteVersions = "*")
public class SignLockedChests {
    public static final Logger LOGGER = LogManager.getLogger("SignLockedChests");

    @Mod.EventHandler
    public void init(FMLInitializationEvent ev) {
        MinecraftForge.EVENT_BUS.register(new BlockEvents());
        LOGGER.info("Chest locking enabled.");
    }
}
