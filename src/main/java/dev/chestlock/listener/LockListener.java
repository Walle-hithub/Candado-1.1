package dev.chestlock.listener;

import dev.chestlock.ChestLockPlugin;
import dev.chestlock.data.LockData;
import dev.chestlock.data.LockManager;
import dev.chestlock.item.LockItems;
import dev.chestlock.util.BlockUtil;
import dev.chestlock.util.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Listener de BLOQUEO: colocar el candado + proteger el bloque (romper, explosiones, hoppers, cofres adyacentes).
 */
public final class LockListener implements Listener {

    private final ChestLockPlugin plugin;
    private final LockManager manager;
    private final LockItems items;

    public LockListener(ChestLockPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getLockManager();
        this.items = plugin.getLockItems();
    }

    // ---------------------------------------------------------------- colocar candado

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseLock(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        ItemStack hand = e.getItem();
        Block block = e.getClickedBlock();
        if (block == null || !items.isLock(hand)) return;

        // El candado es un tripwire hook: jamás debe poder colocarse como bloque.
        e.setUseItemInHand(Event.Result.DENY);
        if (!BlockUtil.isLockable(block)) return; // en bloques normales se interactúa con normalidad

        e.setCancelled(true); // no abrir el cofre al bloquearlo
        Player p = e.getPlayer();

        if (!p.hasPermission("chestlock.use")) {
            p.sendMessage(Text.msg("No tienes permiso para usar candados.", NamedTextColor.RED));
            return;
        }
        // find() mira ambas mitades: un cofre doble con una mitad ya bloqueada cuenta como bloqueado.
        if (manager.find(block) != null) {
            p.sendMessage(Text.msg("Este contenedor ya está bloqueado.", NamedTextColor.RED));
            return;
        }

        manager.create(block, p);
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        p.playSound(block.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 1.4f);
        p.sendMessage(Text.msg("Contenedor bloqueado. Eres el dueño. "
                + "(Agáchate + clic con la mano vacía para gestionar el acceso)", NamedTextColor.GREEN));
    }

    // ---------------------------------------------------------------- protección

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        LockData d = manager.find(e.getBlock());
        if (d == null) return;
        Player p = e.getPlayer();

        if (!d.isOwner(p) && !p.hasPermission("chestlock.admin")) {
            e.setCancelled(true);
            p.sendMessage(Text.msg("Está protegido por " + d.getOwnerName() + ".", NamedTextColor.RED));
            return;
        }
        if (manager.handleBreak(e.getBlock(), d)) { // candado eliminado del todo: devolver el ítem
            e.getBlock().getWorld().dropItemNaturally(
                    e.getBlock().getLocation().add(0.5, 0.5, 0.5), items.createLock(1));
            p.sendMessage(Text.msg("Candado retirado.", NamedTextColor.YELLOW));
        }
    }

    /** Evita "robar" un cofre colocando un cofre al lado de uno ajeno para fusionarlos en doble. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block placed = e.getBlockPlaced();
        if (!BlockUtil.isLockable(placed)) return;
        LockData d = manager.find(placed); // el bloque ya está colocado y enlazado en este punto
        if (d != null && !d.isOwner(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Text.msg("No puedes unir tu cofre a uno bloqueado ajeno.", NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> manager.find(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> manager.find(b) != null);
    }

    /** Hoppers/minecarts no pueden extraer ni insertar en contenedores bloqueados. */
    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        if (isLocked(e.getSource()) || isLocked(e.getDestination())) e.setCancelled(true);
    }

    private boolean isLocked(Inventory inv) {
        InventoryHolder h = inv.getHolder(false); // Paper: sin snapshot, mucho más barato
        if (h instanceof DoubleChest dc) h = dc.getLeftSide();
        return h instanceof BlockState bs && manager.find(bs.getBlock()) != null;
    }
}
