package ch.framedev.jobSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Level based perks. Every level of every job grants a stronger bonus. */
public final class JobBenefits {

    private JobBenefits() {
    }

    private static int clamp(int level) {
        return Math.min(Math.max(level, 0), Levels.max());
    }

    private static double d(String path) {
        return Settings.dbl("perks." + path);
    }

    private static int i(String path) {
        return Settings.integer("perks." + path);
    }

    /** Chance of the main bonus effect. */
    public static double chance(int level) {
        return Math.min(d("max-chance"), clamp(level) * d("chance-per-level"));
    }

    public static double builderRefundChance(int level) {
        return Math.min(d("max-chance"), clamp(level) * d("builder.refund-per-level"));
    }

    public static int blacksmithUnbreaking(int level) {
        int perTier = Math.max(1, i("blacksmith.levels-per-tier"));
        return Math.max(1, Math.min(i("blacksmith.unbreaking-max"), (clamp(level) + perTier - 1) / perTier));
    }

    public static double blacksmithMendingChance(int level) {
        int start = i("blacksmith.mending.start-level");
        return level >= start ? Math.min(1.0, (level - start + 1) * d("blacksmith.mending.chance-per-level")) : 0.0;
    }

    /** Fisher: bite wait time multiplier (lower is faster). */
    public static double fisherWaitMultiplier(int level) {
        int start = i("fisher.fast-bite.start-level");
        if (level < start) {
            return 1.0;
        }
        double reduction = Math.min(d("fisher.fast-bite.max-reduction"),
                (level - start + 1) * d("fisher.fast-bite.reduction-per-level"));
        return 1.0 - reduction;
    }

    public static double hunterDamageBonus(int level) {
        return Math.min(d("hunter.max-damage"), clamp(level) * d("hunter.damage-per-level"));
    }

    public static int bonusVanillaXp(int level) {
        int start = i("bonus-xp.start-level");
        return level >= start ? Math.min(i("bonus-xp.max"), (level - start + 1) * i("bonus-xp.per-level")) : 0;
    }

