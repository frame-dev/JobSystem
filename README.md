# JobSystem

A Paper plugin that lets players pick a profession, earn XP by doing job-related actions, level up (1–30 by default) and unlock perks at every level. Fully configurable and translatable (EN, DE, FR, ES included).

## Requirements

- Paper 1.21+ (built against the Paper API)
- Java 21

## Build

```bash
mvn clean package
```

The jar is created in `target/JobSystem-1.0-SNAPSHOT.jar`. Drop it into your server's `plugins/` folder.

## Jobs

| Job | XP source | Perks (cumulative, scale with level) |
|---|---|---|
| Fisher | Catching fish | Extra fish chance; faster bites and bonus XP from level 4 |
| Hunter | Killing hostile mobs | Double drops; +4% damage per level against monsters; bonus XP from level 4 |
| Butcher | Killing animals | Double drops; hunger restored per kill from level 3; bonus XP from level 4 |
| Farmer | Harvesting fully grown crops | Double crops; extra hunger from food from level 3; bonus XP from level 4 |
| Miner | Mining ore, stone, deepslate | Double ore drops; Haste after mining ore from level 3; bonus XP from level 4 |
| Lumberjack | Chopping logs/stems | Double logs; Haste after chopping from level 3 |
| Builder | Placing blocks | Chance a block isn't used up; reduced fall damage from level 6, immunity at level 10 |
| Blacksmith | Crafting metal tools/armor, shields, anvils | Unbreaking on crafted gear; Mending chance from level 8 |
| Brewer | Taking potions from a brewing stand | Extra potion chance; Regeneration when drinking potions from level 6 |

Use `/job benefits [job]` in-game to see the exact perks for every level.

Default XP needed per level: 100, 200, 300, 400, 500, 650, 800, 1000, 1250, 1500, 1800, 2100, 2500, 3000, 3600, 4300, 5100, 6000, 7000, 8000, 9200, 10500, 12000, 13500, 15000, 17000, 19000, 21500, 24000, 27000 (configurable). The perk descriptions below use the default values.

### Extra perks (all configurable)

- **Tool saver** (Fisher, Hunter, Butcher, Farmer, Miner, Lumberjack): chance your tool loses no durability.
- **Farmer:** harvested crops are replanted automatically.
- **Fisher:** chance to fish up treasure (emeralds, gold, diamonds, ...).
- **Hunter:** Speed after killing a monster.
- **Miner:** Night Vision after mining ore.

### Prestige

At max level, `/job prestige` resets level and XP but keeps your job and gives a permanent XP bonus (+10% per prestige by default, up to 10 prestiges). Prestige is shown in `/job info` and ranks above level in `/job top`.

### Milestone rewards

`rewards.levels.<n>` and `rewards.jobs.<Job>.<n>` in `config.yml` run console commands when a level is reached and can broadcast the milestone (translated per player). `prestige.commands` runs on prestige.

### XP boosts

`/job boost <multiplier> <minutes>` starts a server-wide XP boost; `/job boost off` stops it.

Players see an action bar with their level and progress after each XP gain, and a title, sound and perk list on level-up.

## Commands

Base command: `/job` (alias `/jobs`)

| Command | Description | Permission |
|---|---|---|
| `/job help` | List commands you may use | – |
| `/job list` | Show all jobs | `jobsystem.list` |
| `/job join <job>` | Join a job | `jobsystem.join` |
| `/job leave` | Leave your job (progress is reset) | `jobsystem.leave` |
| `/job info [player]` | Show job, level and XP | `jobsystem.info` (`jobsystem.info.others` for other players) |
| `/job benefits [job]` | Show perks per level | `jobsystem.benefits` |
| `/job top [job]` | Top 10 leaderboard | `jobsystem.top` |
| `/job lang [code\|auto]` | Choose your language | `jobsystem.lang` |
| `/job prestige` | Prestige at max level | `jobsystem.prestige` |
| `/job boost [multiplier] [minutes\|off]` | Show or start a global XP boost | `jobsystem.boost` |
| `/job reload` | Reload config and language files | `jobsystem.reload` |
| `/job admin set <player> <job>` | Set a player's job | `jobsystem.admin.set` |
| `/job admin reset <player>` | Remove a player's job | `jobsystem.admin.reset` |
| `/job admin addxp <player> <amount>` | Give XP | `jobsystem.admin.addxp` |
| `/job admin setlevel <player> <level>` | Set level | `jobsystem.admin.setlevel` |

Job names can be typed in English or in the player's language.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `jobsystem.use` | everyone | Groups `list`, `join`, `leave`, `info`, `benefits`, `top`, `lang`, `prestige` |
| `jobsystem.info.others` | op | View other players' jobs |
| `jobsystem.reload` | op | Reload the plugin |
| `jobsystem.boost` | op | Manage global XP boosts |
| `jobsystem.bypass.cooldown` | op | Ignore the rejoin cooldown |
| `jobsystem.admin` | op | Groups all `jobsystem.admin.*` permissions |
| `jobsystem.*` | op | Everything |

## Configuration

Everything is configured in `plugins/JobSystem/config.yml` (run `/job reload` after editing; every option is commented in the file). Missing options fall back to the bundled defaults.

| Section | What you can configure |
|---|---|
| General | `default-language`, `use-client-language`, `rejoin-cooldown-seconds`, `xp-multiplier`, `disabled-worlds`, `top-size` |
| Anti-exploit | `ignore-player-placed`, `builder-unique-blocks`, `placed-block-cache-size` |
| `levels.xp-required` | XP needed per level; the number of entries is the max level |
| `prestige`, `rewards` | Prestige limits/bonus/commands and milestone rewards |
| `jobs` | Job names, IDs, `enabled` and `perks` toggle per job |
| `xp` | XP awarded for each action |
| `actions` | Which blocks/items/entities count (ore and log patterns, stone list, gear regex, hunter/butcher entity lists, mature crops only) |
| `perks` | All perk values: chance per level, bonus XP, Haste tiers, food, Fisher bite speed, Hunter damage, Builder refund and fall damage, Blacksmith Unbreaking/Mending, Brewer regeneration |
| `display` | Action bar on/off, bar length, character and colors |
| `level-up` | Title, chat perk list, sound name, volume and pitch |

Texts are not in the config; edit the language files (see below).

## Languages

Language files live in `plugins/JobSystem/lang/`. English, German, French and Spanish are included. Messages use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting.

To add a language, copy `en.yml` to e.g. `it.yml`, translate it and run `/job reload`. Missing keys fall back to English. Players switch language with `/job lang <code>`, or `/job lang auto` to follow their client language.

## Data

Job definitions, player jobs, language choices and leave times are stored in `plugins/JobSystem/jobs.yml`.

## Developer API

```java
JobSystem plugin = (JobSystem) Bukkit.getPluginManager().getPlugin("JobSystem");
JobManager jobs = plugin.getJobManager();

jobs.getPlayerJob(player);               // PlayerJob (job, level, experience)
jobs.addExperience(player, "Miner", 25); // true if the player levelled up
```
