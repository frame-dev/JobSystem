package ch.framedev.jobSystem;

import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

public final class JobSystem extends JavaPlugin {

    private FileManager fileManager;
    private JobManager jobManager;
    private Messages messages;
    private double boostMultiplier = 1.0;
    private long boostUntil;

    @Override
    public void onEnable() {
        ConfigurationSerialization.registerClass(Job.class);
        ConfigurationSerialization.registerClass(PlayerJob.class);
        saveDefaultConfig();
        Settings.init(this);
        this.fileManager = new FileManager(this);
        this.messages = new Messages(this);
        this.jobManager = new JobManager(fileManager);
        getServer().getPluginManager().registerEvents(new JobListener(this), this);
        JobCommand jobCommand = new JobCommand(this);
        getCommand("job").setExecutor(jobCommand);
        getCommand("job").setTabCompleter(jobCommand);
        getLogger().info("JobSystem enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("JobSystem disabled");
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public double boostMultiplier() {
        return System.currentTimeMillis() < boostUntil ? boostMultiplier : 1.0;
    }

    public long boostRemainingSeconds() {
        return Math.max(0, (boostUntil - System.currentTimeMillis()) / 1000);
    }

    public void startBoost(double multiplier, long minutes) {
        this.boostMultiplier = multiplier;
        this.boostUntil = System.currentTimeMillis() + minutes * 60_000L;
        long until = boostUntil;
        getServer().getScheduler().runTaskLater(this, () -> {
            if (boostUntil == until) {
                getServer().getOnlinePlayers().forEach(p -> messages.send(p, "boost.ended"));
            }
        }, minutes * 60 * 20L);
    }

    public void stopBoost() {
        this.boostUntil = 0;
    }

    public Messages getMessages() {
        return messages;
    }

    public void reloadAll() {
        reloadConfig();
        messages.load();
        jobManager.registerConfiguredJobs();
    }

    public JobManager getJobManager() {
        return jobManager;
    }
}
