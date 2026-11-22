package dev.chestlock.gui;

import dev.chestlock.ChestLockPlugin;
import dev.chestlock.data.LockData;
import dev.chestlock.util.BlockUtil;
import dev.chestlock.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Minijuego de ganzúa: "timing de pines".
 *
 *  Fila central (slots 9-17): un cursor naranja rebota de lado a lado. Hay UNA casilla verde (el pin).
 *  El jugador pulsa FORZAR (slot 22) justo cuando el cursor está sobre la casilla verde.
 *    - Acierto: se baja un pin; el siguiente pin es más rápido y cambia de sitio.
 *    - Fallo:   se pierde un intento.
 *  Ganas al bajar todos los pines; pierdes si agotas los intentos o cierras la GUI.
 *
 * La ganzúa se consume al EMPEZAR (un intento = una ganzúa, gane o pierda).
 * Se avisa al dueño en ambos casos.
 */
public final class LockpickGame implements Listener {

    private static final int PINS = 3;          // aciertos necesarios
    private static final int LIVES = 2;         // fallos tolerados
    private static final int ROW_START = 9;     // fila del cursor: slots 9..17
    private static final int ROW_LEN = 9;
    private static final int BUTTON_SLOT = 22;
    private static final int PINS_SLOT = 3;
    private static final int LIVES_SLOT = 5;

    private final ChestLockPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Random random = new Random();

    public LockpickGame(ChestLockPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ API

    public void start(Player p, Block block, LockData lock, ItemStack pickInHand) {
        if (sessions.containsKey(p.getUniqueId())) return;

        if (p.getGameMode() != GameMode.CREATIVE) pickInHand.setAmount(pickInHand.getAmount() - 1);

        Session s = new Session(p, block, lock);
        sessions.put(p.getUniqueId(), s);
        p.openInventory(s.inv);
        s.startTimer();
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1f, 1.6f);
    }

    /** Cierra sesiones limpiamente al apagar el plugin (sin penalizar ni alertar). */
    public void shutdown() {
        for (Session s : new ArrayList<>(sessions.values())) {
            s.ended = true;
            if (s.task != null) s.task.cancel();
            s.player.closeInventory();
        }
        sessions.clear();
    }

    // ------------------------------------------------------------------ sesión

    /** Estado de UNA partida; actúa como InventoryHolder para identificar su GUI. */
    private final class Session implements InventoryHolder {
        final Player player;
        final Block block;
        final LockData lock;
        final Inventory inv;

        int cursor = 0, dir = 1, target;
        int pinsLeft = PINS, livesLeft = LIVES;
        int tickCounter = 0;
        boolean ended = false;
        BukkitTask task;

        Session(Player player, Block block, LockData lock) {
            this.player = player;
            this.block = block;
            this.lock = lock;
            this.inv = Bukkit.createInventory(this, 27, Component.text("Forzando cerradura...", NamedTextColor.DARK_GRAY));

            ItemStack filler = Text.icon(Material.BLACK_STAINED_GLASS_PANE, 1, Component.text(" "));
            for (int i = 0; i < 27; i++) inv.setItem(i, filler);
            inv.setItem(BUTTON_SLOT, Text.icon(Material.TRIPWIRE_HOOK, 1,
                    Text.plain("¡FORZAR!", NamedTextColor.GOLD),
                    Text.plain("Púlsalo cuando el cursor esté", NamedTextColor.GRAY),
                    Text.plain("sobre la casilla verde.", NamedTextColor.GRAY)));
            this.target = randomTarget(-1);
            render();
        }

        @Override public Inventory getInventory() { return inv; }

        void startTimer() {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }

        /** Cada pin resuelto acelera el cursor: 5 -> 4 -> 3 ticks por casilla. */
        private int stepTicks() {
            return Math.max(2, 5 - (PINS - pinsLeft));
        }

        private void tick() {
            if (ended) return;
            if (!player.isOnline()) { finish(false); return; }
            if (++tickCounter < stepTicks()) return;
            tickCounter = 0;

            cursor += dir;
            if (cursor >= ROW_LEN - 1) dir = -1;   // rebote derecho
            else if (cursor <= 0) dir = 1;         // rebote izquierdo
            render();
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 1.5f);
        }

