package org.derpcraft.liminal.effects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages flickering fluorescent light effects for the Liminal world.
 *
 * <p>Tracks which lantern positions should flicker and manages per-player
 * {@link FlickerTask} instances that send block change packets to create
 * the visual flicker effect without modifying actual blocks.</p>
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>During world generation, the level engine marks configured shares of lights
 *       as flickering by calling {@link #markFlickering(Location)}</li>
 *   <li>When a player enters the Liminal world, a {@link FlickerTask} is
 *       started for them via {@link #startFlickering(Player)}</li>
 *   <li>The task finds nearby flickering lanterns and sends packet-based
 *       block changes to make them appear to flicker</li>
 *   <li>When the player leaves, the task is cancelled via {@link #stopFlickering(Player)}</li>
 * </ol>
 *
 * @see FlickerTask
 */
public class FlickerManager {

    /**
     * A registered flickering light: where it is, what it looks like when lit,
     * and what it briefly turns into while flickering.
     *
     * @param location   the light's location
     * @param onMaterial the block material while lit
     * @param offMaterial the block material shown during a flicker (packet-only)
     */
    public record FlickerEntry(Location location, Material onMaterial, Material offMaterial) {
    }

    /** The plugin instance. */
    private final JavaPlugin plugin;

    /** Map of flickering light locations to their on/off materials (populated during world generation). */
    private final Map<Location, FlickerEntry> flickeringLights = new ConcurrentHashMap<>();

    /** Map of player UUID to their active flicker task. */
    private final Map<UUID, FlickerTask> playerTasks = new ConcurrentHashMap<>();

    /** Map of player UUID to their active buzz task. */
    private final Map<UUID, BuzzTask> buzzTasks = new ConcurrentHashMap<>();

    /**
     * Constructs a new flicker manager.
     *
     * @param plugin the plugin instance
     */
    public FlickerManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Marks a sea lantern location as flickering.
     *
     * <p>Called during world generation to register which sea lanterns should
     * have the flicker effect. The location is stored in a concurrent map for
     * thread-safe access from generation threads.</p>
     *
     * @param location the lantern location to mark
     */
    public void markFlickering(Location location) {
        markFlickering(location, Material.SEA_LANTERN, Material.GRAY_CONCRETE);
    }

    /**
     * Marks a light location as flickering with explicit on/off materials.
     *
     * <p>Used for level-specific lights: e.g. Level 3 registers redstone torches
     * that briefly "die" (swap to air) during a flicker. Only decorative lights
     * may be registered here &mdash; anything powering redstone circuitry must
     * never flicker.</p>
     *
     * @param location    the light location to mark
     * @param onMaterial  the block material while lit
     * @param offMaterial the block material shown during a flicker
     */
    public void markFlickering(Location location, Material onMaterial, Material offMaterial) {
        flickeringLights.put(location, new FlickerEntry(location, onMaterial, offMaterial));
    }

    /**
     * Removes a light location from the flickering set.
     *
     * <p>Called when a flickering light is broken by a player. This ensures
     * the flicker effect stops being applied to the broken location.</p>
     *
     * @param location the light location to remove
     */
    public void removeFlickering(Location location) {
        flickeringLights.remove(location);
    }

    /**
     * Returns whether a location is marked as flickering.
     *
     * @param location the location to check
     * @return true if the location should flicker
     */
    public boolean isFlickering(Location location) {
        return flickeringLights.containsKey(location);
    }

    /**
     * Returns all flickering light locations.
     *
     * @return unmodifiable view of flickering locations
     */
    public Set<Location> getFlickeringLanterns() {
        return Set.copyOf(flickeringLights.keySet());
    }

    /**
     * Returns all registered flicker entries (location + on/off materials).
     *
     * @return collection of flicker entries
     */
    public Collection<FlickerEntry> getFlickeringEntries() {
        return flickeringLights.values();
    }

    /**
     * Starts the flicker effect for a player.
     *
     * <p>Creates and schedules a new {@link FlickerTask} for the player that
     * will periodically send block change packets to make nearby flickering
     * lanterns appear to flicker. Also starts a {@link BuzzTask} to play
     * continuous ambient buzzing.</p>
     *
     * @param player the player to start flickering for
     */
    public void startFlickering(Player player) {
        if (playerTasks.containsKey(player.getUniqueId())) {
            return; // Already running
        }

        FlickerTask task = new FlickerTask(this, player);
        playerTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 3L); // Every 3 ticks

        BuzzTask buzzTask = new BuzzTask(this, player);
        buzzTasks.put(player.getUniqueId(), buzzTask);
        buzzTask.start();
    }

    /**
     * Stops the flicker effect for a player.
     *
     * <p>Cancels the player's {@link FlickerTask} and {@link BuzzTask}, and restores
     * any blocks that were mid-flicker to their original state.</p>
     *
     * @param player the player to stop flickering for
     */
    public void stopFlickering(Player player) {
        FlickerTask task = playerTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            task.restoreAll();
        }

        BuzzTask buzzTask = buzzTasks.remove(player.getUniqueId());
        if (buzzTask != null) {
            buzzTask.cancel();
        }
    }

    /**
     * Stops all flicker and buzz tasks.
     *
     * <p>Called during plugin shutdown to clean up all active tasks.</p>
     */
    public void shutdown() {
        for (FlickerTask task : playerTasks.values()) {
            task.cancel();
            task.restoreAll();
        }
        playerTasks.clear();

        for (BuzzTask task : buzzTasks.values()) {
            task.cancel();
        }
        buzzTasks.clear();
    }

    /**
     * Returns the number of active flicker tasks.
     *
     * @return count of players with active flicker effects
     */
    public int getActiveTaskCount() {
        return playerTasks.size();
    }

    /**
     * Returns the plugin instance.
     *
     * @return the plugin
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }
}
