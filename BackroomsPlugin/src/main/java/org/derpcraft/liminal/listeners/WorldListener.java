package org.derpcraft.liminal.listeners;

import org.derpcraft.liminal.LiminalPlugin;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Handles world-related events for the Liminal world.
 *
 * <p>Provides the following functionality:</p>
 * <ul>
 *   <li><b>World initialisation</b> &ndash; applies the configured difficulty when the
 *       Liminal world is first created.</li>
 *   <li><b>World load</b> &ndash; applies game rules (mob spawning, weather, PvP, hunger)
 *       and re-registers BlueMap integration when the world loads.</li>
 *   <li><b>Eternal midnight</b> &ndash; disables the daylight cycle and periodically
 *       re-snaps the world time to midnight so the Liminal is always dark.</li>
 *   <li><b>Weather control</b> &ndash; cancels weather changes if weather is disabled
 *       in the configuration.</li>
 * </ul>
 *
 * @see LiminalPlugin
 */
public class WorldListener implements Listener {

    /** In-game tick that corresponds to midnight. */
    private static final long MIDNIGHT_TICKS = 18000L;

    /** How often (in ticks) the time is re-snapped to midnight (100 ticks = 5 seconds). */
    private static final long MIDNIGHT_CHECK_PERIOD = 100L;

    /** Reference to the owning plugin instance. */
    private final LiminalPlugin plugin;

    /** Name of the Liminal world. */
    private final String liminalWorldName;

    /** Repeating task that enforces midnight time; never runs concurrently with itself. */
    private BukkitTask midnightTask;

    /**
     * Constructs a new world event listener.
     *
     * @param plugin the owning plugin instance
     */
    public WorldListener(LiminalPlugin plugin) {
        this.plugin = plugin;
        this.liminalWorldName = plugin.getLiminalConfig().getLiminalWorldName();
    }

    /**
     * Applies the configured difficulty when the Liminal world is initialised.
     *
     * @param event the world init event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldInit(WorldInitEvent event) {
        World world = event.getWorld();
        if (!world.getName().equals(liminalWorldName)) return;

        var cfg = plugin.getLiminalConfig();
        try {
            world.setDifficulty(Difficulty.valueOf(cfg.getDifficulty()));
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * Applies game rules and re-registers BlueMap when the Liminal world loads.
     *
     * @param event the world load event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        if (!world.getName().equals(liminalWorldName)) return;

        var cfg = plugin.getLiminalConfig();
        world.setSpawnFlags(cfg.isSpawnMonsters(), cfg.isSpawnAnimals());
        world.setGameRuleValue("doMobSpawning", String.valueOf(cfg.isMobSpawning()));
        world.setGameRuleValue("doWeatherCycle", String.valueOf(cfg.isWeatherEnabled()));
        world.setGameRuleValue("pvp", String.valueOf(cfg.isPvp()));
        world.setGameRuleValue("doHunger", String.valueOf(cfg.isHungerEnabled()));
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setTime(MIDNIGHT_TICKS);
        startMidnightEnforcement(world);

        plugin.getLogger().info("Liminal world '" + liminalWorldName + "' loaded and configured");

        if (plugin.getBlueMapHook() != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getBlueMapHook().unregister();
                plugin.getBlueMapHook().register();
            }, 100L);
        }
    }

    /**
     * Cancels weather changes in the Liminal world if weather is disabled.
     *
     * @param event the weather change event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        World world = event.getWorld();
        if (!world.getName().equals(liminalWorldName)) return;

        if (!plugin.getLiminalConfig().isWeatherEnabled() && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    /**
     * Starts a repeating task that re-snaps the world time to midnight every
     * {@value #MIDNIGHT_CHECK_PERIOD} ticks, so even manually issued
     * {@code /time set} commands are reverted within seconds.
     *
     * @param world the Liminal world to enforce
     */
    private void startMidnightEnforcement(World world) {
        if (midnightTask != null) {
            midnightTask.cancel();
        }
        midnightTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            World liminal = plugin.getServer().getWorld(liminalWorldName);
            if (liminal == null) return;
            if (liminal.getTime() != MIDNIGHT_TICKS) {
                liminal.setTime(MIDNIGHT_TICKS);
            }
        }, MIDNIGHT_CHECK_PERIOD, MIDNIGHT_CHECK_PERIOD);
    }
}
