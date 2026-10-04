package ch.framedev.jobSystem;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public class FileManager {

    private final File playerJobFile;
    private final FileConfiguration cfg;

    public FileManager(JobSystem plugin) {
        playerJobFile = new File(plugin.getDataFolder(), "jobs.yml");
        if (!playerJobFile.exists()) {
            playerJobFile.getParentFile().mkdirs();
            try {
                playerJobFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        cfg = YamlConfiguration.loadConfiguration(playerJobFile);
    }

    public boolean addJob(Job job) {
        if (existsJobByName(job.getName()) || existsJobById(job.getId())) {
            return false;
        }
        cfg.set("job." + job.getName(), job);
        try {
            cfg.save(playerJobFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    public List<Job> getJobs() {
        List<Job> jobs = new ArrayList<>();
        ConfigurationSection jobSection = cfg.getConfigurationSection("job");
        if (jobSection == null) {
            return jobs;
        }
        for (String key : jobSection.getKeys(false)) {
            jobs.add((Job) cfg.get("job." + key));
        }
        return jobs;
    }

    public boolean existsJobByName(String name) {
        return cfg.contains("job." + name);
    }

    public boolean existsJobById(int id) {
        for (Job job : getJobs()) {
            if (job.getId() == id) {
                return true;
            }
        }
        return false;
    }

    public Job getJobById(int id) {
        for (Job job : getJobs()) {
            if (job.getId() == id) {
                return job;
            }
        }
        return null;
    }

    public Job getJobByName(String name) {
        for (Job job : getJobs()) {
            if (job.getName().equalsIgnoreCase(name)) {
                return job;
            }
        }
        return null;
    }

    public boolean setPlayerJob(Player player, Job job) {
        return setPlayerJob(player, new PlayerJob(job));
    }

    public boolean setPlayerJob(Player player, PlayerJob playerJob) {
        if (playerJob == null || playerJob.getJob() == null) {
            return false;
        }
        cfg.set("jobs." + player.getName(), playerJob);
        save();
        return true;
    }

    public boolean removePlayerJob(Player player) {
        if (!hasPlayerJob(player)) {
            return false;
        }
        cfg.set("jobs." + player.getName(), null);
        save();
        return true;
    }

    public boolean hasPlayerJob(Player player) {
        String path = "jobs." + player.getName();
        return cfg.contains(path) && cfg.get(path) != null;
    }

    public Map<String, PlayerJob> getAllPlayerJobs() {
        Map<String, PlayerJob> result = new LinkedHashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection("jobs");
        if (section == null) {
            return result;
        }
        for (String name : section.getKeys(false)) {
            PlayerJob pj = readPlayerJob("jobs." + name);
            if (pj != null && pj.hasJob()) {
                result.put(name, pj);
            }
        }
        return result;
    }

    public String getLanguage(Player player) {
        return cfg.getString("lang." + player.getName());
    }

    public void setLanguage(Player player, String code) {
        cfg.set("lang." + player.getName(), code);
        save();
    }

    public long getLeaveTime(Player player) {
        return cfg.getLong("left." + player.getName(), 0L);
    }

    public void setLeaveTime(Player player, long millis) {
        cfg.set("left." + player.getName(), millis <= 0 ? null : millis);
        save();
    }

    public PlayerJob getPlayerJob(Player player) {
        return readPlayerJob("jobs." + player.getName());
    }

    private PlayerJob readPlayerJob(String path) {
        if (!cfg.contains(path) || cfg.get(path) == null) {
            return null;
        }
        Object value = cfg.get(path);
        if (value instanceof PlayerJob playerJob) {
            return playerJob;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            return PlayerJob.deserialize((java.util.Map<String, Object>) map);
        }
        return null;
    }

    public Job getPlayerJobJob(Player player) {
        PlayerJob playerJob = getPlayerJob(player);
        return playerJob == null ? null : playerJob.getJob();
    }

    public void save() {
        try {
            cfg.save(playerJobFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
