package dev.chestlock.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Helpers pequeños para texto (Adventure) e ítems de GUI. */
public final class Text {
    private Text() {}

    /** Texto sin cursiva (los nombres de ítem vienen en cursiva por defecto). */
    public static Component plain(String s, TextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    /** Mensaje de chat con prefijo. */
    public static Component msg(String s, TextColor c) {
        return Component.text("[ChestLock] ", NamedTextColor.DARK_GRAY).append(Component.text(s, c));
    }

    /** Crea un ítem de GUI con nombre y lore opcional. */
    public static ItemStack icon(Material m, int amount, Component name, Component... lore) {
        ItemStack it = new ItemStack(m, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name);
        if (lore.length > 0) meta.lore(List.of(lore));
        it.setItemMeta(meta);
        return it;
    }
}
