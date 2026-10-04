package ch.framedev.jobSystem;

import java.util.List;

/** Level thresholds, read from "levels.xp-required" in config.yml. The list size is the max level. */
public final class Levels {

    private Levels() {
    }

    private static List<Double> table() {
        List<Double> list = Settings.cfg().getDoubleList("levels.xp-required");
        return list.isEmpty() ? List.of(100.0) : list;
    }

    public static int max() {
        return table().size();
    }

    /** XP needed to advance from the given level to the next one. */
    public static double required(int level) {
        List<Double> table = table();
        int index = Math.min(Math.max(level, 1), table.size()) - 1;
        return Math.max(1.0, table.get(index));
    }

    public static int clamp(int level) {
        return Math.min(Math.max(level, 1), max());
    }
}
