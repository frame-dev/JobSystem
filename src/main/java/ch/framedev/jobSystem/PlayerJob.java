package ch.framedev.jobSystem;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PlayerJob implements ConfigurationSerializable {

    private Job job;
    private int level;
    private double experience;
    private int prestige;

    public PlayerJob() {
        this(null, 1, 0.0);
    }

    public PlayerJob(Job job) {
        this(job, 1, 0.0);
    }

    public PlayerJob(Job job, int level, double experience) {
        this.job = job;
        this.level = Levels.clamp(level);
        this.experience = Math.max(0.0, experience);
        normalizeExperience();
    }

    public int getPrestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        this.prestige = Math.max(0, prestige);
    }

    /** Resets level and XP and increases the prestige counter. */
    public void prestige() {
        prestige++;
        level = 1;
        experience = 0.0;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public int getLevelNumber() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Levels.clamp(level);
        normalizeExperience();
    }

    public double getExperience() {
        return experience;
    }

    public void setExperience(double experience) {
        this.experience = Math.max(0.0, experience);
        normalizeExperience();
    }

    public boolean hasJob() {
        return job != null;
    }

    public boolean isMaxLevel() {
        return level >= Levels.max();
    }

    public double getExperienceRequiredForNextLevel() {
        return isMaxLevel() ? 0.0 : Levels.required(level);
    }

    public double getExperienceNeededUntilNextLevel() {
        return isMaxLevel() ? 0.0 : Math.max(0.0, Levels.required(level) - experience);
    }

    public void addExperience(double amount) {
        if (amount <= 0) {
            return;
        }
        double remaining = amount;
        while (!isMaxLevel() && remaining > 0) {
            double needed = getExperienceNeededUntilNextLevel();
            if (remaining < needed) {
                experience += remaining;
                remaining = 0;
            } else {
                remaining -= needed;
                levelUp();
            }
        }
        normalizeExperience();
    }

    public void levelUp() {
        if (isMaxLevel()) {
            return;
        }
        level++;
        experience = 0.0;
    }

    private void normalizeExperience() {
        if (experience < 0) {
            experience = 0.0;
        }
        if (isMaxLevel()) {
            experience = 0.0;
        } else if (experience >= Levels.required(level)) {
            experience = Levels.required(level) - 1;
        }
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("job", job == null ? null : job.serialize());
        map.put("level", level);
        map.put("experience", experience);
        map.put("prestige", prestige);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static PlayerJob deserialize(Map<String, Object> map) {
        Job job = null;
        Object jobData = map.get("job");
        if (jobData instanceof Map<?, ?> jobMap) {
            job = new Job((Map<String, Object>) jobMap);
        } else if (jobData instanceof Job stored) {
            job = stored;
        }

        int level = 1;
        Object levelValue = map.get("level");
        if (levelValue instanceof Number number) {
            level = number.intValue();
        } else if (levelValue instanceof String text) {
            try {
                level = Integer.parseInt(text.toUpperCase(Locale.ROOT).replace("LEVEL_", ""));
            } catch (NumberFormatException ignored) {
            }
        }

        double experience = 0.0;
        if (map.get("experience") instanceof Number number) {
            experience = number.doubleValue();
        }
        PlayerJob result = new PlayerJob(job, level, experience);
        if (map.get("prestige") instanceof Number number) {
            result.setPrestige(number.intValue());
        }
        return result;
    }
}
