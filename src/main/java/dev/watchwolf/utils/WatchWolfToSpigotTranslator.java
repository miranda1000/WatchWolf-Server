package dev.watchwolf.utils;

import com.cryptomorin.xseries.XBlock;
import com.cryptomorin.xseries.XMaterial;
import dev.watchwolf.core.entities.Position;
import dev.watchwolf.core.entities.blocks.Block;
import dev.watchwolf.core.entities.blocks.transformer.Transformers;
import dev.watchwolf.core.entities.entities.DroppedItem;
import dev.watchwolf.core.entities.items.Item;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/**
 * We need to convert WatchWolf blocks into Spigot's block
 */
public class WatchWolfToSpigotTranslator {
    public static void setBlockData(final Block watchWolfBlock, final org.bukkit.block.Block target) throws IllegalArgumentException {
        XMaterial spigotMaterial = XMaterial.matchXMaterial(watchWolfBlock.getName())
                .orElseThrow(() -> new IllegalArgumentException("Material '" + watchWolfBlock.getName() + "' not found"));

        // TODO to add blockData in <1.13 I'd have to work with XBlock class, but instead of `Block` constructor with a `BlockData` string
        //      the constructor should call to XMaterial to get the material and then change the blockData
        if (XMaterial.supports(13)) {
            // we have blockData
            String spigotBlock = Bukkit.createBlockData(spigotMaterial.parseMaterial()).getAsString();
            String blockData = Transformers.getInstance().modifyAllBlockData(watchWolfBlock, spigotBlock); // set the properties

            target.setBlockData(Bukkit.createBlockData(blockData));
        }
        else XBlock.setType(target, spigotMaterial); // we just know the type
    }

    private static String setBlockDataProperty(String blockData, String property, String value) {
        return blockData.replaceAll("(?<=[,\\[])" + property + "=[^,\\]]+", property + "=" + value);
    }

    public static Location getLocation(Position pos) {
        return new Location(Bukkit.getWorld(pos.getWorld()), pos.getX(), pos.getY(), pos.getZ());
    }

    public static ItemStack getItem(final Item item) throws IllegalArgumentException {
        XMaterial spigotMaterial = XMaterial.matchXMaterial(item.getType().name())
                .orElseThrow(() -> new IllegalArgumentException("Material '" + item.getType().name() + "' not found"));
        return new ItemStack(spigotMaterial.parseMaterial(), item.getAmount());
    }

    public static EntityType getType(dev.watchwolf.core.entities.entities.EntityType type) {
        return EntityType.valueOf(type.name());
    }

    public static Entity spawnEntity(dev.watchwolf.core.entities.entities.Entity entity) throws IllegalArgumentException {
        World w = Bukkit.getWorld(entity.getPosition().getWorld());
        if (w == null) throw new IllegalArgumentException("World '" + entity.getPosition().getWorld() + "' not found");

        Location location = WatchWolfToSpigotTranslator.getLocation(entity.getPosition());
        EntityType entityType = WatchWolfToSpigotTranslator.getType(entity.getType());
        Entity spawned;
        switch (entityType) {
            case DROPPED_ITEM:
                spawned = w.dropItem(location, WatchWolfToSpigotTranslator.getItem(((DroppedItem)entity).getItem()));

                // TODO other special cases

            default:
                spawned = w.spawnEntity(location, entityType);
                // TODO apply other properties
        }

        return spawned;
    }
}
