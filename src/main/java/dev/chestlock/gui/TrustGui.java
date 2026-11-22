package dev.chestlock.gui;

import dev.chestlock.ChestLockPlugin;
import dev.chestlock.data.LockData;
import dev.chestlock.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * GUI de gestión de la lista blanca (54 slots):
 *   0-17   : jugadores de confianza actuales (clic = revocar)
 *   18-26  : separador
 *   27-52  : jugadores ONLINE sin acceso (clic = dar acceso)
 *   53     : quitar el candado
 *
 * Se identifica con un InventoryHolder propio (nada de comparar títulos).
 * Cada cabeza lleva el UUID en su PDC, así no dependemos del nombre.
 */
public final class TrustGui implements Listener {

    private static final int TRUSTED_END = 18;
    private static final int CANDIDATES_START = 27;
    private static final int DELETE_SLOT = 53;

    private final ChestLockPlugin plugin;
    private final NamespacedKey uuidKey;

    public TrustGui(ChestLockPlugin plugin) {
        this.plugin = plugin;
        this.uuidKey = new NamespacedKey(plugin, "gui_uuid");
    }

    private static final class Holder implements InventoryHolder {
        final LockData lock;
        Inventory inv;
        Holder(LockData lock) { this.lock = lock; }
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player p, LockData lock) {
        Holder h = new Holder(lock);
        h.inv = Bukkit.createInventory(h, 54, Component.text("Acceso al contenedor", NamedTextColor.DARK_GRAY));
        render(h);
        p.openInventory(h.inv);
    }

    private void render(Holder h) {
        Inventory inv = h.inv;
        inv.clear();
        LockData lock = h.lock;

        // Fila superior: confiados (clic = quitar)
        int slot = 0;
        for (UUID id : lock.getTrusted()) {
            if (slot >= TRUSTED_END) break;
            inv.setItem(slot++, head(Bukkit.getOfflinePlayer(id),
                    Text.plain("Clic para REVOCAR el acceso", NamedTextColor.RED)));
        }

        // Separador + etiqueta
        ItemStack pane = Text.icon(Material.GRAY_STAINED_GLASS_PANE, 1, Component.text(" "));
        for (int i = TRUSTED_END; i < CANDIDATES_START; i++) inv.setItem(i, pane);
        inv.setItem(22, Text.icon(Material.PAPER, 1,
                Text.plain("Jugadores en línea", NamedTextColor.YELLOW),
                Text.plain("Clic en una cabeza para dar acceso.", NamedTextColor.GRAY),
                Text.plain("Offline: /chestlock trust <jugador>", NamedTextColor.DARK_GRAY)));

        // Candidatos online
        slot = CANDIDATES_START;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= DELETE_SLOT) break;
            if (lock.isOwner(online) || lock.isTrusted(online)) continue;
            inv.setItem(slot++, head(online, Text.plain("Clic para DAR acceso", NamedTextColor.GREEN)));
        }

        inv.setItem(DELETE_SLOT, Text.icon(Material.BARRIER, 1,
                Text.plain("Retirar candado", NamedTextColor.RED),
                Text.plain("Desbloquea el contenedor y", NamedTextColor.GRAY),
                Text.plain("te devuelve el candado.", NamedTextColor.GRAY)));
    }

    private ItemStack head(OfflinePlayer op, Component lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        m.setOwningPlayer(op);
        String name = op.getName() != null ? op.getName() : op.getUniqueId().toString();
        m.displayName(Text.plain(name, NamedTextColor.WHITE));
        m.lore(java.util.List.of(lore));
        m.getPersistentDataContainer().set(uuidKey, PersistentDataType.STRING, op.getUniqueId().toString());
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true); // nada se puede mover dentro de esta GUI
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        // Revalidar: el lock pudo ser eliminado/roto mientras la GUI estaba abierta.
        if (!h.lock.isOwner(p) || !plugin.getLockManager().exists(h.lock)) {
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
            return;
        }

        int slot = e.getRawSlot();
        if (slot == DELETE_SLOT) {
            plugin.getLockManager().remove(h.lock);
            p.getInventory().addItem(plugin.getLockItems().createLock(1))
                    .values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
            p.sendMessage(Text.msg("Candado retirado.", NamedTextColor.YELLOW));
            Bukkit.getScheduler().runTask(plugin, (Runnable) p::closeInventory);
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String raw = clicked.getItemMeta().getPersistentDataContainer().get(uuidKey, PersistentDataType.STRING);
        if (raw == null) return;
        UUID id = UUID.fromString(raw);

        if (slot < TRUSTED_END) {
            h.lock.untrust(id);
        } else if (slot >= CANDIDATES_START && slot < DELETE_SLOT) {
            h.lock.trust(id);
        } else {
            return;
        }
        plugin.getLockManager().markDirty();
        render(h);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
