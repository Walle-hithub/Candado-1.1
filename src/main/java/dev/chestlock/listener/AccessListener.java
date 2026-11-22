package dev.chestlock.listener;

import dev.chestlock.ChestLockPlugin;
import dev.chestlock.data.LockData;
import dev.chestlock.util.BlockUtil;
import dev.chestlock.util.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Listener de ACCESO: decide qué pasa cuando alguien hace clic derecho en un contenedor bloqueado.
 *
 *   dueño             -> abre normal; agachado + mano vacía => GUI de confianza
 *   confiado / bypass -> abre normal
 *   resto             -> sin ganzúa: denegado | con ganzúa: minijuego
 */
public final class AccessListener implements Listener {

    private final ChestLockPlugin plugin;

    public AccessListener(ChestLockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null || !BlockUtil.isLockable(block)) return;

        LockData lock = plugin.getLockManager().find(block);
        if (lock == null) return;

        Player p = e.getPlayer();
        // El evento se dispara también para la mano secundaria: la denegación debe aplicar a ambas,
        // pero las acciones (GUI / minijuego) solo con la mano principal para no duplicarlas.
        boolean mainHand = e.getHand() == EquipmentSlot.HAND;
        ItemStack hand = p.getInventory().getItemInMainHand();

        if (lock.isOwner(p)) {
            if (mainHand && p.isSneaking() && hand.getType().isAir()) {
                e.setCancelled(true);
                plugin.getTrustGui().open(p, lock);
            }
            return; // si no, el cofre se abre con normalidad
        }
        if (lock.isTrusted(p) || p.hasPermission("chestlock.bypass")) return;

        // --- Acceso no autorizado ---
        e.setCancelled(true);
        if (!mainHand) return;

        if (plugin.getLockItems().isLockpick(hand)) {
            plugin.getLockpickGame().start(p, block, lock, hand);
        } else {
            p.playSound(block.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
            p.sendMessage(Text.msg("Bloqueado por " + lock.getOwnerName()
                    + ". Necesitas una ganzúa para forzarlo.", NamedTextColor.RED));
        }
    }
}
