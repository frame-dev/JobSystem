package ch.framedev.jobSystem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Loads lang/*.yml files and resolves translated MiniMessage strings per player. */
public class Messages {

    private static final List<String> BUNDLED = List.of("en", "de", "fr", "es");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JobSystem plugin;
    private final Map<String, YamlConfiguration> languages = new TreeMap<>();
    private String defaultLanguage = "en";

    public Messages(JobSystem plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        languages.clear();
        File dir = new File(plugin.getDataFolder(), "lang");
        dir.mkdirs();
        for (String code : BUNDLED) {
            if (!new File(dir, code + ".yml").exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String code = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                InputStream bundled = plugin.getResource("lang/" + code + ".yml");
                if (bundled == null) {
                    bundled = plugin.getResource("lang/en.yml");
                }
                if (bundled != null) {
                    cfg.setDefaults(YamlConfiguration.loadConfiguration(
                            new InputStreamReader(bundled, StandardCharsets.UTF_8)));
                }
                languages.put(code, cfg);
            }
        }
        defaultLanguage = plugin.getConfig().getString("default-language", "en").toLowerCase(Locale.ROOT);
        if (!languages.containsKey(defaultLanguage)) {
            defaultLanguage = "en";
        }
    }

    public Set<String> languages() {
        return new TreeSet<>(languages.keySet());
    }

    public boolean hasLanguage(String code) {
        return languages.containsKey(code.toLowerCase(Locale.ROOT));
    }

    public String languageName(String code) {
        return raw(code, "language-name", code);
    }

    public String languageOf(CommandSender sender) {
        if (sender instanceof Player player) {
            String chosen = plugin.getFileManager().getLanguage(player);
            if (chosen != null && languages.containsKey(chosen)) {
                return chosen;
            }
            if (plugin.getConfig().getBoolean("use-client-language", true)) {
                String client = player.locale().getLanguage().toLowerCase(Locale.ROOT);
                if (languages.containsKey(client)) {
                    return client;
                }
            }
        }
        return defaultLanguage;
    }

    private String raw(String lang, String key, String fallback) {
        YamlConfiguration cfg = languages.get(lang);
        String value = cfg == null ? null : cfg.getString(key);
        if (value == null && languages.containsKey("en")) {
            value = languages.get("en").getString(key);
        }
        return value == null ? fallback : value;
    }

    public boolean has(String lang, String key) {
        YamlConfiguration cfg = languages.get(lang);
        return cfg != null && cfg.getString(key) != null;
    }

    /** Formats a message; args are alternating placeholder names and values. */
    public String format(String lang, String key, String... args) {
        String text = raw(lang, key, key);
        for (int i = 0; i + 1 < args.length; i += 2) {
            text = text.replace("{" + args[i] + "}", args[i + 1]);
        }
        return text;
    }

    public Component component(String lang, String key, String... args) {
        return MM.deserialize(format(lang, key, args));
    }

    public Component component(CommandSender sender, String key, String... args) {
        return component(languageOf(sender), key, args);
    }

    public void send(CommandSender sender, String key, String... args) {
        String lang = languageOf(sender);
        sender.sendMessage(MM.deserialize(format(lang, "prefix") + format(lang, key, args)));
    }

    /** Sends a message without the prefix (for lists and menus). */
    public void sendPlain(CommandSender sender, String key, String... args) {
        sender.sendMessage(component(sender, key, args));
    }

    public String jobName(String lang, String jobName) {
        String key = "jobs." + jobName.toLowerCase(Locale.ROOT);
        return has(lang, key) ? format(lang, key) : jobName;
    }

    public String perk(String lang, JobBenefits.Perk perk) {
        return format(lang, "perk." + perk.key(), perk.args());
    }
}
