package dev.watchwolf.server;

import dev.watchwolf.core.entities.files.ConfigFile;
import dev.watchwolf.server.timings.TimingsManagerFactory;
import dev.watchwolf.server.timings.TimingsOperator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public class EnhancedInformationProvider extends ExtendedPetitionManager implements EnhancedInformationServerPetition {
    private TimingsOperator timingsOperator;

    public EnhancedInformationProvider(Server watchwolf, Plugin _watchwolf) {
        super(watchwolf, _watchwolf);

        this.timingsOperator = TimingsManagerFactory.build(watchwolf);
    }

    @Override
    public void startTimings() throws IOException {
        this.timingsOperator.startTimings();
    }

    @Override
    public ConfigFile stopTimings() throws IOException {
        String timingsPath = null;
        boolean timeoutRaised;
        do {
            try {
                timingsPath = this.timingsOperator.stopTimings();
                timeoutRaised = false;
            } catch (TimeoutException ignore) {
                this.getWatchWolf().getLogger().info("Tried to get timings report, but it was too soon. Trying again later...");
                timeoutRaised = true;
                try { Thread.sleep(30_000); } catch (InterruptedException ignored) {}
            }
        } while (timeoutRaised);

        return new ConfigFile("index.html", urlToHtml(timingsPath).getBytes(StandardCharsets.UTF_8), "");
    }

    private static String urlToHtml(String url) {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "  <head>\n" +
                "    <meta http-equiv=\"refresh\" content=\"0; url='" + url + "'\" />\n" +
                "  </head>\n" +
                "  <body>\n" +
                "    <p>You will be redirected to " + url + " soon.</p>\n" +
                "  </body>\n" +
                "</html>";
    }
}
