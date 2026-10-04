package ch.framedev.jobSystem;

import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class JobListener implements Listener {

    private record BlockKey(UUID world, long block) {
    }

    private static Set<BlockKey> lruSet(int max) {
        return Collections.newSetFromMap(new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BlockKey, Boolean> eldest) {
                return size() > max;
            }
        });
    }

    private final JobSystem plugin;
    private final JobManager jobManager;
    private final Messages msg;
    private final Set<BlockKey> placedBlocks;
    private final Set<BlockKey> builderBlocks;

    public JobListener(JobSystem plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
        this.msg = plugin.getMessages();
        int cache = Math.max(1000, Settings.integer("placed-block-cache-size"));
        this.placedBlocks = lruSet(cache);
        this.builderBlocks = lruSet(cache);
    }

    private BlockKey key(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getBlockKey());
    }

    private double xp(String key, double def) {
        return plugin.getConfig().getDouble("xp." + key, def);
    }

    private boolean disabled(Player player) {
        return plugin.getConfig().getStringList("disabled-worlds").contains(player.getWorld().getName());
    }

    private void give(Player player, String job, double baseXp) {
        if (disabled(player)) {
            return;
        }
        PlayerJob current = jobManager.getPlayerJob(player);
        if (current == null || !current.hasJob() || !current.getJob().getName().equalsIgnoreCase(job)) {
            return;
        }
        int levelBefore = current.getLevelNumber();
        double prestigeBonus = 1.0 + current.getPrestige() * Settings.dbl("prestige.xp-bonus-per-prestige");
        double gained = baseXp * Settings.dbl("xp-multiplier") * prestigeBonus * plugin.boostMultiplier();
        jobManager.addExperience(player, job, gained);
        PlayerJob pj = jobManager.getPlayerJob(player);
        String lang = msg.languageOf(player);
        String jobName = msg.jobName(lang, job);
        String gain = gained == Math.rint(gained) ? String.valueOf((int) gained)
                : String.format(java.util.Locale.ROOT, "%.1f", gained);
        if (pj.isMaxLevel()) {
            if (Settings.bool("display.action-bar")) player.sendActionBar(msg.component(lang, "actionbar.max", "job", jobName,
                    "level", String.valueOf(pj.getLevelNumber()), "gain", gain));
        } else {
            double required = pj.getExperienceRequiredForNextLevel();
            double ratio = Math.min(1.0, pj.getExperience() / required);
            int length = Math.max(1, Settings.integer("display.bar-length"));
            int filled = (int) Math.round(ratio * length);
            String ch = Settings.string("display.bar-char");
            String bar = "<" + Settings.string("display.bar-filled-color") + ">" + ch.repeat(filled)
                    + "</" + Settings.string("display.bar-filled-color") + ">"
                    + "<" + Settings.string("display.bar-empty-color") + ">" + ch.repeat(length - filled)
                    + "</" + Settings.string("display.bar-empty-color") + ">";
            if (Settings.bool("display.action-bar")) player.sendActionBar(msg.component(lang, "actionbar.progress", "job", jobName,
                    "level", String.valueOf(pj.getLevelNumber()), "bar", bar,
                    "xp", String.valueOf((int) pj.getExperience()), "required", String.valueOf((int) required),
                    "percent", String.valueOf((int) (ratio * 100)), "gain", gain));
        }
        if (pj.getLevelNumber() > levelBefore) {
            announceLevelUp(player, lang, job, jobName, pj.getLevelNumber());
            for (int n = levelBefore + 1; n <= pj.getLevelNumber(); n++) {
                applyRewards(player, job, n, pj.getPrestige());
            }
        }
    }

    private void applyRewards(Player player, String job, int level, int prestige) {
        for (String path : List.of("rewards.levels." + level, "rewards.jobs." + job + "." + level)) {
            for (String command : Settings.list(path + ".commands")) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                        command.replace("{player}", player.getName()).replace("{job}", job)
                                .replace("{level}", String.valueOf(level)).replace("{prestige}", String.valueOf(prestige)));
            }
        }
        if (Settings.bool("rewards.levels." + level + ".broadcast")) {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                String lang = msg.languageOf(online);
                online.sendMessage(msg.component(lang, "levelup.broadcast", "player", player.getName(),
                        "job", msg.jobName(lang, job), "level", String.valueOf(level)));
            }
        }
    }

    private void announceLevelUp(Player player, String lang, String job, String jobName, int level) {
        player.sendMessage(msg.component(lang, "levelup.chat", "job", jobName, "level", String.valueOf(level)));
        if (Settings.bool("level-up.chat-perks") && JobManager.perksEnabled(job)) {
            player.sendMessage(msg.component(lang, "levelup.perks"));
            for (JobBenefits.Perk perk : JobBenefits.perks(job, level)) {
                player.sendMessage(msg.component(lang, "levelup.perk-line", "perk", msg.perk(lang, perk)));
            }
        }
        if (plugin.getConfig().getBoolean("level-up.title", true)) {
            player.showTitle(Title.title(
                    msg.component(lang, "levelup.title", "level", String.valueOf(level)),
                    msg.component(lang, "levelup.subtitle", "job", jobName)));
        }
        if (plugin.getConfig().getBoolean("level-up.sound", true)) {
            player.playSound(player.getLocation(), Settings.string("level-up.sound-name"),
                    (float) Settings.dbl("level-up.sound-volume"), (float) Settings.dbl("level-up.sound-pitch"));
        }
    }

    /** Returns the player's level in the given job, or 0 if they do not have it. */
    private int levelOf(Player player, String job) {
        PlayerJob pj = jobManager.getPlayerJob(player);
        if (disabled(player) || !JobManager.perksEnabled(job) || pj == null || !pj.hasJob() || !pj.getJob().getName().equalsIgnoreCase(job)) {
            return 0;
        }
        return pj.getLevelNumber();
    }

    private void bonusXp(Player player, Location loc, int level) {
        int amount = JobBenefits.bonusVanillaXp(level);
        if (amount > 0) {
            loc.getWorld().spawn(loc, ExperienceOrb.class, orb -> orb.setExperience(amount));
        }
    }

    private void haste(Player player, int level) {
        int amp = JobBenefits.hasteAmplifier(level);
        if (amp >= 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, JobBenefits.hasteTicks(level), amp, true, false));
        }
    }

    private void feed(Player player, int amount) {
        if (amount > 0) {
            player.setFoodLevel(Math.min(20, player.getFoodLevel() + amount));
            player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + amount));
        }
    }

    private boolean matches(String name, String suffixPath, String materialPath) {
        return Settings.list(materialPath).contains(name)
                || Settings.list(suffixPath).stream().anyMatch(name::endsWith);
    }

    /** Parses entries like "EMERALD:1-2" from perks.fisher.treasure.items. */
    private ItemStack randomTreasure() {
        List<String> items = Settings.list("perks.fisher.treasure.items");
        if (items.isEmpty()) {
            return null;
        }
        String[] parts = items.get(ThreadLocalRandom.current().nextInt(items.size())).split(":");
        Material material = Material.matchMaterial(parts[0]);
        if (material == null) {
            return null;
        }
        int min = 1;
        int max = 1;
        if (parts.length > 1) {
            String[] range = parts[1].split("-");
            try {
                min = Integer.parseInt(range[0].trim());
                max = range.length > 1 ? Integer.parseInt(range[1].trim()) : min;
            } catch (NumberFormatException ignored) {
            }
        }
        return new ItemStack(material, ThreadLocalRandom.current().nextInt(min, Math.max(min, max) + 1));
    }

    private boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        int level = levelOf(player, "Fisher");
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            double mult = JobBenefits.fisherWaitMultiplier(level);
            if (level > 0 && mult < 1.0) {
                var hook = event.getHook();
                int min = Settings.integer("perks.fisher.fast-bite.min-wait-ticks");
                hook.setMinWaitTime(Math.max(min, (int) (hook.getMinWaitTime() * mult)));
                hook.setMaxWaitTime(Math.max(min * 2, (int) (hook.getMaxWaitTime() * mult)));
            }
            return;
        }
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (level > 0) {
            bonusXp(player, player.getLocation(), level);
        }
        if (level > 0 && roll(JobBenefits.chance(level)) && event.getCaught() instanceof Item caught) {
            player.getWorld().dropItem(player.getLocation(), caught.getItemStack().clone());
        }
        if (level > 0 && roll(JobBenefits.fisherTreasureChance(level))) {
            ItemStack treasure = randomTreasure();
            if (treasure != null) {
                player.getWorld().dropItem(player.getLocation(), treasure);
            }
        }
        give(player, "Fisher", xp("fisher", 10));
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        Entity entity = event.getEntity();
        String job;
        double xp;
        String type = entity.getType().name();
        List<String> hunterList = Settings.list("actions.hunter-entities");
        List<String> butcherList = Settings.list("actions.butcher-entities");
        boolean hunter = hunterList.isEmpty() ? entity instanceof Monster : hunterList.contains(type);
        boolean butcher = butcherList.isEmpty() ? entity instanceof Animals : butcherList.contains(type);
        if (hunter) {
            job = "Hunter";
            xp = xp("hunter", 8);
        } else if (butcher) {
            job = "Butcher";
            xp = xp("butcher", 6);
        } else {
            return;
        }
        int level = levelOf(killer, job);
        if (level > 0) {
            event.setDroppedExp(event.getDroppedExp() + JobBenefits.bonusVanillaXp(level));
            if (job.equals("Butcher")) {
                feed(killer, JobBenefits.bonusFood(level));
            } else {
                int ticks = JobBenefits.hunterKillSpeedTicks(level);
                if (ticks > 0) {
                    killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0, true, false));
                }
            }
        }
        if (level > 0 && roll(JobBenefits.chance(level))) {
            for (ItemStack drop : new ArrayList<>(event.getDrops())) {
                event.getDrops().add(drop.clone());
            }
        }
        give(killer, job, xp);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();
        String name = type.name();

        String job;
        double xp;
        boolean bonusEligible = true;
        if (block.getBlockData() instanceof Ageable ageable
                && (!Settings.bool("actions.farmer-require-mature") || ageable.getAge() == ageable.getMaximumAge())) {
            job = "Farmer";
            xp = xp("farmer", 4);
        } else if (matches(name, "actions.miner-ore-suffixes", "actions.miner-ore-materials")) {
            job = "Miner";
            xp = xp("miner-ore", 8);
        } else if (Settings.list("actions.miner-stone-materials").contains(name)) {
            job = "Miner";
            xp = xp("miner-stone", 1);
            bonusEligible = false;
        } else if (matches(name, "actions.lumberjack-suffixes", "actions.lumberjack-materials")) {
            job = "Lumberjack";
            xp = xp("lumberjack", 3);
        } else {
            return;
        }

        boolean wasPlaced = placedBlocks.remove(key(block));
        if (wasPlaced && !job.equals("Farmer") && plugin.getConfig().getBoolean("ignore-player-placed", true)) {
            return;
        }

        int level = levelOf(player, job);
        if (level > 0 && job.equals("Miner") && bonusEligible) {
            int nv = JobBenefits.minerNightVisionTicks(level);
            if (nv > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, nv, 0, true, false));
            }
        }
        if (level > 0 && job.equals("Farmer") && JobBenefits.autoReplant(level)
                && Settings.list("perks.farmer.auto-replant.crops").contains(name)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (block.getType() == Material.AIR) {
                    block.setType(type);
                    if (block.getBlockData() instanceof Ageable crop) {
                        crop.setAge(0);
                        block.setBlockData(crop);
                    }
                }
            });
        }
        if (level > 0 && bonusEligible && (job.equals("Miner") || job.equals("Lumberjack"))) {
            haste(player, level);
        }
        if (level > 0 && bonusEligible && !job.equals("Lumberjack")) {
            event.setExpToDrop(event.getExpToDrop() + JobBenefits.bonusVanillaXp(level));
        }
        if (level > 0 && bonusEligible && player.getGameMode() != GameMode.CREATIVE
                && roll(JobBenefits.chance(level))) {
            Location loc = block.getLocation();
            for (ItemStack drop : block.getDrops(player.getInventory().getItemInMainHand(), player)) {
                loc.getWorld().dropItemNaturally(loc, drop);
            }
        }
        give(player, job, xp);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block placed = event.getBlockPlaced();
        placedBlocks.add(key(placed));
        int level = levelOf(player, "Builder");
        if (level > 0 && player.getGameMode() != GameMode.CREATIVE
                && roll(JobBenefits.builderRefundChance(level))) {
            ItemStack refund = event.getItemInHand().clone();
            refund.setAmount(1);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.getInventory().addItem(refund).values()
                            .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest)));
        }
        boolean unique = builderBlocks.add(key(placed))
                || !plugin.getConfig().getBoolean("builder-unique-blocks", true);
        if (unique && !(placed.getBlockData() instanceof Ageable)) {
            give(player, "Builder", xp("builder", 0.5));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        String name = result.getType().name();
        boolean gear = name.matches(Settings.string("actions.blacksmith-gear-regex"))
                || Settings.list("actions.blacksmith-extra-materials").contains(name);
        if (!gear) {
            return;
        }
        int level = levelOf(player, "Blacksmith");
        if (level > 0 && !event.isShiftClick() && roll(JobBenefits.chance(level))) {
            ItemStack enchanted = result.clone();
            ItemMeta meta = enchanted.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, JobBenefits.blacksmithUnbreaking(level), true);
                if (roll(JobBenefits.blacksmithMendingChance(level))) {
                    meta.addEnchant(Enchantment.MENDING, 1, true);
                }
                enchanted.setItemMeta(meta);
                event.setCurrentItem(enchanted);
            }
        }
        give(player, "Blacksmith", xp("blacksmith", 5));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrewTake(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.BREWING
                || event.getClickedInventory() != event.getInventory()
                || event.getSlot() > 2
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.getType().name().endsWith("POTION")) {
            return;
        }
        int level = levelOf(player, "Brewer");
        if (level > 0 && roll(JobBenefits.chance(level))) {
            player.getInventory().addItem(item.clone()).values()
                    .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        }
        give(player, "Brewer", xp("brewer", 5));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof Monster) {
            int level = levelOf(player, "Hunter");
            if (level > 0) {
                event.setDamage(event.getDamage() * (1.0 + JobBenefits.hunterDamageBonus(level)));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player) {
            int level = levelOf(player, "Builder");
            if (level > 0) {
                event.setDamage(event.getDamage() * JobBenefits.builderFallMultiplier(level));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material type = event.getItem().getType();
        if (type == Material.POTION) {
            int seconds = JobBenefits.brewerRegenSeconds(levelOf(player, "Brewer"));
            if (seconds > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, seconds * 20, 0));
            }
        } else if (type.isEdible()) {
            int level = levelOf(player, "Farmer");
            if (level > 0) {
                plugin.getServer().getScheduler().runTask(plugin, () -> feed(player, JobBenefits.bonusFood(level)));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToolDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        String tool = event.getItem().getType().name();
        List<String> jobs;
        if (tool.endsWith("_PICKAXE")) {
            jobs = List.of("Miner");
        } else if (tool.endsWith("_AXE")) {
            jobs = List.of("Lumberjack");
        } else if (tool.endsWith("_HOE")) {
            jobs = List.of("Farmer");
        } else if (tool.equals("FISHING_ROD")) {
            jobs = List.of("Fisher");
        } else if (tool.endsWith("_SWORD")) {
            jobs = List.of("Hunter", "Butcher");
        } else {
            return;
        }
        for (String job : jobs) {
            int level = levelOf(player, job);
            if (level > 0 && roll(JobBenefits.toolSaverChance(level))) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
