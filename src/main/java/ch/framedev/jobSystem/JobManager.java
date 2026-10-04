package ch.framedev.jobSystem;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;

public class JobManager {

    private final FileManager fileManager;

    public JobManager(FileManager fileManager) {
        this.fileManager = fileManager;
        registerConfiguredJobs();
    }

    /** Registers every job from the "jobs" section of config.yml that is not stored yet. */
    public void registerConfiguredJobs() {
        ConfigurationSection section = Settings.cfg().getConfigurationSection("jobs");
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            int id = section.getInt(name + ".id", -1);
            if (id < 0 || fileManager.existsJobByName(name) || fileManager.existsJobById(id)) {
                continue;
            }
            addJob(new Job(id, name));
        }
    }

    public static boolean isEnabled(String jobName) {
        return Settings.cfg().getBoolean("jobs." + jobName + ".enabled", true);
    }

    public static boolean perksEnabled(String jobName) {
        return Settings.bool("perks.enabled") && Settings.cfg().getBoolean("jobs." + jobName + ".perks", true);
    }

    public boolean addJob(Job job) {
        return fileManager.addJob(job);
    }

    /** Enabled jobs only. */
    public List<Job> getJobs() {
        return fileManager.getJobs().stream().filter(job -> isEnabled(job.getName())).toList();
    }

    public Job getJobById(int id) {
        return fileManager.getJobById(id);
    }

    public Job getJobByName(String name) {
        Job job = fileManager.getJobByName(name);
        return job != null && isEnabled(job.getName()) ? job : null;
    }

    public boolean setPlayerJob(Player player, Job job) {
        return fileManager.setPlayerJob(player, job);
    }

    public boolean setPlayerJob(Player player, PlayerJob playerJob) {
        return fileManager.setPlayerJob(player, playerJob);
    }

    public boolean removePlayerJob(Player player) {
        return fileManager.removePlayerJob(player);
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public boolean hasPlayerJob(Player player) {
        return fileManager.hasPlayerJob(player);
    }

    public PlayerJob getPlayerJob(Player player) {
        return fileManager.getPlayerJob(player);
    }

    public Job getPlayerJobJob(Player player) {
        return fileManager.getPlayerJobJob(player);
    }

    /**
     * Grants experience if the player's current job matches the given job name.
     * Returns true if the player levelled up.
     */
    public boolean addExperience(Player player, String jobName, double amount) {
        PlayerJob playerJob = getPlayerJob(player);
        if (playerJob == null || !playerJob.hasJob()
                || !playerJob.getJob().getName().equalsIgnoreCase(jobName)) {
            return false;
        }
        int before = playerJob.getLevelNumber();
        playerJob.addExperience(amount);
        fileManager.setPlayerJob(player, playerJob);
        return playerJob.getLevelNumber() > before;
    }
}
