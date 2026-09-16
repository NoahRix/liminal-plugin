package org.derpcraft.liminal.listeners;

import org.derpcraft.liminal.LiminalPlugin;
import org.derpcraft.liminal.config.LevelConfig;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.derpcraft.liminal.effects.FlickerManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles player-related events in the Liminal world.
 *
 * <p>Provides the following functionality:</p>
 * <ul>
 *   <li><b>Level announcements</b> &ndash; when a player enters the Liminal or crosses
 *       into a different level's Y range, they receive a chat message or action bar
 *       notification with the level name.</li>
 *   <li><b>Respawn handling</b> &ndash; players who die in the Liminal respawn at the
 *       world's spawn location rather than the server's global spawn.</li>
 * </ul>
 *
 * @see LiminalPlugin
 */
public class PlayerListener implements Listener {

    /** Reference to the owning plugin instance. */
    private final LiminalPlugin plugin;

    /** Name of the Liminal world. */
    private final String liminalWorldName;

    /** Last level shown on a player's action bar, keyed by player UUID. */
    private final Map<UUID, String> shownLevels = new HashMap<>();

    /**
     * Constructs a new player event listener.
     *
     * @param plugin the owning plugin instance
     */
    public PlayerListener(LiminalPlugin plugin) {
        this.plugin = plugin;
        this.liminalWorldName = plugin.getLiminalConfig().getLiminalWorldName();
    }

    /**
     * Sends a level announcement when a player joins while in the Liminal world.
     *
     * @param event the player join event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        World liminalWorld = plugin.getServer().getWorld(liminalWorldName);
        if (liminalWorld != null && player.getWorld() == liminalWorld) {
            sendLevelMessage(player);
            startFlickerEffect(player);
            LevelConfig level = plugin.getLiminalConfig().getLevelForChunk(
                    player.getLocation().getBlockX() >> 4,
                    player.getLocation().getBlockZ() >> 4);
            if (level != null) {
                shownLevels.put(player.getUniqueId(), level.getId());
            }
        }
    }

    /**
     * Handles player quit to stop flicker effects and forget the last shown level.
     *
     * @param event the player quit event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopFlickerEffect(event.getPlayer());
        shownLevels.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Handles player teleport to start/stop flicker effects based on destination world.
     *
     * @param event the player teleport event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        World toWorld = event.getTo() != null ? event.getTo().getWorld() : null;
        World liminalWorld = plugin.getServer().getWorld(liminalWorldName);

        if (toWorld == liminalWorld) {
            startFlickerEffect(player);
        } else {
            stopFlickerEffect(player);
        }
    }

    /**
     * Starts the flicker effect for a player if the flicker manager is available.
     *
     * @param player the player
     */
    private void startFlickerEffect(Player player) {
        FlickerManager flickerManager = LiminalPlugin.getFlickerManager();
        if (flickerManager != null) {
            flickerManager.startFlickering(player);
        }
    }

    /**
     * Stops the flicker effect for a player if the flicker manager is available.
     *
     * @param player the player
     */
    private void stopFlickerEffect(Player player) {
        FlickerManager flickerManager = LiminalPlugin.getFlickerManager();
        if (flickerManager != null) {
            flickerManager.stopFlickering(player);
        }
    }

    /**
     * Redirects respawn to the Liminal spawn when a player dies in the Liminal world.
     *
     * @param event the player respawn event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        World liminalWorld = plugin.getServer().getWorld(liminalWorldName);
        if (liminalWorld != null && player.getWorld() == liminalWorld) {
            event.setRespawnLocation(liminalWorld.getSpawnLocation());
        }
    }

    /**
     * Shows the current level name on the action bar when a player crosses a chunk border
     * in the Liminal world into a different level.
     *
     * <p>Only resends when the level ring actually changes, so the action bar no longer
     * spams while walking within a level.</p>
     *
     * @param event the player move event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() >> 4 == event.getTo().getBlockX() >> 4
                && event.getFrom().getBlockZ() >> 4 == event.getTo().getBlockZ() >> 4) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(liminalWorldName)) return;

        // Levels are arranged as ripple rings around spawn, so the level depends
        // on the player's chunk, not their Y coordinate.
        LevelConfig level = plugin.getLiminalConfig().getLevelForChunk(
                player.getLocation().getBlockX() >> 4,
                player.getLocation().getBlockZ() >> 4);
        if (level == null) return;

        UUID playerUuid = player.getUniqueId();
        String levelId = level.getId();
        String shown = shownLevels.get(playerUuid);
        if (levelId.equals(shown)) {
            return;
        }

        shownLevels.put(playerUuid, levelId);
        player.sendActionBar(ChatColor.GOLD + level.getName());
    }

    /**
     * Sends a chat message to the player announcing which level they are in.
     *
     * @param player the player to message
     */
    private void sendLevelMessage(Player player) {
        LevelConfig level = plugin.getLiminalConfig().getLevelForChunk(
                player.getLocation().getBlockX() >> 4,
                player.getLocation().getBlockZ() >> 4);
        if (level != null) {
            player.sendMessage(ChatColor.GOLD + "You are in " + ChatColor.YELLOW + level.getName());
        }
    }
}
