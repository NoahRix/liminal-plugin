package org.derpcraft.liminal;

import org.derpcraft.liminal.commands.LiminalAdminCommand;
import org.derpcraft.liminal.commands.LiminalCommand;
import org.derpcraft.liminal.config.LiminalConfig;
import org.derpcraft.liminal.generator.LiminalChunkGenerator;
import org.derpcraft.liminal.integration.bluemap.BlueMapHook;
import org.derpcraft.liminal.integration.multiworld.MultiWorldHook;
import org.derpcraft.liminal.listeners.PlayerListener;
import org.derpcraft.liminal.listeners.WorldListener;
import org.derpcraft.liminal.listeners.ProtectionListener;
import org.derpcraft.liminal.listeners.LightBreakListener;
import org.derpcraft.liminal.listeners.RailsListener;
import org.derpcraft.liminal.effects.FlickerManager;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;

/**
 * Main plugin class for LiminalGen.
 *
 * <p>LiminalGen is a multi-level Liminal world generator for Paper/Spigot Minecraft
 * servers. It generates vertically-stacked liminal space levels, each with their own
 * aesthetic, room layout, and special features.</p>
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>{@link LiminalConfig} &ndash; loads and holds all configuration from {@code config.yml}</li>
 *   <li>{@link LiminalChunkGenerator} &ndash; orchestrates chunk generation by delegating to levels</li>
 *   <li>{@link org.derpcraft.liminal.generator.levels.LiminalLevel} &ndash; abstract base for level generators</li>
 *   <li>{@link org.derpcraft.liminal.generator.levels.LiminalLevel} &ndash; the spec-driven level engine;
 *       per-level behaviour is defined entirely in each level's YML file</li>
 * </ul>
 *
 * <h2>Integrations</h2>
 * <p>Optional hooks into third-party plugins:</p>
 * <ul>
 *   <li><b>BlueMap</b> &ndash; renders the Liminal as a dedicated 3D map with markers</li>
 *   <li><b>MultiWorld</b> &ndash; manages the Liminal as a separate world</li>
 *   <li><b>WorldGuard</b> &ndash; region-based protection (future)</li>
 * </ul>
 *
 * @see LiminalConfig
 * @see LiminalChunkGenerator
 */
public class LiminalPlugin extends JavaPlugin {

    /** Singleton instance of the plugin. */
    private static LiminalPlugin instance;

    /** Plugin configuration. */
    private LiminalConfig config;

    /** Chunk generator for the Liminal world. */
    private LiminalChunkGenerator chunkGenerator;

    /** Optional BlueMap integration hook. */
    private BlueMapHook blueMapHook;

    /** Optional MultiWorld integration hook. */
    private MultiWorldHook multiWorldHook;

    /** Manages flickering fluorescent light effects. */
    private static FlickerManager flickerManager;

    /**
     * Called when the plugin is enabled.
     *
     * <p>Initialises configuration, creates the chunk generator, sets up optional
     * integrations, registers commands and event listeners.</p>
     */
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = new LiminalConfig(this);
        config.load();

        chunkGenerator = new LiminalChunkGenerator(config);
        flickerManager = new FlickerManager(this);

        PluginManager pm = getServer().getPluginManager();

        if (pm.getPlugin("BlueMap") != null) {
            try {
                blueMapHook = new BlueMapHook(this);
                blueMapHook.register();
                getLogger().info("BlueMap integration enabled");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to hook into BlueMap", e);
            }
        }

        if (pm.getPlugin("MultiWorld") != null) {
            try {
                multiWorldHook = new MultiWorldHook(this);
                multiWorldHook.register();
                getLogger().info("MultiWorld integration enabled");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to hook into MultiWorld", e);
            }
        }

        getCommand("liminal").setExecutor(new LiminalCommand(this));
        getCommand("liminaladmin").setExecutor(new LiminalAdminCommand(this));

        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new WorldListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new LightBreakListener(this), this);
        pm.registerEvents(new RailsListener(this), this);

        getLogger().info("LiminalGen v" + getDescription().getVersion() + " enabled");
    }

    /**
     * Called when the plugin is disabled.
     *
     * <p>Unregisters integrations and cleans up resources.</p>
     */
    @Override
    public void onDisable() {
        if (blueMapHook != null) {
            blueMapHook.unregister();
        }
        if (flickerManager != null) {
            flickerManager.shutdown();
        }
        instance = null;
        getLogger().info("LiminalGen disabled");
    }

    /**
     * Returns the default world generator for the Liminal world.
     *
     * <p>Called by the server when a world is created with {@code -g LiminalGen}.</p>
     *
     * @param worldName the name of the world being created
     * @param id        optional generator ID (unused)
     * @return the chunk generator instance
     */
    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return chunkGenerator;
    }

    /**
     * Returns the singleton plugin instance.
     *
     * @return the plugin instance, or {@code null} if the plugin is not enabled
     */
    public static LiminalPlugin getInstance() {
        return instance;
    }

    /**
     * Returns the plugin configuration.
     *
     * @return the Liminal configuration
     */
    public LiminalConfig getLiminalConfig() {
        return config;
    }

    /**
     * Returns the chunk generator for the Liminal world.
     *
     * @return the chunk generator
     */
    public LiminalChunkGenerator getChunkGenerator() {
        return chunkGenerator;
    }

    /**
     * Returns the BlueMap integration hook, if available.
     *
     * @return the BlueMap hook, or {@code null} if BlueMap is not installed
     */
    public @Nullable BlueMapHook getBlueMapHook() {
        return blueMapHook;
    }

    /**
     * Returns the MultiWorld integration hook, if available.
     *
     * @return the MultiWorld hook, or {@code null} if MultiWorld is not installed
     */
    public @Nullable MultiWorldHook getMultiWorldHook() {
        return multiWorldHook;
    }

    /**
     * Returns the flicker manager for fluorescent light effects.
     *
     * @return the flicker manager, or {@code null} if the plugin is not enabled
     */
    public static @Nullable FlickerManager getFlickerManager() {
        return flickerManager;
    }

    /**
     * Reloads the plugin configuration and recreates the chunk generator.
     *
     * <p>Also re-registers the BlueMap integration to pick up any map configuration
     * changes.</p>
     */
    public void reload() {
        reloadConfig();
        config.load();
        chunkGenerator = new LiminalChunkGenerator(config);
        if (blueMapHook != null) {
            blueMapHook.unregister();
            blueMapHook.register();
        }
    }
}
