package dev.chestlock.data;

import dev.chestlock.ChestLockPlugin;
import dev.chestlock.util.BlockUtil;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gestor de datos.
 *
 * Decisión de diseño: índice central en memoria (HashMap, búsqueda O(1)) + YAML en disco, en vez de
 * PersistentDataContainer en el bloque, porque:
 *   - las PUERTAS no son TileState, así que no admiten PDC;
 *   - un cofre doble son dos TileState distintos y habría que mantenerlos sincronizados.
 * El PDC sí se usa para identificar los ítems (Candado / Ganzúa) y las cabezas de la GUI.
 *
 * Persistencia: flag "dirty"; la serialización se hace en el hilo principal (rápida, en memoria)
 * y la escritura a disco en un hilo async.
 */
public final class LockManager {

    private final ChestLockPlugin plugin;
    private final File file;
    private final Map<String, LockData> locks = new HashMap<>();
    private boolean dirty;

    public LockManager(ChestLockPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "locks.yml");
    }

    // ------------------------------------------------------------ consulta

    /**
     * Busca el candado que protege este bloque. Recorre TODOS los bloques enlazados
     * (ambas mitades de un cofre doble / ambas puertas), por lo que da igual en qué mitad se hizo clic.
     */
    public LockData find(Block b) {
        for (Block linked : BlockUtil.linked(b)) {
            LockData d = locks.get(BlockUtil.key(linked));
            if (d != null) return d;
        }
        return null;
    }

    public boolean exists(LockData d) {
        return locks.get(d.getAnchorKey()) == d;
    }

    // ------------------------------------------------------------ modificación

    public LockData create(Block b, Player owner) {
        String key = BlockUtil.key(BlockUtil.normalize(b));
        LockData d = new LockData(owner.getUniqueId(), owner.getName(), key);
        locks.put(key, d);
        dirty = true;
        return d;
    }

    public void remove(LockData d) {
        locks.remove(d.getAnchorKey());
        dirty = true;
    }

    public void markDirty() { dirty = true; }

    /**
     * Se llama cuando se rompe un bloque protegido.
     * Devuelve true si el candado desapareció del todo.
     * Si se rompe una mitad de un cofre doble, el candado "migra" a la mitad que queda.
     */
    public boolean handleBreak(Block broken, LockData d) {
        Block base = BlockUtil.normalize(broken);
        String brokenKey = BlockUtil.key(base);

        if (Tag.DOORS.isTagged(base.getType())) { // puertas: el candado muere con la puerta
            remove(d);
            return true;
        }

        List<Block> remaining = BlockUtil.linked(base).stream()
                .filter(b -> !BlockUtil.key(b).equals(brokenKey)).toList();
        if (remaining.isEmpty()) {
            remove(d);
            return true;
        }
        if (d.getAnchorKey().equals(brokenKey)) { // re-anclar a la mitad superviviente
            locks.remove(brokenKey);
            d.setAnchorKey(BlockUtil.key(remaining.get(0)));
            locks.put(d.getAnchorKey(), d);
            dirty = true;
        }
        return false;
    }

    // ------------------------------------------------------------ persistencia

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("locks");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            try {
                String pos = s.getString("pos");
                UUID owner = UUID.fromString(s.getString("owner", ""));
                if (pos == null) continue;
                LockData d = new LockData(owner, s.getString("ownerName", "?"), pos);
                for (String t : s.getStringList("trusted")) d.trust(UUID.fromString(t));
                locks.put(pos, d);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Entrada corrupta en locks.yml: " + id);
            }
        }
        plugin.getLogger().info("Cargados " + locks.size() + " candados.");
    }

    /** Serializa en memoria. Se indexa por número (no por posición) porque los nombres de mundo pueden llevar puntos. */
    private String serialize() {
        YamlConfiguration yml = new YamlConfiguration();
        int i = 0;
        for (LockData d : locks.values()) {
            String p = "locks." + i++;
            yml.set(p + ".pos", d.getAnchorKey());
            yml.set(p + ".owner", d.getOwner().toString());
            yml.set(p + ".ownerName", d.getOwnerName());
            yml.set(p + ".trusted", d.getTrusted().stream().map(UUID::toString).toList());
        }
        return yml.saveToString();
    }

    /** Guardado async (autoguardado). */
    public void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        String data = serialize();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    /** Guardado síncrono (onDisable). */
    public void saveNow() {
        dirty = false;
        write(serialize());
    }

    private synchronized void write(String data) {
        try {
            Files.writeString(file.toPath(), data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar locks.yml", e);
        }
    }
}
