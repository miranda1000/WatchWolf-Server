package dev.watchwolf.utils;

import com.cryptomorin.xseries.XMaterial;
import dev.watchwolf.core.entities.Position;
import dev.watchwolf.core.entities.blocks.*;
import dev.watchwolf.core.entities.blocks.transformer.Transformers;
import dev.watchwolf.core.entities.entities.*;
import dev.watchwolf.core.entities.items.Item;
import dev.watchwolf.core.entities.items.ItemType;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpigotToWatchWolfTranslator {
    /*   --- BLOCK DATA TO BLOCK ---   */

    public static Block getBlock(org.bukkit.block.Block b) {
        XMaterial material = XMaterial.matchXMaterial(b.getType());
        Block watchWolfBlock = Blocks.getBlock(material.name()); // the name will always be the last version material name (the one WatchWolf is based on)

        String blockData;
        // TODO <1.13
        if (XMaterial.supports(13)) blockData = b.getBlockData().getAsString();
        else blockData = "minecraft:" + watchWolfBlock.getName();

        Map<String,String> arguments = getArgumentsAndProperty(blockData);

        return Transformers.getInstance().applyPropertiesToBlock(watchWolfBlock, arguments);
    }

    public static Set<String> getArguments(String blockData) {
        return SpigotToWatchWolfTranslator.getArgumentsAndProperty(blockData).keySet();
    }

    /*   --- INTERNAL USE ONLY ---   */

    private static final Pattern blockDataData = Pattern.compile("minecraft:([^\\[]+)\\[(.+)\\]");
    public static Map<String,String> getArgumentsAndProperty(String blockData) {
        Map<String,String> arguments = new HashMap<>();

        Matcher m = SpigotToWatchWolfTranslator.blockDataData.matcher(blockData);
        if (!m.find()) return arguments; // just a basic block

        String matName = m.group(1);
        String[] args = m.group(2).split(",");
        for (String arg : args) {
            String[] data = arg.split("=");
            // non-useful data
            if (data[0].startsWith("has_bottle_")
                    || data[0].startsWith("has_record")) continue;                          // inv dependant
            if (data[0].equals("enabled") && matName.equals("HOPPER")
                    || data[0].equals("triggered")) continue;                               // adjacent redstone dependant
            if (data[0].equals("instrument") && matName.equals("NOTE_BLOCK")) continue;  // bottom-block dependant
            if (data[0].equals("occupied")) continue;                                       // entity dependant
            if (data[0].equals("persistent")
                    || data[0].equals("unstable")
                    || data[0].equals("bloom")) continue;                                   // admin block
            if (data[0].equals("distance")) continue;                                       // block dependant (leaves relative to wood)
            if (data[0].equals("stage")) continue;                                          // same block type (aging saplings)
            if (data[0].equals("attached") || data[0].equals("disarmed")) continue;         // block dependant
            if (data[0].equals("power")) continue;                                          // block/event dependant
            if (data[0].equals("tilt")) continue;                                           // entity dependant (over the leaf)
            if (data[0].equals("can_summon") || data[0].equals("shrieking")) continue;      // admin/entity dependant (sculk shrieker)
            if (data[0].equals("bottom") && matName.equals("SCAFFOLDING")) continue;     // bottom-block dependant
            if (data[0].equals("has_book")) continue;                                       // inv dependant
            if (data[0].equals("sculk_sensor_phase")) continue;                             // admin block
            if (data[0].equals("signal_fire")) continue;                                    // bottom-block dependant
            if (data[0].equals("hatch")) continue;                                          // unable to concatenate
            if (data[0].equals("up") && matName.endsWith("_WALL")) continue;             // adjacent-block dependant
            if (data[0].equals("thickness")) continue;                                      // block dependant
            if (data[0].equals("snowy")) continue;                                          // block dependant
            if (data[0].equals("in_wall")) continue;                                        // same block
            // any block is tall by default
            //if (data[1].equals("tall")) continue;                                           // top-block dependant

            if (data[0].equals("lit") && (matName.equals("SMOKER")
                    || matName.equals("FURNACE"))) continue;                             // inv dependant
            if (data[0].equals("powered") && Arrays.asList("ACACIA_DOOR",
                    "ACACIA_FENCE_GATE", "ACACIA_TRAPDOOR", "ACTIVATOR_RAIL",
                    "BELL", "BIRCH_DOOR", "BIRCH_FENCE_GATE", "BIRCH_TRAPDOOR",
                    "CRIMSON_DOOR", "CRIMSON_FENCE_GATE", "CRIMSON_TRAPDOOR",
                    "DARK_OAK_DOOR", "DARK_OAK_FENCE_GATE", "DARK_OAK_TRAPDOOR",
                    "IRON_DOOR", "IRON_TRAPDOOR", "JUNGLE_DOOR", "JUNGLE_FENCE_GATE",
                    "JUNGLE_TRAPDOOR", "LECTERN", "MANGROVE_DOOR",
                    "MANGROVE_FENCE_GATE", "MANGROVE_TRAPDOOR", "NOTE_BLOCK",
                    "OAK_DOOR", "OAK_FENCE_GATE", "OAK_TRAPDOOR", "POWERED_RAIL",
                    "SPRUCE_DOOR", "SPRUCE_FENCE_GATE", "SPRUCE_TRAPDOOR",
                    "TRIPWIRE", "WARPED_DOOR", "WARPED_FENCE_GATE",
                    "WARPED_TRAPDOOR").contains(matName)) continue;
            if (data[0].equals("age") && Arrays.asList("CAVE_VINES", "CACTUS", "FIRE",
                    "KELP", "SUGAR_CANE", "MANGROVE_PROPAGULE", "TWISTING_VINES",
                    "WEEPING_VINES").contains(matName)) continue;

            arguments.put(data[0], data[1]);
        }

        return arguments;
    }

    public static Item getItem(ItemStack item) throws IllegalArgumentException {
        if (item == null) return null;
        XMaterial material = XMaterial.matchXMaterial(item.getType());
        return new Item(ItemType.valueOf(material.name()), (byte)item.getAmount());
    }

    public static Position getPosition(Location loc) {
        return new Position((loc.getWorld() == null) ? "" : loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    public static Entity getEntity(org.bukkit.entity.Entity e) {
        String uuid = e.getUniqueId().toString();
        Position pos = SpigotToWatchWolfTranslator.getPosition(e.getLocation());
        
        if (e.getType().name().equals("DROPPED_ITEM")) return new DroppedItem(uuid, pos, SpigotToWatchWolfTranslator.getItem(((org.bukkit.entity.Item)e).getItemStack()));
        if (e.getType().name().equals("EXPERIENCE_ORB")) return new ExperienceOrb(uuid, pos);
        if (e.getType().name().equals("AREA_EFFECT_CLOUD")) return new AreaEffectCloud(uuid, pos);
        if (e.getType().name().equals("ELDER_GUARDIAN")) return new ElderGuardian(uuid, pos);
        if (e.getType().name().equals("WITHER_SKELETON")) return new WitherSkeleton(uuid, pos);
        if (e.getType().name().equals("STRAY")) return new Stray(uuid, pos);
        if (e.getType().name().equals("EGG")) return new Egg(uuid, pos);
        if (e.getType().name().equals("LEASH_HITCH")) return new LeashHitch(uuid, pos);
        if (e.getType().name().equals("PAINTING")) return new Painting(uuid, pos);
        if (e.getType().name().equals("ARROW")) return new Arrow(uuid, pos);
        if (e.getType().name().equals("SNOWBALL")) return new Snowball(uuid, pos);
        if (e.getType().name().equals("FIREBALL")) return new Fireball(uuid, pos);
        if (e.getType().name().equals("SMALL_FIREBALL")) return new SmallFireball(uuid, pos);
        if (e.getType().name().equals("ENDER_PEARL")) return new EnderPearl(uuid, pos);
        if (e.getType().name().equals("ENDER_SIGNAL")) return new EnderSignal(uuid, pos);
        if (e.getType().name().equals("SPLASH_POTION")) return new SplashPotion(uuid, pos);
        if (e.getType().name().equals("THROWN_EXP_BOTTLE")) return new ThrownExpBottle(uuid, pos);
        if (e.getType().name().equals("ITEM_FRAME")) return new ItemFrame(uuid, pos);
        if (e.getType().name().equals("WITHER_SKULL")) return new WitherSkull(uuid, pos);
        if (e.getType().name().equals("PRIMED_TNT")) return new PrimedTnt(uuid, pos);
        if (e.getType().name().equals("FALLING_BLOCK")) return new FallingBlock(uuid, pos);
        if (e.getType().name().equals("FIREWORK")) return new Firework(uuid, pos);
        if (e.getType().name().equals("HUSK")) return new Husk(uuid, pos);
        if (e.getType().name().equals("SPECTRAL_ARROW")) return new SpectralArrow(uuid, pos);
        if (e.getType().name().equals("SHULKER_BULLET")) return new ShulkerBullet(uuid, pos);
        if (e.getType().name().equals("DRAGON_FIREBALL")) return new DragonFireball(uuid, pos);
        if (e.getType().name().equals("ZOMBIE_VILLAGER")) return new ZombieVillager(uuid, pos);
        if (e.getType().name().equals("SKELETON_HORSE")) return new SkeletonHorse(uuid, pos);
        if (e.getType().name().equals("ZOMBIE_HORSE")) return new ZombieHorse(uuid, pos);
        if (e.getType().name().equals("ARMOR_STAND")) return new ArmorStand(uuid, pos);
        if (e.getType().name().equals("DONKEY")) return new Donkey(uuid, pos);
        if (e.getType().name().equals("MULE")) return new Mule(uuid, pos);
        if (e.getType().name().equals("EVOKER_FANGS")) return new EvokerFangs(uuid, pos);
        if (e.getType().name().equals("EVOKER")) return new Evoker(uuid, pos);
        if (e.getType().name().equals("VEX")) return new Vex(uuid, pos);
        if (e.getType().name().equals("VINDICATOR")) return new Vindicator(uuid, pos);
        if (e.getType().name().equals("ILLUSIONER")) return new Illusioner(uuid, pos);
        if (e.getType().name().equals("MINECART_COMMAND")) return new MinecartCommand(uuid, pos);
        if (e.getType().name().equals("BOAT")) return new Boat(uuid, pos);
        if (e.getType().name().equals("MINECART")) return new Minecart(uuid, pos);
        if (e.getType().name().equals("MINECART_CHEST")) return new MinecartChest(uuid, pos);
        if (e.getType().name().equals("MINECART_FURNACE")) return new MinecartFurnace(uuid, pos);
        if (e.getType().name().equals("MINECART_TNT")) return new MinecartTnt(uuid, pos);
        if (e.getType().name().equals("MINECART_HOPPER")) return new MinecartHopper(uuid, pos);
        if (e.getType().name().equals("MINECART_MOB_SPAWNER")) return new MinecartMobSpawner(uuid, pos);
        if (e.getType().name().equals("CREEPER")) return new Creeper(uuid, pos);
        if (e.getType().name().equals("SKELETON")) return new Skeleton(uuid, pos);
        if (e.getType().name().equals("SPIDER")) return new Spider(uuid, pos);
        if (e.getType().name().equals("GIANT")) return new Giant(uuid, pos);
        if (e.getType().name().equals("ZOMBIE")) return new Zombie(uuid, pos);
        if (e.getType().name().equals("SLIME")) return new Slime(uuid, pos);
        if (e.getType().name().equals("GHAST")) return new Ghast(uuid, pos);
        if (e.getType().name().equals("ZOMBIFIED_PIGLIN")) return new ZombifiedPiglin(uuid, pos);
        if (e.getType().name().equals("ENDERMAN")) return new Enderman(uuid, pos);
        if (e.getType().name().equals("CAVE_SPIDER")) return new CaveSpider(uuid, pos);
        if (e.getType().name().equals("SILVERFISH")) return new Silverfish(uuid, pos);
        if (e.getType().name().equals("BLAZE")) return new Blaze(uuid, pos);
        if (e.getType().name().equals("MAGMA_CUBE")) return new MagmaCube(uuid, pos);
        if (e.getType().name().equals("ENDER_DRAGON")) return new EnderDragon(uuid, pos);
        if (e.getType().name().equals("WITHER")) return new Wither(uuid, pos);
        if (e.getType().name().equals("BAT")) return new Bat(uuid, pos);
        if (e.getType().name().equals("WITCH")) return new Witch(uuid, pos);
        if (e.getType().name().equals("ENDERMITE")) return new Endermite(uuid, pos);
        if (e.getType().name().equals("GUARDIAN")) return new Guardian(uuid, pos);
        if (e.getType().name().equals("SHULKER")) return new Shulker(uuid, pos);
        if (e.getType().name().equals("PIG")) return new Pig(uuid, pos);
        if (e.getType().name().equals("SHEEP")) return new Sheep(uuid, pos);
        if (e.getType().name().equals("COW")) return new Cow(uuid, pos);
        if (e.getType().name().equals("CHICKEN")) return new Chicken(uuid, pos);
        if (e.getType().name().equals("SQUID")) return new Squid(uuid, pos);
        if (e.getType().name().equals("WOLF")) return new Wolf(uuid, pos);
        if (e.getType().name().equals("MUSHROOM_COW")) return new MushroomCow(uuid, pos);
        if (e.getType().name().equals("SNOWMAN")) return new Snowman(uuid, pos);
        if (e.getType().name().equals("OCELOT")) return new Ocelot(uuid, pos);
        if (e.getType().name().equals("IRON_GOLEM")) return new IronGolem(uuid, pos);
        if (e.getType().name().equals("HORSE")) return new Horse(uuid, pos);
        if (e.getType().name().equals("RABBIT")) return new Rabbit(uuid, pos);
        if (e.getType().name().equals("POLAR_BEAR")) return new PolarBear(uuid, pos);
        if (e.getType().name().equals("LLAMA")) return new Llama(uuid, pos);
        if (e.getType().name().equals("LLAMA_SPIT")) return new LlamaSpit(uuid, pos);
        if (e.getType().name().equals("PARROT")) return new Parrot(uuid, pos);
        if (e.getType().name().equals("VILLAGER")) return new Villager(uuid, pos);
        if (e.getType().name().equals("ENDER_CRYSTAL")) return new EnderCrystal(uuid, pos);
        if (e.getType().name().equals("TURTLE")) return new Turtle(uuid, pos);
        if (e.getType().name().equals("PHANTOM")) return new Phantom(uuid, pos);
        if (e.getType().name().equals("TRIDENT")) return new Trident(uuid, pos);
        if (e.getType().name().equals("COD")) return new Cod(uuid, pos);
        if (e.getType().name().equals("SALMON")) return new Salmon(uuid, pos);
        if (e.getType().name().equals("PUFFERFISH")) return new Pufferfish(uuid, pos);
        if (e.getType().name().equals("TROPICAL_FISH")) return new TropicalFish(uuid, pos);
        if (e.getType().name().equals("DROWNED")) return new Drowned(uuid, pos);
        if (e.getType().name().equals("DOLPHIN")) return new Dolphin(uuid, pos);
        if (e.getType().name().equals("CAT")) return new Cat(uuid, pos);
        if (e.getType().name().equals("PANDA")) return new Panda(uuid, pos);
        if (e.getType().name().equals("PILLAGER")) return new Pillager(uuid, pos);
        if (e.getType().name().equals("RAVAGER")) return new Ravager(uuid, pos);
        if (e.getType().name().equals("TRADER_LLAMA")) return new TraderLlama(uuid, pos);
        if (e.getType().name().equals("WANDERING_TRADER")) return new WanderingTrader(uuid, pos);
        if (e.getType().name().equals("FOX")) return new Fox(uuid, pos);
        if (e.getType().name().equals("BEE")) return new Bee(uuid, pos);
        if (e.getType().name().equals("HOGLIN")) return new Hoglin(uuid, pos);
        if (e.getType().name().equals("PIGLIN")) return new Piglin(uuid, pos);
        if (e.getType().name().equals("STRIDER")) return new Strider(uuid, pos);
        if (e.getType().name().equals("ZOGLIN")) return new Zoglin(uuid, pos);
        if (e.getType().name().equals("PIGLIN_BRUTE")) return new PiglinBrute(uuid, pos);
        if (e.getType().name().equals("AXOLOTL")) return new Axolotl(uuid, pos);
        if (e.getType().name().equals("GLOW_ITEM_FRAME")) return new GlowItemFrame(uuid, pos);
        if (e.getType().name().equals("GLOW_SQUID")) return new GlowSquid(uuid, pos);
        if (e.getType().name().equals("GOAT")) return new Goat(uuid, pos);
        if (e.getType().name().equals("MARKER")) return new Marker(uuid, pos);
        if (e.getType().name().equals("ALLAY")) return new Allay(uuid, pos);
        if (e.getType().name().equals("CHEST_BOAT")) return new ChestBoat(uuid, pos);
        if (e.getType().name().equals("FROG")) return new Frog(uuid, pos);
        if (e.getType().name().equals("TADPOLE")) return new Tadpole(uuid, pos);
        if (e.getType().name().equals("WARDEN")) return new Warden(uuid, pos);
        if (e.getType().name().equals("FISHING_HOOK")) return new FishingHook(uuid, pos);
        if (e.getType().name().equals("LIGHTNING")) return new Lightning(uuid, pos);
        if (e.getType().name().equals("PLAYER")) return new Player(uuid, pos);
        return null; // unknown entity
    }
}
