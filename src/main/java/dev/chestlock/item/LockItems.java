package dev.chestlock.item;

import dev.chestlock.ChestLockPlugin;
import dev.chestlock.util.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Fábrica e identificación de ítems personalizados.
 * Se identifican por PersistentDataContainer (no por nombre ni lore: el yunque los podría falsificar).
 */
public final class LockItems {

    private final ChestLockPlugin plugin;
    private final NamespacedKey lockKey;
    private final NamespacedKey pickKey;

    public LockItems(ChestLockPlugin plugin) {
        this.plugin = plugin;
        this.lockKey = new NamespacedKey(plugin, "is_lock");
        this.pickKey = new NamespacedKey(plugin, "is_lockpick");
    }

    @SuppressWarnings("deprecation") // setCustomModelData(Integer) sigue funcionando en 1.21.x
    public ItemStack createLock(int amount) {
        ItemStack it = new ItemStack(Material.TRIPWIRE_HOOK, amount);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.plain("Candado", NamedTextColor.GOLD));
        m.lore(List.of(
                Text.plain("Clic derecho sobre un cofre,", NamedTextColor.GRAY),
                Text.plain("barril o puerta para bloquearlo.", NamedTextColor.GRAY)));
        m.setCustomModelData(1001);
        m.getPersistentDataContainer().set(lockKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    @SuppressWarnings("deprecation")
    public ItemStack createLockpick(int amount) {
        ItemStack it = new ItemStack(Material.IRON_NUGGET, amount);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.plain("Ganzúa", NamedTextColor.AQUA));
        m.lore(List.of(
                Text.plain("Clic derecho en un contenedor ajeno", NamedTextColor.GRAY),
                Text.plain("para intentar forzar la cerradura.", NamedTextColor.GRAY),
                Text.plain("Se consume en cada intento.", NamedTextColor.DARK_GRAY)));
        m.setCustomModelData(1002);
        m.getPersistentDataContainer().set(pickKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isLock(ItemStack item) { return hasTag(item, lockKey); }
    public boolean isLockpick(ItemStack item) { return hasTag(item, pickKey); }

    private boolean hasTag(ItemStack item, NamespacedKey key) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Recetas de crafteo (se eliminan antes de añadir para soportar /reload). */
    public void registerRecipes() {
        NamespacedKey lk = new NamespacedKey(plugin, "lock_recipe");
        Bukkit.removeRecipe(lk);
        ShapedRecipe lock = new ShapedRecipe(lk, createLock(1));
        lock.shape(" I ", "IGI", " I ");
        lock.setIngredient('I', Material.IRON_INGOT);
        lock.setIngredient('G', Material.GOLD_INGOT);
        Bukkit.addRecipe(lock);

        NamespacedKey pk = new NamespacedKey(plugin, "lockpick_recipe");
        Bukkit.removeRecipe(pk);
        ShapedRecipe pick = new ShapedRecipe(pk, createLockpick(3));
        pick.shape("I", "S");
        pick.setIngredient('I', Material.IRON_INGOT);
        pick.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(pick);
    }
}