        /** Se llama al pulsar FORZAR. */
        void attempt() {
            if (ended) return;
            if (cursor == target) {
                pinsLeft--;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f + 0.2f * (PINS - pinsLeft));
                if (pinsLeft <= 0) { finish(true); return; }
                target = randomTarget(target);
                tickCounter = 0;
            } else {
                livesLeft--;
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                if (livesLeft <= 0) { finish(false); return; }
            }
            render();
        }

        /** Nueva casilla objetivo distinta de la anterior. */
        private int randomTarget(int previous) {
            int t;
            do { t = random.nextInt(ROW_LEN); } while (t == previous);
            return t;
        }

        void render() {
            for (int i = 0; i < ROW_LEN; i++) {
                ItemStack it;
                if (i == cursor && i == target) {
                    it = Text.icon(Material.EMERALD_BLOCK, 1, Text.plain("¡AHORA!", NamedTextColor.GREEN));
                } else if (i == cursor) {
                    it = Text.icon(Material.ORANGE_STAINED_GLASS_PANE, 1, Text.plain("Ganzúa", NamedTextColor.GOLD));
                } else if (i == target) {
                    it = Text.icon(Material.LIME_STAINED_GLASS_PANE, 1, Text.plain("Pin", NamedTextColor.GREEN));
                } else {
                    it = Text.icon(Material.GRAY_STAINED_GLASS_PANE, 1, Component.text(" "));
                }
                inv.setItem(ROW_START + i, it);
            }
            inv.setItem(PINS_SLOT, Text.icon(Material.TRIPWIRE_HOOK, pinsLeft,
                    Text.plain("Pines restantes: " + pinsLeft, NamedTextColor.YELLOW)));
            inv.setItem(LIVES_SLOT, Text.icon(Material.REDSTONE, livesLeft,
                    Text.plain("Fallos permitidos: " + livesLeft, NamedTextColor.RED)));
        }

        /** Termina la partida (idempotente). */
        void finish(boolean success) {
            if (ended) return;
            ended = true;
            if (task != null) task.cancel();
            sessions.remove(player.getUniqueId());

            int x = block.getX(), y = block.getY(), z = block.getZ();
            Player owner = Bukkit.getPlayer(lock.getOwner());

            if (success) {
                player.sendMessage(Text.msg("¡Cerradura forzada!", NamedTextColor.GREEN));
                if (owner != null) owner.sendMessage(Text.msg("¡ALERTA! " + player.getName()
                        + " ha forzado tu contenedor en " + x + ", " + y + ", " + z + ".", NamedTextColor.RED));
            } else {
                player.sendMessage(Text.msg("La ganzúa se ha roto. La cerradura sigue intacta.", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                if (owner != null) owner.sendMessage(Text.msg("Aviso: " + player.getName()
                        + " intentó forzar tu contenedor en " + x + ", " + y + ", " + z + ".", NamedTextColor.GOLD));
            }

            // Cerrar/abrir inventarios dentro de un evento de click es delicado: diferimos 1 tick.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() == this) player.closeInventory();
                if (success && player.isOnline()) openTarget();
            });
        }

        /** Efecto del éxito: abrir el inventario del contenedor, o la puerta unos segundos. */
        private void openTarget() {
            Block base = BlockUtil.normalize(block);
            if (Tag.DOORS.isTagged(base.getType())) {
                List<Block> doors = BlockUtil.linked(base); // incluye puerta doble
                setDoors(doors, true);
                Bukkit.getScheduler().runTaskLater(plugin, () -> setDoors(doors, false), 60L);
            } else if (base.getState() instanceof Container c) {
                // Para un cofre doble, el estado devuelve el inventario combinado (54 slots).
                player.openInventory(c.getInventory());
            }
        }

        private void setDoors(List<Block> doors, boolean open) {
            for (Block b : doors) {
                if (b.getBlockData() instanceof Door d) {
                    d.setOpen(open);
                    b.setBlockData(d);
                }
            }
        }
    }

    // ------------------------------------------------------------------ eventos de GUI

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Session s)) return;
        e.setCancelled(true); // incluye shift-click, teclas numéricas, etc.
        if (e.getRawSlot() == BUTTON_SLOT) s.attempt();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Session) e.setCancelled(true);
    }

    /** Cerrar la GUI a mitad de partida = fallo (evita "cancelar para reintentar gratis"). */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Session s && !s.ended) s.finish(false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null) s.finish(false);
    }
}
