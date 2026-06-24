package org.tf2movies.handlers;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

public class BlockEvents {

    private static final String LOCK_PREFIX = "[Private]";

    /** A lock sign is a wall sign whose first line is our marker; line two is the owner. */
    private static boolean isLockSign(TileEntity tile) {
        return tile instanceof TileEntitySign
            && LOCK_PREFIX.equals(((TileEntitySign) tile).signText[0]);
    }

    /** {dx, dz} from a chest to the wall-sign slot on the given face, or null if not a side face. */
    private static int[] faceOffset(int face) {
        switch (face) {
            case 2: return new int[] {0, -1};
            case 3: return new int[] {0, 1};
            case 4: return new int[] {-1, 0};
            case 5: return new int[] {1, 0};
            default: return null;
        }
    }

    /**
     * All chest blocks that act as a single inventory: the given chest plus its
     * double-chest partner if one of the same type sits directly beside it. Returns
     * {x, z} pairs (all share the chest's y).
     */
    private static int[][] chestGroup(World world, int cx, int cy, int cz) {
        Block chestBlock = world.getBlock(cx, cy, cz);
        int[][] neighbors = {{cx - 1, cz}, {cx + 1, cz}, {cx, cz - 1}, {cx, cz + 1}};
        for (int[] n : neighbors) {
            if (world.getBlock(n[0], cy, n[1]) == chestBlock) {
                return new int[][] {{cx, cz}, {n[0], n[1]}};
            }
        }
        return new int[][] {{cx, cz}};
    }

    /**
     * Owner of the lock protecting this chest, scanning every exposed side face of the
     * whole (possibly double) chest, or null if unlocked.
     */
    private static String findLockOwner(World world, int cx, int cy, int cz) {
        for (int[] c : chestGroup(world, cx, cy, cz)) {
            for (int face = 2; face <= 5; face++) {
                int[] off = faceOffset(face);
                int x = c[0] + off[0];
                int z = c[1] + off[1];
                if (world.getBlock(x, cy, z) == Blocks.wall_sign
                    && (world.getBlockMetadata(x, cy, z) & 7) == face) {
                    TileEntity te = world.getTileEntity(x, cy, z);
                    if (isLockSign(te)) {
                        return ((TileEntitySign) te).signText[1];
                    }
                }
            }
        }
        return null;
    }

    private static boolean isOwner(String owner, EntityPlayer player) {
        return player != null && owner != null
            && owner.equals(player.getGameProfile().getName());
    }

    private static boolean isOp(EntityPlayer player) {
        return player != null && MinecraftServer.getServer()
            .getConfigurationManager().func_152596_g(player.getGameProfile());
    }

    /**
     * Intercept the interaction *before* vanilla runs ItemSign.onItemUse / opens the
     * chest. Doing it here means the sign-editor GUI packet is never sent and we keep
     * full control over both placement and access. Server-side only — placement and
     * container access are authoritative there, so this behaves identically on
     * integrated (singleplayer) and dedicated (multiplayer) servers.
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent event) {
        if (event.world.isRemote) return;
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;

        EntityPlayer player = event.entityPlayer;

        TileEntity clicked = event.world.getTileEntity(event.x, event.y, event.z);
        boolean isChest = clicked instanceof TileEntityChest;

        // Block opening a locked chest for anyone but its owner or an op.
        if (isChest) {
            String owner = findLockOwner(event.world, event.x, event.y, event.z);
            if (owner != null && !isOwner(owner, player) && !isOp(player)) {
                event.setCanceled(true);
                player.addChatMessage(new ChatComponentText("This chest belongs to " + owner + "."));
                return;
            }
        }

        // The rest is sign placement: only when sneaking, otherwise vanilla opens it.
        if (!player.isSneaking()) return;

        ItemStack held = player.getHeldItem();
        if (held == null || held.getItem() != Items.sign) return;

        // Wall signs only attach to the four vertical faces (2-5).
        int face = event.face;
        if (faceOffset(face) == null) return;
        if (!isChest) return;

        // One lock per chest: refuse if it (or its double partner) already has one.
        String existing = findLockOwner(event.world, event.x, event.y, event.z);
        if (existing != null) {
            event.setCanceled(true);
            player.addChatMessage(new ChatComponentText("This chest is already locked."));
            return;
        }

        // Position the sign on the clicked face (mirrors ItemSign's offsetting).
        int[] off = faceOffset(face);
        int x = event.x + off[0];
        int y = event.y;
        int z = event.z + off[1];

        // Only place into empty, editable space; otherwise leave it to vanilla.
        Block target = event.world.getBlock(x, y, z);
        if (!target.isReplaceable(event.world, x, y, z)) return;
        if (!player.canPlayerEdit(x, y, z, face, held)) return;

        // We're handling this placement ourselves.
        event.setCanceled(true);

        // For a wall sign the metadata is simply the clicked face.
        event.world.setBlock(x, y, z, Blocks.wall_sign, face, 3);

        TileEntity tile = event.world.getTileEntity(x, y, z);
        if (tile instanceof TileEntitySign) {
            TileEntitySign sign = (TileEntitySign) tile;
            sign.signText[0] = LOCK_PREFIX;
            sign.signText[1] = player.getGameProfile().getName();
            sign.markDirty();
            event.world.markBlockForUpdate(x, y, z);
        }

        if (!player.capabilities.isCreativeMode) {
            if (--held.stackSize <= 0) {
                player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            }
        }
    }

    /**
     * A lock — and the chest it protects — can only be broken by the player who placed
     * it. Runs server-side via the BlockEvent bus; tile entities are still present at
     * this point so we can read the owner.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.world.isRemote) return;

        TileEntity tile = event.world.getTileEntity(event.x, event.y, event.z);

        String owner;
        if (isLockSign(tile)) {
            owner = ((TileEntitySign) tile).signText[1];
        } else if (tile instanceof TileEntityChest) {
            owner = findLockOwner(event.world, event.x, event.y, event.z);
        } else {
            return;
        }

        if (owner == null) return; // unlocked

        EntityPlayer breaker = event.getPlayer();
        if (isOwner(owner, breaker) || isOp(breaker)) return;

        event.setCanceled(true);
        if (breaker != null) {
            breaker.addChatMessage(new ChatComponentText("This lock belongs to " + owner + "."));
        }
    }
}
