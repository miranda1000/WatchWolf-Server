package dev.watchwolf.server;

import com.cryptomorin.xseries.XMaterial;
import dev.watchwolf.entities.Container;
import dev.watchwolf.entities.Difficulty;
import dev.watchwolf.entities.Position;
import dev.watchwolf.entities.blocks.Block;
import dev.watchwolf.entities.entities.Chicken;
import dev.watchwolf.entities.entities.Entity;
import dev.watchwolf.entities.files.ConfigFile;
import dev.watchwolf.entities.items.Item;
import dev.watchwolf.server.events.invincibility.OnPlayerDamage;
import dev.watchwolf.server.events.whitelist.OfflineSpigotWhitelistResolver;
import dev.watchwolf.server.events.whitelist.ServerWhitelistResolver;
import dev.watchwolf.server.worldguard.UnimplementedWorldGuardManager;
import dev.watchwolf.server.worldguard.WorldGuardManagerFactory;
import dev.watchwolf.utils.SpigotToWatchWolfTranslator;
import dev.watchwolf.utils.WatchWolfToSpigotTranslator;
import org.apache.logging.log4j.LogManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server extends JavaPlugin implements ServerPetition, SequentialExecutor {
    private ServerWhitelistResolver whitelistResolver;
    private OnPlayerDamage playerDamageResolver;

    private ServerConnector connector;

    private final Queue<SequentialExecutor.ThrowableRunnable> futureExecute = new LinkedList<>();
    private int futureExecuteTaskID;
    private CommandRunner commandRunner;

    private WorldGuardServerPetition worldGuardManager;

    private EnhancedInformationServerPetition enhancedInformationProvider;

    @Override
    public void onEnable() {
        this.whitelistResolver = new OfflineSpigotWhitelistResolver(this);
        this.playerDamageResolver = new OnPlayerDamage(this);

        // get the logger
        final org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
        LogAppender appender = new LogAppender(this);
        logger.addAppender(appender);
        this.commandRunner = appender;

        getLogger().info("Loading socket data...");

        // read data
        final FileConfiguration config = this.getConfig();
        final String ip = config.getString("target-ip");
        final int port = config.getInt("use-port");

        // extended petitions managers
        PluginManager pm = this.getServer().getPluginManager();
        Plugin worldguard = pm.getPlugin("WorldGuard");
        if (worldguard != null) this.worldGuardManager = WorldGuardManagerFactory.build(this, worldguard);
        else this.worldGuardManager = new UnimplementedWorldGuardManager();

        this.enhancedInformationProvider = new EnhancedInformationProvider(this, this);

        // execute sequentially the orders one by one (and letting the server update)
        this.futureExecuteTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            synchronized (this.futureExecute) {
                if (this.futureExecute.isEmpty()) return;

                SequentialExecutor.ThrowableRunnable run = this.futureExecute.remove();
                try {
                    run.run();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }, 0L, 1);

        try {
            getLogger().info("Hosting on " + port + " (for " + ip + ")");
            this.connector = new ServerConnector(ip, port, this, this);

            new Thread(this.connector).start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // TODO events?
    }

    @Override
    public void onDisable() {
        this.connector.close();
        Bukkit.getScheduler().cancelTask(this.futureExecuteTaskID);
    }

    @Override
    public void run(SequentialExecutor.ThrowableRunnable run) {
        synchronized (this.futureExecute) {
            this.futureExecute.add(run);
        }
    }

    private String runSpigotCommand(String cmd) {
        return this.commandRunner.runCommand(cmd);
    }

    private static boolean isUsername(String nick) {
        Pattern pattern = Pattern.compile("^\\w{3,16}$"); // https://help.minecraft.net/hc/en-us/articles/4408950195341-Minecraft-Java-Edition-Username-VS-Gamertag-FAQ
        Matcher m = pattern.matcher(nick);
        return m.matches();
    }

    @Override
    public void opPlayer(String nick) {
        if (!Server.isUsername(nick)) return;
        getLogger().info("OP player (" + nick + ") request");
        this.runSpigotCommand("op " + nick);
    }

    @Override
    public void whitelistPlayer(String nick) {
        if (!Server.isUsername(nick)) return;
        getLogger().info("Whitelist player (" + nick + ") request");
        this.whitelistResolver.addToWhitelist(nick);
    }

    @Override
    public Position getPlayerPosition(String username) throws IOException {
        getLogger().info("Get " + username + "'s position request");
        Player p = Bukkit.getPlayer(username);
        if (p == null) return null;
        Location loc = p.getLocation();
        return new Position(p.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    @Override
    public float getPlayerPitch(String username) throws IOException {
        getLogger().info("Get " + username + "'s pitch request");
        Player p = Bukkit.getPlayer(username);
        if (p == null) return 0.0f;
        return p.getLocation().getPitch();
    }

    @Override
    public float getPlayerYaw(String username) throws IOException {
        getLogger().info("Get " + username + "'s yaw request");
        Player p = Bukkit.getPlayer(username);
        if (p == null) return 0.0f;
        float yaw = p.getLocation().getYaw();
        if (yaw > 180f) yaw -= 360f; // Minecraft does this
        return yaw;
    }

    @Override
    public void giveItem(String s, Item item) throws IOException {
        getLogger().info("Give " + item.toString() + " to " + s + " request");
        Player p = Bukkit.getPlayer(s);
        if (p == null) {
            getLogger().info("Player " + s + " not found");
            return;
        }
        p.getInventory().addItem(WatchWolfToSpigotTranslator.getItem(item));
    }

    @Override
    public void tp(String username, Position position) throws IOException {
        getLogger().info("Tp " + username + " to " + position + " request");
        Player p = Bukkit.getPlayer(username);
        if (p == null) return;
        p.teleport(WatchWolfToSpigotTranslator.getLocation(position));
    }

    @Override
    public Container getInventory(String username) throws IOException {
        getLogger().info("Get " + username + "'s inventory request");
        Player p = Bukkit.getPlayer(username);
        if (p == null) return null;
        ItemStack []r = p.getInventory().getContents();
        Item []items = new Item[r.length];
        for (int n = 0; n < items.length; n++) items[n] = SpigotToWatchWolfTranslator.getItem(r[n]);
        return new Container(items);
    }

    @Override
    public String[] getPlayers() throws IOException {
        getLogger().info("Get players request");
        return Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).toArray(String[]::new);
    }

    @Override
    public void stopServer(ServerStopNotifier onServerStop) {
        getLogger().info("Stop server request");
        this.runSpigotCommand("stop");
    }

    @Override
    public void setBlock(Position position, Block block) throws IOException {
        getLogger().info("Set block " + block.toString() + " at " + position.toString() + " request");
        WatchWolfToSpigotTranslator.setBlockData(block, WatchWolfToSpigotTranslator.getLocation(position).getBlock());
    }

    @Override
    public Block getBlock(Position position) throws IOException {
        Location loc = WatchWolfToSpigotTranslator.getLocation(position);
        Block found = SpigotToWatchWolfTranslator.getBlock(loc.getBlock());
        getLogger().info("Get block at " + position.toString() + " request; Found " + found.toString());
        return found;
    }

    @Override
    public String runCommand(String cmd) throws IOException {
        getLogger().info("Run " + cmd + " request");
        String r = this.runSpigotCommand(cmd);
        if (!r.isEmpty()) getLogger().info("In response of '" + cmd + "', got: " + r);
        return r;
    }

    @Override
    public Entity[] getEntities(Position position, double radius) throws IOException {
        getLogger().info("Get entities request");
        return this.getEntitiesByRadius(position, radius).stream().map(e -> SpigotToWatchWolfTranslator.getEntity(e))
                .filter(Objects::nonNull).toArray(Entity[]::new);
    }

    @Override
    public Entity spawnEntity(Entity entity) throws IOException {
        getLogger().info("Spawn " + entity.toString() + " request");
        try {
            return SpigotToWatchWolfTranslator.getEntity(WatchWolfToSpigotTranslator.spawnEntity(entity));
        } catch (Exception ex) {
            getLogger().warning(ex.getMessage());
        }
        return null;
    }

    @Override
    public Entity getEntity(String uuid) throws IOException {
        org.bukkit.entity.Entity e = null;

        if (XMaterial.supports(12)) e = Bukkit.getEntity(UUID.fromString(uuid)); // we have Bukkit.getEntity
        else {
            // @author https://www.spigotmc.org/threads/get-entity-from-uuid.65446/
            for (World world : Bukkit.getWorlds()) {
                for (org.bukkit.entity.Entity entity : world.getEntities()) {
                    if (entity.getUniqueId().equals(uuid)) {
                        e = entity;
                        break;
                    }
                }

                if (e != null) break;
            }
        }


        if (e == null) return new Chicken("-1", new Position("",0,0,0)); // dummy entity
        return SpigotToWatchWolfTranslator.getEntity(e);
    }

    @Override
    public void setDifficulty(Difficulty difficulty) throws IOException {
        getLogger().info("Changing difficulty to " + difficulty.name() + "...");
        for (World w : this.getServer().getWorlds()) w.setDifficulty(org.bukkit.Difficulty.valueOf(difficulty.name()));
    }

    @Override
    public void setInvincibleMode(boolean b) throws IOException {
        getLogger().info("Invincibility mode set to " + (b ? "true" : "false"));
        this.playerDamageResolver.setInvincible(b);
    }

    public List<org.bukkit.entity.Entity> getEntitiesByRadius(Position position, double radius) {
        ArrayList<org.bukkit.entity.Entity> r = new ArrayList<>();

        World world = Bukkit.getWorld(position.getWorld());
        if (world == null) return r; // no entities

        for (org.bukkit.entity.Entity e : world.getEntities()) {
            if (Math.abs(e.getLocation().getX() - position.getX()) <= radius
                    && Math.abs(e.getLocation().getZ() - position.getZ()) <= radius) r.add(e); // inside the radius
        }

        return r;
    }

    @Override
    public String getVersion() throws IOException {
        return getClass().getPackage().getImplementationVersion(); // Maven version
    }

    @Override
    public void synchronize() throws IOException {}

    @Override
    public void createRegion(String s, Position firstPosition, Position secondPosition) throws IOException {
        getLogger().info("Requesting a new WorldGuard region (" + s + "), in " + firstPosition.toString() + ", " + secondPosition.toString());
        this.worldGuardManager.createRegion(s, firstPosition, secondPosition);
    }

    @Override
    public String[] getRegions() throws IOException {
        String[] r = this.worldGuardManager.getRegions();
        getLogger().info("WorldGuard list request, got " + Arrays.asList(r).toString());
        return r;
    }

    @Override
    public String[] getRegions(Position position) throws IOException {
        String[] r = this.worldGuardManager.getRegions(position);
        getLogger().info("WorldGuard list at pos=" + position.toString() + " request, got " + Arrays.asList(r).toString());
        return r;
    }

    @Override
    public void startTimings() throws IOException {
        getLogger().info("Start timings request");
        this.enhancedInformationProvider.startTimings();
    }

    @Override
    public ConfigFile stopTimings() throws IOException {
        getLogger().info("Stop timings request");
        return this.enhancedInformationProvider.stopTimings();
    }
}
