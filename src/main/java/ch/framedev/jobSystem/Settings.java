package ch.framedev.jobSystem;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** Static access to the plugin configuration (defaults come from the bundled config.yml). */
public final class Settings {

    private static JobSystem plugin;

    private Settings() {
    }

    public static void init(JobSystem instance) {
        plugin = instance;
    }

    public static FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public static double dbl(String path) {
        return cfg().getDouble(path);
    }

    public static int integer(String path) {
        return cfg().getInt(path);
    }

    public static boolean bool(String path) {
        return cfg().getBoolean(path);
    }

    public static String string(String path) {
        return cfg().getString(path, "");
    }

    public static List<String> list(String path) {
        return cfg().getStringList(path);
    }
}