    /** Haste tier (0 = none) from the "perks.haste.tiers" map of level -> tier. */
    public static int hasteTier(int level) {
        var section = Settings.cfg().getConfigurationSection("perks.haste.tiers");
        int tier = 0;
        int best = -1;
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    int from = Integer.parseInt(key);
                    if (from <= level && from > best) {
                        best = from;
                        tier = section.getInt(key);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return tier;
    }

    public static int hasteAmplifier(int level) {
        return hasteTier(level) - 1;
    }

    public static int hasteTicks(int level) {
        return i("haste.base-ticks") + clamp(level) * i("haste.ticks-per-level");
    }

    public static int bonusFood(int level) {
        int start = i("food.start-level");
        return level >= start ? Math.min(i("food.max"), level / Math.max(1, i("food.level-divisor"))) : 0;
    }

    public static double builderFallMultiplier(int level) {
        if (level >= i("builder.fall.immune-level")) {
            return 0.0;
        }
        int start = i("builder.fall.start-level");
        if (level >= start) {
            return Math.max(0.0, 1.0 - (level - start + 1) * d("builder.fall.reduction-per-level"));
        }
        return 1.0;
    }

    public static int brewerRegenSeconds(int level) {
        int start = i("brewer.regen.start-level");
        return level >= start ? Math.min(i("brewer.regen.max-seconds"), (level - start + 1) * i("brewer.regen.seconds-per-level")) : 0;
    }

    /** Chance that a job tool does not lose durability. */
    public static double toolSaverChance(int level) {
        int start = i("tool-saver.start-level");
        return level >= start ? Math.min(d("tool-saver.max-chance"), (level - start + 1) * d("tool-saver.chance-per-level")) : 0.0;
    }

    public static boolean autoReplant(int level) {
        return level >= i("farmer.auto-replant.start-level");
    }

    public static double fisherTreasureChance(int level) {
        int start = i("fisher.treasure.start-level");
        return level >= start ? Math.min(d("fisher.treasure.max-chance"), (level - start + 1) * d("fisher.treasure.chance-per-level")) : 0.0;
    }

    public static int hunterKillSpeedTicks(int level) {
        int start = i("hunter.kill-speed.start-level");
        return level >= start ? i("hunter.kill-speed.base-ticks") + (level - start) * i("hunter.kill-speed.ticks-per-level") : 0;
    }

    public static int minerNightVisionTicks(int level) {
        int start = i("miner.night-vision.start-level");
        return level >= start ? i("miner.night-vision.base-ticks") + (level - start) * i("miner.night-vision.ticks-per-level") : 0;
    }

    private static String sec(int ticks) {
        return String.valueOf(ticks / 20);
    }

    /** A perk description: a language key under "perk." plus placeholder name/value pairs. */
    public record Perk(String key, String... args) {
    }

    private static String pct(double value) {
        return String.valueOf((int) Math.round(value * 100));
    }

    /** Cumulative perks for the given job at the given level. */
    public static List<Perk> perks(String jobName, int level) {
        String chance = pct(chance(level));
        String xp = String.valueOf(bonusVanillaXp(level));
        String tier = String.valueOf(hasteTier(level));
        List<Perk> out = new ArrayList<>();
        switch (jobName.toLowerCase(Locale.ROOT)) {
            case "fisher" -> {
                out.add(new Perk("extra-fish", "pct", chance));
                if (fisherWaitMultiplier(level) < 1.0) {
                    out.add(new Perk("fast-bite", "pct", pct(1 - fisherWaitMultiplier(level))));
                }
                if (fisherTreasureChance(level) > 0) out.add(new Perk("treasure", "pct", pct(fisherTreasureChance(level))));
                if (bonusVanillaXp(level) > 0) out.add(new Perk("bonus-xp", "amount", xp));
            }
            case "hunter" -> {
                out.add(new Perk("double-hostile", "pct", chance));
                out.add(new Perk("damage", "pct", pct(hunterDamageBonus(level))));
                if (hunterKillSpeedTicks(level) > 0) out.add(new Perk("kill-speed", "seconds", sec(hunterKillSpeedTicks(level))));
                if (bonusVanillaXp(level) > 0) out.add(new Perk("bonus-xp", "amount", xp));
            }
            case "butcher" -> {
                out.add(new Perk("double-animal", "pct", chance));
                if (bonusFood(level) > 0) out.add(new Perk("feed-kill", "amount", String.valueOf(bonusFood(level))));
                if (bonusVanillaXp(level) > 0) out.add(new Perk("bonus-xp", "amount", xp));
            }
            case "farmer" -> {
                out.add(new Perk("double-crops", "pct", chance));
                if (autoReplant(level)) out.add(new Perk("auto-replant"));
                if (bonusFood(level) > 0) out.add(new Perk("feed-eat", "amount", String.valueOf(bonusFood(level))));
                if (bonusVanillaXp(level) > 0) out.add(new Perk("bonus-xp", "amount", xp));
            }
            case "miner" -> {
                out.add(new Perk("double-ore", "pct", chance));
                if (minerNightVisionTicks(level) > 0) out.add(new Perk("night-vision", "seconds", sec(minerNightVisionTicks(level))));
                if (hasteTier(level) > 0) out.add(new Perk("haste-mine", "tier", tier));
                if (bonusVanillaXp(level) > 0) out.add(new Perk("bonus-xp", "amount", xp));
            }
            case "lumberjack" -> {
                out.add(new Perk("double-logs", "pct", chance));
                if (hasteTier(level) > 0) out.add(new Perk("haste-chop", "tier", tier));
            }
            case "builder" -> {
                out.add(new Perk("refund", "pct", pct(builderRefundChance(level))));
                if (builderFallMultiplier(level) <= 0.0) {
                    out.add(new Perk("fall-immune"));
                } else if (builderFallMultiplier(level) < 1.0) {
                    out.add(new Perk("fall", "pct", pct(1 - builderFallMultiplier(level))));
                }
            }
            case "blacksmith" -> {
                out.add(new Perk("unbreaking", "pct", chance, "tier", String.valueOf(blacksmithUnbreaking(level))));
                if (blacksmithMendingChance(level) > 0) out.add(new Perk("mending", "pct", pct(blacksmithMendingChance(level))));
            }
            case "brewer" -> {
                out.add(new Perk("extra-potion", "pct", chance));
                if (brewerRegenSeconds(level) > 0) out.add(new Perk("regen", "seconds", String.valueOf(brewerRegenSeconds(level))));
            }
            default -> out.add(new Perk("none"));
        }
        if (toolSaverChance(level) > 0 && List.of("fisher", "hunter", "butcher", "farmer", "miner", "lumberjack")
                .contains(jobName.toLowerCase(Locale.ROOT))) {
            out.add(new Perk("tool-saver", "pct", pct(toolSaverChance(level))));
        }
        return out;
    }

    public static String describe(Messages messages, String lang, String jobName, int level) {
        List<String> parts = new ArrayList<>();
        for (Perk perk : perks(jobName, level)) {
            parts.add(messages.perk(lang, perk));
        }
        return String.join(", ", parts);
    }
}
