package dev.chestlock.data;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Un candado: dueño + lista blanca propia de ESTE contenedor. */
public final class LockData {
    private final UUID owner;
    private final String ownerName;
    private final Set<UUID> trusted = new HashSet<>();
    private String anchorKey; // posición del bloque donde se registró (ver BlockUtil.key)

    public LockData(UUID owner, String ownerName, String anchorKey) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.anchorKey = anchorKey;
    }

    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public Set<UUID> getTrusted() { return trusted; }
    public String getAnchorKey() { return anchorKey; }
    public void setAnchorKey(String k) { this.anchorKey = k; }

    public boolean isOwner(Player p) { return owner.equals(p.getUniqueId()); }
    public boolean isTrusted(Player p) { return trusted.contains(p.getUniqueId()); }
    public boolean canAccess(Player p) { return isOwner(p) || isTrusted(p); }

    public void trust(UUID id) { if (!owner.equals(id)) trusted.add(id); }
    public void untrust(UUID id) { trusted.remove(id); }
}
