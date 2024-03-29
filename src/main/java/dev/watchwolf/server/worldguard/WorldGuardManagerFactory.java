package dev.watchwolf.server.worldguard;

import dev.watchwolf.server.Server;
import dev.watchwolf.server.WorldGuardServerPetition;
import dev.watchwolf.entities.Version;
import org.bukkit.plugin.Plugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorldGuardManagerFactory {
    public static WorldGuardServerPetition build(Server watchwolf, Plugin plugin) {
        Pattern versionPattern = Pattern.compile("^(v?[\\d\\.]+)");
        String wgVersion = plugin.getDescription().getVersion();
        Matcher m = versionPattern.matcher(wgVersion);
        if (!m.find()) throw new IllegalArgumentException("Couldn't match the version RegEx with WorldGuard's '" + wgVersion + "'");

        Version currentVersion = new Version(m.group(1)),
                thresholdVersion = new Version("7.0.0");

        if (currentVersion.compareTo(thresholdVersion) < 0) return new API6WorldGuardManager(watchwolf, plugin);
        else return new WorldGuardManager(watchwolf, plugin);
    }
}
