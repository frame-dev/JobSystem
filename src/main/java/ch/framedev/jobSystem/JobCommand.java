package ch.framedev.jobSystem;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JobCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUBS = List.of("set", "reset", "addxp", "setlevel");
    private static final Map<String, String> PERMISSIONS = Map.of(
            "list", "jobsystem.list",
            "join", "jobsystem.join",
            "leave", "jobsystem.leave",
            "info", "jobsystem.info",
            "benefits", "jobsystem.benefits",
            "top", "jobsystem.top",
            "lang", "jobsystem.lang",
            "prestige", "jobsystem.prestige",
            "boost", "jobsystem.boost",
            "reload", "jobsystem.reload");

    private final JobSystem plugin;
    private final JobManager jobs;
    private final Messages msg;

    public JobCommand(JobSystem plugin) {
        this.plugin = plugin;
        this.jobs = plugin.getJobManager();
        this.msg = plugin.getMessages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help")) {
            help(sender);
            return true;
        }
        if (sub.equals("admin")) {
            admin(sender, args);
            return true;
        }
        String permission = PERMISSIONS.get(sub);
        if (permission == null) {
            msg.send(sender, "unknown-sub");
            return true;
        }
        if (!sender.hasPermission(permission)) {
            msg.send(sender, "no-permission", "permission", permission);
            return true;
        }
        switch (sub) {
            case "list" -> list(sender);
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "info" -> info(sender, args);
            case "benefits" -> benefits(sender, args);
            case "top" -> top(sender, args);
            case "lang" -> lang(sender, args);
            case "prestige" -> prestige(sender);
            case "boost" -> boost(sender, args);
            case "reload" -> {
                plugin.reloadAll();
                msg.send(sender, "reload.done");
            }
            default -> msg.send(sender, "unknown-sub");
        }
        return true;
    }

    private void help(CommandSender sender) {
        msg.sendPlain(sender, "help.header");
        for (Map.Entry<String, String> e : PERMISSIONS.entrySet()) {
            if (sender.hasPermission(e.getValue())) {
                msg.sendPlain(sender, "help." + e.getKey());
            }
        }
        if (ADMIN_SUBS.stream().anyMatch(s -> sender.hasPermission("jobsystem.admin." + s))) {
            msg.sendPlain(sender, "help.admin");
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        msg.send(sender, "players-only");
        return null;
    }

    private String jobName(CommandSender sender, Job job) {
        return msg.jobName(msg.languageOf(sender), job.getName());
    }

    private void list(CommandSender sender) {
        msg.sendPlain(sender, "list.header");
        for (Job job : jobs.getJobs()) {
            msg.sendPlain(sender, "list.entry", "job", jobName(sender, job));
        }
    }

    private void join(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "join.usage");
            return;
        }
        if (jobs.hasPlayerJob(player)) {
            msg.send(sender, "join.already");
            return;
        }
        Job job = findJob(args[1], msg.languageOf(sender));
        if (job == null) {
            msg.send(sender, "join.unknown");
            return;
        }
        long cooldownMs = plugin.getConfig().getLong("rejoin-cooldown-seconds", 300) * 1000L;
        long remaining = jobs.getFileManager().getLeaveTime(player) + cooldownMs - System.currentTimeMillis();
        if (remaining > 0 && !player.hasPermission("jobsystem.bypass.cooldown")) {
            msg.send(sender, "join.cooldown", "time", formatTime((remaining + 999) / 1000));
            return;
        }
        jobs.setPlayerJob(player, job);
        msg.send(sender, "join.success", "job", jobName(sender, job));
    }

    /** Finds a job by its stored name or by its translated name in the player's language. */
    private Job findJob(String input, String lang) {
        Job job = jobs.getJobByName(input);
        if (job != null) {
            return job;
        }
        for (Job candidate : jobs.getJobs()) {
            if (msg.jobName(lang, candidate.getName()).equalsIgnoreCase(input)) {
                return candidate;
            }
        }
        return null;
    }

    private void leave(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (jobs.removePlayerJob(player)) {
            jobs.getFileManager().setLeaveTime(player, System.currentTimeMillis());
            msg.send(sender, "leave.success");
        } else {
            msg.send(sender, "leave.none");
        }
    }

    private void info(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2 && !args[1].equalsIgnoreCase(sender.getName())) {
            if (!sender.hasPermission("jobsystem.info.others")) {
                msg.send(sender, "no-permission", "permission", "jobsystem.info.others");
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                msg.send(sender, "player-not-found");
                return;
            }
        } else {
            target = requirePlayer(sender);
            if (target == null) {
                return;
            }
        }
        PlayerJob pj = jobs.getPlayerJob(target);
        if (pj == null || !pj.hasJob()) {
            msg.send(sender, "info.none", "player", target.getName());
            return;
        }
        msg.sendPlain(sender, "info.job", "job", jobName(sender, pj.getJob()));
        if (Settings.bool("prestige.enabled")) {
            msg.sendPlain(sender, "info.prestige", "prestige", String.valueOf(pj.getPrestige()),
                    "max", String.valueOf(Settings.integer("prestige.max-prestige")),
                    "bonus", String.valueOf((int) Math.round(pj.getPrestige() * Settings.dbl("prestige.xp-bonus-per-prestige") * 100)));
        }
        msg.sendPlain(sender, "info.level", "level", String.valueOf(pj.getLevelNumber()),
                "max", String.valueOf(Levels.max()));
        if (pj.isMaxLevel()) {
            msg.sendPlain(sender, "info.max");
        } else {
            msg.sendPlain(sender, "info.xp", "xp", String.valueOf((int) pj.getExperience()),
                    "required", String.valueOf((int) pj.getExperienceRequiredForNextLevel()));
        }
    }

    private void benefits(CommandSender sender, String[] args) {
        String lang = msg.languageOf(sender);
        Job job;
        int current = 0;
        if (args.length >= 2) {
            job = findJob(args[1], lang);
            if (job == null) {
                msg.send(sender, "join.unknown");
                return;
            }
        } else {
            Player player = requirePlayer(sender);
            if (player == null) {
                return;
            }
            PlayerJob pj = jobs.getPlayerJob(player);
            if (pj == null || !pj.hasJob()) {
                msg.send(sender, "benefits.none");
                return;
            }
            job = pj.getJob();
            current = pj.getLevelNumber();
        }
        msg.sendPlain(sender, "benefits.header", "job", jobName(sender, job));
        for (int n = 1; n <= Levels.max(); n++) {
            msg.sendPlain(sender, "benefits.line",
                    "marker", n == current ? "<green>> </green>" : "  ",
                    "level", String.valueOf(n),
                    "perks", JobBenefits.describe(msg, lang, job.getName(), n));
        }
    }

    private void top(CommandSender sender, String[] args) {
        String lang = msg.languageOf(sender);
        Job filter = null;
        if (args.length >= 2) {
            filter = findJob(args[1], lang);
            if (filter == null) {
                msg.send(sender, "join.unknown");
                return;
            }
        }
        final Job selected = filter;
        List<Map.Entry<String, PlayerJob>> entries = new ArrayList<>(jobs.getFileManager().getAllPlayerJobs().entrySet());
        entries.removeIf(e -> selected != null
                && !e.getValue().getJob().getName().equalsIgnoreCase(selected.getName()));
        entries.sort(Comparator
                .<Map.Entry<String, PlayerJob>>comparingInt(e -> e.getValue().getPrestige()).reversed()
                .thenComparing(Comparator.<Map.Entry<String, PlayerJob>>comparingInt(e -> e.getValue().getLevelNumber()).reversed())
                .thenComparing(Comparator.<Map.Entry<String, PlayerJob>>comparingDouble(
                        e -> e.getValue().getExperience()).reversed()));
        String title = selected == null ? msg.format(lang, "top.all") : msg.jobName(lang, selected.getName());
        msg.sendPlain(sender, "top.header", "job", title);
        if (entries.isEmpty()) {
            msg.sendPlain(sender, "top.empty");
            return;
        }
        for (int i = 0; i < Math.min(Math.max(1, Settings.integer("top-size")), entries.size()); i++) {
            Map.Entry<String, PlayerJob> e = entries.get(i);
            PlayerJob pj = e.getValue();
            msg.sendPlain(sender, "top.entry",
                    "rank", String.valueOf(i + 1),
                    "player", e.getKey(),
                    "job", msg.jobName(lang, pj.getJob().getName()),
                    "level", String.valueOf(pj.getLevelNumber()),
                    "xp", String.valueOf((int) pj.getExperience()));
        }
    }

    private void prestige(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        PlayerJob pj = jobs.getPlayerJob(player);
        if (!Settings.bool("prestige.enabled")) {
            msg.send(sender, "prestige.disabled");
        } else if (pj == null || !pj.hasJob()) {
            msg.send(sender, "info.none", "player", player.getName());
        } else if (!pj.isMaxLevel()) {
            msg.send(sender, "prestige.not-max", "max", String.valueOf(Levels.max()));
        } else if (pj.getPrestige() >= Settings.integer("prestige.max-prestige")) {
            msg.send(sender, "prestige.maxed");
        } else {
            pj.prestige();
            jobs.setPlayerJob(player, pj);
            msg.send(sender, "prestige.success", "prestige", String.valueOf(pj.getPrestige()),
                    "bonus", String.valueOf((int) Math.round(pj.getPrestige() * Settings.dbl("prestige.xp-bonus-per-prestige") * 100)));
            for (String command : Settings.list("prestige.commands")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName())
                        .replace("{job}", pj.getJob().getName()).replace("{prestige}", String.valueOf(pj.getPrestige())));
            }
        }
    }

    private void boost(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (plugin.boostMultiplier() > 1.0) {
                msg.send(sender, "boost.status", "multiplier", String.valueOf(plugin.boostMultiplier()),
                        "time", formatTime(plugin.boostRemainingSeconds()));
            } else {
                msg.send(sender, "boost.none");
            }
            return;
        }
        if (args[1].equalsIgnoreCase("off")) {
            plugin.stopBoost();
            msg.send(sender, "boost.stopped");
            return;
        }
        try {
            double multiplier = Double.parseDouble(args[1]);
            long minutes = args.length > 2 ? Long.parseLong(args[2]) : 60;
            if (multiplier <= 0 || minutes <= 0) {
                throw new NumberFormatException();
            }
            plugin.startBoost(multiplier, minutes);
            for (Player online : Bukkit.getOnlinePlayers()) {
                msg.send(online, "boost.started", "multiplier", String.valueOf(multiplier), "minutes", String.valueOf(minutes));
            }
        } catch (NumberFormatException e) {
            msg.send(sender, "boost.usage");
        }
    }

    private void lang(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            String current = msg.languageOf(player);
            msg.send(sender, "lang.current", "lang", current + " (" + msg.languageName(current) + ")");
            msg.send(sender, "lang.available", "langs", String.join(", ", msg.languages()));
            return;
        }
        String code = args[1].toLowerCase(Locale.ROOT);
        if (code.equals("auto")) {
            jobs.getFileManager().setLanguage(player, null);
            msg.send(sender, "lang.auto");
        } else if (msg.hasLanguage(code)) {
            jobs.getFileManager().setLanguage(player, code);
            msg.send(sender, "lang.set", "lang", code + " (" + msg.languageName(code) + ")");
        } else {
            msg.send(sender, "lang.unknown", "lang", code);
        }
    }

    private void admin(CommandSender sender, String[] args) {
        if (args.length < 2 || !ADMIN_SUBS.contains(args[1].toLowerCase(Locale.ROOT))) {
            msg.send(sender, "admin.usage");
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        String permission = "jobsystem.admin." + sub;
        if (!sender.hasPermission(permission)) {
            msg.send(sender, "no-permission", "permission", permission);
            return;
        }
        if (args.length < 3) {
            msg.send(sender, "admin.usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            msg.send(sender, "player-not-found");
            return;
        }
        String lang = msg.languageOf(sender);
        switch (sub) {
            case "set" -> {
                Job job = args.length > 3 ? findJob(args[3], lang) : null;
                if (job == null) {
                    msg.send(sender, "admin.set-usage");
                    return;
                }
                jobs.setPlayerJob(target, job);
                msg.send(sender, "admin.set", "player", target.getName(), "job", jobName(sender, job));
            }
            case "reset" -> {
                jobs.removePlayerJob(target);
                msg.send(sender, "admin.reset", "player", target.getName());
            }
            default -> {
                PlayerJob pj = jobs.getPlayerJob(target);
                if (pj == null || !pj.hasJob()) {
                    msg.send(sender, "admin.nojob", "player", target.getName());
                    return;
                }
                if (args.length < 4) {
                    msg.send(sender, "admin.value-usage", "sub", sub);
                    return;
                }
                try {
                    if (sub.equals("addxp")) {
                        pj.addExperience(Double.parseDouble(args[3]));
                    } else {
                        int newLevel = Integer.parseInt(args[3]);
                        if (newLevel < 1 || newLevel > Levels.max()) {
                            throw new IllegalArgumentException();
                        }
                        pj.setLevel(newLevel);
                        pj.setExperience(0);
                    }
                } catch (IllegalArgumentException e) {
                    msg.send(sender, "admin.invalid", "max", String.valueOf(Levels.max()));
                    return;
                }
                jobs.setPlayerJob(target, pj);
                msg.send(sender, "admin.updated", "player", target.getName(),
                        "level", String.valueOf(pj.getLevelNumber()), "xp", String.valueOf((int) pj.getExperience()));
            }
        }
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String lang = msg.languageOf(sender);
        if (args.length == 1) {
            out.add("help");
            PERMISSIONS.forEach((sub, perm) -> {
                if (sender.hasPermission(perm)) {
                    out.add(sub);
                }
            });
            if (ADMIN_SUBS.stream().anyMatch(s -> sender.hasPermission("jobsystem.admin." + s))) {
                out.add("admin");
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "join", "benefits", "top" -> jobs.getJobs().forEach(j -> out.add(msg.jobName(lang, j.getName())));
                case "boost" -> out.add("off");
                case "lang" -> {
                    out.add("auto");
                    out.addAll(msg.languages());
                }
                case "info" -> {
                    if (sender.hasPermission("jobsystem.info.others")) {
                        Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    }
                }
                case "admin" -> ADMIN_SUBS.forEach(s -> {
                    if (sender.hasPermission("jobsystem.admin." + s)) {
                        out.add(s);
                    }
                });
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("set")) {
            jobs.getJobs().forEach(j -> out.add(msg.jobName(lang, j.getName())));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
