package dev.watchwolf.server.worldguard;

import dev.watchwolf.core.entities.Position;
import dev.watchwolf.server.ExtendedPetitionManager;
import dev.watchwolf.server.Server;
import dev.watchwolf.server.WorldGuardServerPetition;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class API6WorldGuardManager extends ExtendedPetitionManager implements WorldGuardServerPetition {
    public API6WorldGuardManager(Server watchwolf, Plugin plugin) {
        super(watchwolf, plugin);
    }

    /**
     * Get the WorldGuard manager of that Bukkit world
     * @param world World to get the manager
     * @return World manager (com.sk89q.worldguard.protection.managers.RegionManager)
     */
    private Object getWorldManager(World world) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, IllegalArgumentException {
        Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
        Class<?> regionContainerClass = Class.forName("com.sk89q.worldguard.bukkit.RegionContainer");
        Method getRegionContainer = worldGuardClass.getDeclaredMethod("getRegionContainer");

        Object container = getRegionContainer.invoke(
                worldGuardClass.cast(this.getPlugin())
        );

        Method getContainerRegion = regionContainerClass.getDeclaredMethod("get", World.class);
        return getContainerRegion.invoke(regionContainerClass.cast(container), world);
    }

    @Override
    public void createRegion(String s, Position position, Position position1) throws IOException {
        if (!position.getWorld().equals(position1.getWorld())) throw new IllegalArgumentException("A region can only be in one world!");

        World positionWorld = Bukkit.getWorld(position.getWorld());
        if (positionWorld == null) throw new IllegalArgumentException("The world specified doesn't exists.");

        try {
            Class<?> vectorClass = Class.forName("com.sk89q.worldedit.BlockVector");
            Constructor<?> vectorBuilder = vectorClass.getConstructor(double.class, double.class, double.class);
            Class<?> protectedRegionClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion");
            Constructor<?> protectedRegionBuilder = protectedRegionClass.getConstructor(String.class, vectorClass, vectorClass);
            Class<?> worldManagerClass = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager");
            Method addRegion = worldManagerClass.getDeclaredMethod("addRegion", Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion"));

            Object region = protectedRegionBuilder.newInstance(s,
                    vectorClass.cast(vectorBuilder.newInstance(position.getX(), position.getY(), position.getZ())),
                    vectorClass.cast(vectorBuilder.newInstance(position1.getX(), position1.getY(), position1.getZ())));
            addRegion.invoke(worldManagerClass.cast(this.getWorldManager(positionWorld)), protectedRegionClass.cast(region));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String[] getRegions() throws IOException {
        ArrayList<String> regions = new ArrayList<>();

        try {
            Class<?> worldManagerClass = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager");
            Method getRegions = worldManagerClass.getDeclaredMethod("getRegions");

            for (World world : Bukkit.getWorlds()) {
                regions.addAll(
                        ((Map<String, Object>) getRegions.invoke(worldManagerClass.cast(this.getWorldManager(world))))
                                .keySet()
                );
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        return regions.toArray(new String[0]);
    }

    @Override
    public String[] getRegions(Position position) throws IOException {
        World positionWorld = Bukkit.getWorld(position.getWorld());
        if (positionWorld == null) throw new IllegalArgumentException("The world specified doesn't exists.");

        try {
            Class<?> vectorClass = Class.forName("com.sk89q.worldedit.Vector");
            Constructor<?> vectorBuilder = vectorClass.getConstructor(double.class, double.class, double.class);
            Class<?> worldManagerClass = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager");
            Class<?> applicableRegionSetClass = Class.forName("com.sk89q.worldguard.protection.ApplicableRegionSet");
            Method getRegions = applicableRegionSetClass.getDeclaredMethod("getRegions");
            final Class<?> protectedRegionClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            final Method getProtectedRegionId = protectedRegionClass.getDeclaredMethod("getId");

            Object worldManager = this.getWorldManager(positionWorld);
            Method getApplicableRegions = worldManagerClass.getDeclaredMethod("getApplicableRegions", vectorClass);
            Object positionVector = vectorBuilder.newInstance(position.getX(), position.getY(), position.getZ());
            return ((Set<Object>)getRegions.invoke(
                    applicableRegionSetClass.cast(
                            getApplicableRegions.invoke(worldManagerClass.cast(worldManager), vectorClass.cast(positionVector))
                    )
            )).stream()
                    .map(r -> {
                        try {
                            return (String)getProtectedRegionId.invoke(protectedRegionClass.cast(r));
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    })
                    .collect(Collectors.toList()).toArray(new String[0]);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
