package dev.chestlock.util;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Door;

import java.util.ArrayList;
import java.util.List;

/**
 * Toda la geometría de "qué bloques forman UN mismo contenedor" vive aquí:
 *  - cofre doble  -> 2 bloques
 *  - puerta       -> mitad inferior + mitad superior (se normaliza a la inferior)
 *  - puerta doble -> 2 puertas contiguas
 */
public final class BlockUtil {
    private BlockUtil() {}

    private static final BlockFace[] HORIZONTAL =
            {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    public static boolean isLockable(Block b) {
        Material m = b.getType();
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL
                || Tag.DOORS.isTagged(m);
    }

    /** Las puertas se identifican siempre por su mitad INFERIOR. */
    public static Block normalize(Block b) {
        if (b.getBlockData() instanceof Door d && d.getHalf() == Bisected.Half.TOP) {
            return b.getRelative(BlockFace.DOWN);
        }
        return b;
    }

    /** Clave única y estable de posición para guardar en el YAML. */
    public static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    /**
     * Devuelve todos los bloques que forman el mismo contenedor (el primero es siempre el "base").
     * Se consultan los datos de bloque en el momento, así que es correcto aunque el cofre
     * pase de simple a doble después de colocar el candado.
     */
    public static List<Block> linked(Block b) {
        Block base = normalize(b);
        List<Block> list = new ArrayList<>(2);
        list.add(base);

        BlockData data = base.getBlockData();
        if (data instanceof Chest chest && chest.getType() != Chest.Type.SINGLE) {
            Block other = findChestPartner(base, chest);
            if (other != null) list.add(other);
        } else if (data instanceof Door door) {
            Block other = findDoorPartner(base, door);
            if (other != null) list.add(other);
        }
        return list;
    }

    /**
     * Detección de cofre doble SIN fórmulas de orientación LEFT/RIGHT (que son fáciles de invertir):
     * el compañero es el vecino perpendicular al "facing" con mismo material, mismo facing y
     * tipo complementario (LEFT <-> RIGHT).
     */
    private static Block findChestPartner(Block base, Chest chest) {
        BlockFace facing = chest.getFacing();
        for (BlockFace f : HORIZONTAL) {
            if (f == facing || f == facing.getOppositeFace()) continue; // solo lados
            Block n = base.getRelative(f);
            if (n.getType() == base.getType()
                    && n.getBlockData() instanceof Chest oc
                    && oc.getFacing() == facing
                    && oc.getType() != Chest.Type.SINGLE
                    && oc.getType() != chest.getType()) {
                return n;
            }
        }
        return null;
    }

    /** Puerta doble: vecino lateral, mismo facing, bisagra opuesta, mitad inferior. */
    private static Block findDoorPartner(Block base, Door door) {
        BlockFace facing = door.getFacing();
        for (BlockFace f : HORIZONTAL) {
            if (f == facing || f == facing.getOppositeFace()) continue;
            Block n = base.getRelative(f);
            if (n.getType() == base.getType()
                    && n.getBlockData() instanceof Door od
                    && od.getHalf() == Bisected.Half.BOTTOM
                    && od.getFacing() == facing
                    && od.getHinge() != door.getHinge()) {
                return n;
            }
        }
        return null;
    }
}
