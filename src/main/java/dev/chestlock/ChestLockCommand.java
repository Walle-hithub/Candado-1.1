package dev.chestlock;

import dev.chestlock.data.LockData;
import dev.chestlock.util.BlockUtil;
import dev.chestlock.util.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * /chestlock give <lock|lockpick> [cantidad]   (admin)
 * /chestlock manage                            (abre la GUI del contenedor que miras)
 * /chestlock trust|untrust <jugador>           (también offline, si ha entrado alguna vez)
 * /chestlock info
 */
public final class ChestLockCommand implements TabExecutor {

    private final ChestLockPlugin plugin;

    public ChestLockCommand(ChestLockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }
        if (args.length == 0) {
            p.sendMessage(Text.msg("Uso: /chestlock <give|manage|trust|untrust|info>", NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(p, args);
            case "manage" -> {
                LockData l = targetLock(p, true);
                if (l != null) plugin.getTrustGui().open(p, l);
            }
            case "trust", "untrust" -> {
                if (args.length < 2) {
                    p.sendMessage(Text.msg("Indica un jugador.", NamedTextColor.RED));
                    return true;
                }
                LockData l = targetLock(p, true);
                if (l == null) return true;
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[1]); // sin consultas bloqueantes a Mojang
                if (t == null) {
                    p.sendMessage(Text.msg("Jugador desconocido (debe haberse conectado antes).", NamedTextColor.RED));
                    return true;
                }
                if (args[0].equalsIgnoreCase("trust")) l.trust(t.getUniqueId());
                else l.untrust(t.getUniqueId());
                plugin.getLockManager().markDirty();
                p.sendMessage(Text.msg("Hecho: " + args[0] + " " + args[1], NamedTextColor.GREEN));
            }
            case "info" -> {
                LockData l = targetLock(p, false);
                if (l == null) return true;
                p.sendMessage(Text.msg("Dueño: " + l.getOwnerName() + " | Confiados: " + l.getTrusted().size(),
                        NamedTextColor.AQUA));
            }
            default -> p.sendMessage(Text.msg("Subcomando desconocido.", NamedTextColor.RED));
        }
        return true;
    }

    private void give(Player p, String[] args) {
        if (!p.hasPermission("chestlock.admin")) {
            p.sendMessage(Text.msg("Sin permiso.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Text.msg("Uso: /chestlock give <lock|lockpick> [cantidad]", NamedTextColor.YELLOW));
            return;
        }
        int amount = 1;
        if (args.length > 2) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[2]))); }
            catch (NumberFormatException ignored) { }
        }
        ItemStack item = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "lock" -> plugin.getLockItems().createLock(amount);
            case "lockpick" -> plugin.getLockItems().createLockpick(amount);
            default -> null;
        };
        if (item == null) {
            p.sendMessage(Text.msg("Elige lock o lockpick.", NamedTextColor.RED));
            return;
        }
        p.getInventory().addItem(item).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }

    /** Contenedor que mira el jugador (alcance 5) + validaciones. */
    private LockData targetLock(Player p, boolean ownerOnly) {
        Block b = p.getTargetBlockExact(5);
        if (b == null || !BlockUtil.isLockable(b)) {
            p.sendMessage(Text.msg("Mira un contenedor bloqueado.", NamedTextColor.RED));
            return null;
        }
        LockData l = plugin.getLockManager().find(b);
        if (l == null) {
            p.sendMessage(Text.msg("Ese contenedor no está bloqueado.", NamedTextColor.RED));
            return null;
        }
        if (ownerOnly && !l.isOwner(p) && !p.hasPermission("chestlock.admin")) {
            p.sendMessage(Text.msg("No eres el dueño.", NamedTextColor.RED));
            return null;
        }
        return l;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return List.of("give", "manage", "trust", "untrust", "info");
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) return List.of("lock", "lockpick");
        return List.of();
    }
}
