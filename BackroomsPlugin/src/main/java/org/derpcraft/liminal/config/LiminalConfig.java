package org.derpcraft.liminal.config;

import org.derpcraft.liminal.generator.levels.LiminalLevel;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Loads and holds all configuration for the Liminal plugin.
 *
 * <p>Global settings are read from the plugin's {@code config.yml}; each
 * level's configuration lives in its own file under
 * {@code plugins/LiminalGen/levels/} ({@code level0.yml}, {@code level1.yml},
 * ...). Missing level files are recreated from the jar defaults on startup,
 * and a legacy inline {@code levels:} section of {@code config.yml} is
 * migrated into individual files once. Global configuration includes:</p>
 * <ul>
 *   <li><b>World settings</b> &ndash; world name, generation seed, grid cell size</li>
 *   <li><b>Gameplay settings</b> &ndash; mob spawning, loot, hazards, PvP, difficulty</li>
 *   <li><b>BlueMap integration</b> &ndash; map name, markers, overlays</li>
 * </ul>
 *
 * <p>After loading the raw {@link LevelConfig} data objects, this class instantiates
 * the spec-driven {@link LiminalLevel} engine for each enabled level; all
 * per-level generation behaviour comes from the level's YML file.</p>
 *
 * @see LevelConfig
 * @see LiminalLevel
 */
public class LiminalConfig {

    /** Plugin data-folder directory holding per-level YAML files. */
    private static final String LEVELS_DIRECTORY = "levels";

    /** Default level files shipped inside the plugin jar (extracted on first run). */
    private static final List<String> DEFAULT_LEVEL_IDS = List.of("level0", "level1", "level2", "level3");

    /** Reference to the owning plugin instance for config file access. */
    private final JavaPlugin plugin;

    /** Name of the Liminal world (e.g. "liminal"). */
    private String liminalWorldName;

    /** Fixed generation seed; 0 means use the world's random seed. */
    private long generationSeed;

    /** Size of the grid cell used for room placement (in chunks). */
    private int gridCellSize;

    /** Probability (0.0&ndash;1.0) that a stairwell connects two levels. */
    private double stairwellChance;

    /** Width of the transition corridor between adjacent level rings, in blocks. */
    private int transitionWidth;

    /**
     * A pair of adjacent levels and the boundary radius where their rings meet.
     * The transition corridor between them ramps elevation and blends materials.
     *
     * @param from    the inner level (closer to spawn)
     * @param to      the outer level (farther from spawn)
     * @param boundary the ring radius where the two levels meet
     */
    public record TransitionInfo(LevelConfig from, LevelConfig to, double boundary) {
    }

    /** Whether mobs can spawn naturally in the Liminal world. */
    private boolean mobSpawning;

    /** Whether loot generation is enabled. */
    private boolean lootGeneration;

    /** Whether environmental hazards (water, fire, cobwebs) are placed. */
    private boolean hazards;

    /** Whether hunger is enabled in the Liminal world. */
    private boolean hungerEnabled;

    /** Whether PvP is enabled in the Liminal world. */
    private boolean pvp;

    /** World difficulty (e.g. "NORMAL", "HARD"). */
    private String difficulty;

    /** Whether monsters spawn in the Liminal world. */
    private boolean spawnMonsters;

    /** Whether animals spawn in the Liminal world. */
    private boolean spawnAnimals;

    /** Whether weather cycles in the Liminal world. */
    private boolean weatherEnabled;

    /** Whether BlueMap integration is enabled. */
    private boolean blueMapEnabled;

    /** Whether BlueMap markers are shown. */
    private boolean blueMapMarkers;

    /** Whether BlueMap overlays are shown. */
    private boolean blueMapOverlays;

    /** Whether a dedicated BlueMap map is created for the Liminal. */
    private boolean blueMapDedicatedMap;

    /** Name of the BlueMap map for the Liminal. */
    private String blueMapName;

    /** Map of level ID to {@link LevelConfig} data objects. */
    private final Map<String, LevelConfig> levels = new HashMap<>();

    /** Cached list of enabled level configs, sorted by Y descending (topmost first). */
    private List<LevelConfig> enabledLevelsCache = null;

    /** Cached list of enabled level configs, sorted by ring radius ascending (centre first). */
    private List<LevelConfig> radiusSortedCache = null;

    /** Cached map of level ID to enabled {@link LiminalLevel} generator instance. */
    private Map<String, LiminalLevel> levelInstancesCache = null;

    /**
     * Constructs a new configuration loader.
     *
     * @param plugin the owning plugin instance
     */
    public LiminalConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads (or reloads) all configuration from the plugin's {@code config.yml}.
     *
     * <p>This clears any previously cached level data and re-reads every section.
     * Call this after {@code reloadConfig()} to apply changes without restarting.</p>
     */
    public void load() {
        FileConfiguration cfg = plugin.getConfig();

        liminalWorldName = cfg.getString("liminal-world-name", "liminal");
        generationSeed = cfg.getLong("generation.seed", 0);
        gridCellSize = cfg.getInt("generation.grid-cell-size", 16);
        stairwellChance = cfg.getDouble("generation.stairwell-chance", 0.02);

        mobSpawning = cfg.getBoolean("gameplay.mob-spawning", true);
        lootGeneration = cfg.getBoolean("gameplay.loot-generation", true);
        hazards = cfg.getBoolean("gameplay.hazards", true);
        hungerEnabled = cfg.getBoolean("gameplay.hunger-enabled", true);
        pvp = cfg.getBoolean("gameplay.pvp", false);
        difficulty = cfg.getString("gameplay.difficulty", "NORMAL");
        spawnMonsters = cfg.getBoolean("gameplay.spawn-monsters", true);
        spawnAnimals = cfg.getBoolean("gameplay.spawn-animals", false);
        weatherEnabled = cfg.getBoolean("gameplay.weather-enabled", false);

        transitionWidth = cfg.getInt("generation.transition-width", 40);

        blueMapEnabled = cfg.getBoolean("bluemap.enabled", true);
        blueMapMarkers = cfg.getBoolean("bluemap.markers", true);
        blueMapOverlays = cfg.getBoolean("bluemap.overlays", true);
        blueMapDedicatedMap = cfg.getBoolean("bluemap.dedicated-map", true);
        blueMapName = cfg.getString("bluemap.map-name", "Liminal");

        levels.clear();
        enabledLevelsCache = null;
        radiusSortedCache = null;
        levelInstancesCache = null;

        loadLevelFiles(cfg);
    }

    /**
     * Discovers and loads every per-level YAML file from
     * {@code plugins/LiminalGen/levels/}.
     *
     * <p>Each {@code <id>.yml} file defines one level (the filename stem is the
     * level ID). When the directory is empty, the configured {@code levels:}
     * section of {@code config.yml} is migrated into individual files if
     * present; otherwise the default level files shipped in the jar are
     * extracted.</p>
     *
     * @param cfg the plugin's root configuration (used for legacy migration)
     */
    private void loadLevelFiles(FileConfiguration cfg) {
        File dir = new File(plugin.getDataFolder(), LEVELS_DIRECTORY);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create level config directory: " + dir);
        }

        if (listLevelFiles(dir).isEmpty()) {
            ConfigurationSection legacy = cfg.getConfigurationSection("levels");
            if (legacy != null && !legacy.getKeys(false).isEmpty()) {
                migrateLegacyLevels(legacy, dir);
            } else {
                for (String id : DEFAULT_LEVEL_IDS) {
                    try {
                        plugin.saveResource(LEVELS_DIRECTORY + "/" + id + ".yml", false);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Missing default level resource: " + id + ".yml");
                    }
                }
            }
        }

        for (File file : listLevelFiles(dir)) {
            String id = file.getName().substring(0, file.getName().length() - ".yml".length());
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            LevelConfig lc = parseLevel(id, yml);
            levels.put(id, lc);
        }

        String ids = levels.keySet().stream().sorted().collect(Collectors.joining(", "));
        plugin.getLogger().info("Loaded " + levels.size() + " level config(s) from "
                + LEVELS_DIRECTORY + "/: " + ids);
    }

    /**
     * Lists the level YAML files in the given directory, sorted by name.
     *
     * @param dir the levels directory
     * @return the sorted list of {@code .yml} files (excluding dotfiles)
     */
    private List<File> listLevelFiles(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml") && !name.startsWith("."));
        if (files == null) return List.of();
        Arrays.sort(files);
        return Arrays.asList(files);
    }

    /**
     * Splits the legacy inline {@code levels:} section of {@code config.yml}
     * into one file per level, preserving every configured value.
     *
     * @param legacy the legacy {@code levels:} section
     * @param dir    the target levels directory
     */
    private void migrateLegacyLevels(ConfigurationSection legacy, File dir) {
        for (String id : legacy.getKeys(false)) {
            ConfigurationSection ls = legacy.getConfigurationSection(id);
            if (ls == null) continue;

            YamlConfiguration out = new YamlConfiguration();
            for (String path : ls.getKeys(true)) {
                if (ls.isConfigurationSection(path)) continue;
                out.set(path, ls.get(path));
            }

            File target = new File(dir, id + ".yml");
            try {
                out.save(target);
                plugin.getLogger().info("Migrated level '" + id + "' from config.yml to "
                        + LEVELS_DIRECTORY + "/" + id + ".yml");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to migrate level '" + id + "'", e);
            }
        }
    }

    /**
     * Parses a single level configuration file (or legacy config section) into
     * a {@link LevelConfig}.
     *
     * @param id the level ID (filename stem)
     * @param ls the section holding the level's keys at its root
     * @return the parsed level config
     */
    private LevelConfig parseLevel(String id, ConfigurationSection ls) {
        LevelConfig lc = new LevelConfig();
        lc.setId(id);
        lc.setEnabled(ls.getBoolean("enabled", true));
        lc.setName(ls.getString("name", id));
        lc.setMinY(ls.getInt("min-y", -64));
        lc.setMaxY(ls.getInt("max-y", 0));
        lc.setMinRadius(ls.getInt("min-radius", 0));
        lc.setMaxRadius(ls.getInt("max-radius", -1));
        lc.setCeilingHeight(ls.getInt("ceiling-height", 4));

        ConfigurationSection room = ls.getConfigurationSection("room");
        if (room != null) {
            lc.setRoomMinWidth(room.getInt("min-width", 8));
            lc.setRoomMaxWidth(room.getInt("max-width", 24));
            lc.setRoomMinLength(room.getInt("min-length", 8));
            lc.setRoomMaxLength(room.getInt("max-length", 24));
            lc.setWallMaterial(parseMaterial(room, "wall-material", "BIRCH_PLANKS", id));
            lc.setFloorMaterial(parseMaterial(room, "floor-material", "LIGHT_GRAY_CONCRETE", id));
            lc.setCeilingMaterial(parseMaterial(room, "ceiling-material", "SMOOTH_STONE", id));
        }

        ConfigurationSection lighting = ls.getConfigurationSection("lighting");
        if (lighting != null) {
            lc.setLightMaterial(parseMaterial(lighting, "material", "SEA_LANTERN", id));
            lc.setLightSpacing(lighting.getInt("spacing", 5));
        }

        lc.setBrokenTrackChance(ls.getDouble("broken-track-chance", 0.0));
        lc.setCobwebChance(ls.getDouble("cobweb-chance", 0.02));
        lc.setGhostCartChance(ls.getDouble("ghost-cart-chance", 0.3));
        lc.setElevationStep(ls.getInt("elevation-step", 0));
        lc.setRails(ls.getBoolean("rails", false));
        lc.setWallGradient(parseMaterialList(id, ls, "transition.wall-gradient"));
        lc.setFloorGradient(parseMaterialList(id, ls, "transition.floor-gradient"));
        lc.setCeilingGradient(parseMaterialList(id, ls, "transition.ceiling-gradient"));

        // Layout: stacked floors, sub-floor depth, doorway height, stairwells.
        lc.setLayoutMode(ls.getString("layout.mode", "rooms"));
        lc.setFloors(ls.getInt("layout.floors", 1));
        lc.setFloorOffset(ls.getInt("layout.floor-offset", 1));
        lc.setDoorwayHeight(ls.getInt("layout.doorway-height", 2));
        ConfigurationSection stairwell = ls.getConfigurationSection("layout.stairwell");
        if (stairwell != null) {
            String style = stairwell.getString("style", StairwellSpec.STYLE_SPIRAL);
            if (!StairwellSpec.STYLE_SPIRAL.equals(style) && !StairwellSpec.STYLE_RAMP.equals(style)) {
                plugin.getLogger().warning("Unknown stairwell style '" + style + "' in level " + id
                        + " (expected spiral or ramp); using spiral");
                style = StairwellSpec.STYLE_SPIRAL;
            }
            lc.setStairwell(new StairwellSpec(
                    stairwell.getDouble("chance", 0.15),
                    parseMaterial(stairwell, "stairs", "OAK_STAIRS", id),
                    parseMaterial(stairwell, "landing", null, id),
                    parseMaterial(stairwell, "cap-light", "SEA_LANTERN", id),
                    style));
        }

        // Lighting style: ceiling light blocks or floor torches.
        String style = ls.getString("lighting.style", "ceiling");
        if (style == null || (!style.equals("ceiling") && !style.equals("floor-torch"))) {
            plugin.getLogger().warning("Unknown lighting style '" + style + "' in level " + id + " (expected ceiling or floor-torch)");
            style = "ceiling";
        }
        lc.setLightStyle(style);
        lc.setLightFlickerChance(ls.getDouble("lighting.flicker-chance", 0.0));
        lc.setLightDarkRadius(ls.getInt("lighting.dark-radius", 0));

        // Feature list (hanging decor, floor pools, rail networks, ...).
        lc.setFeatures(parseFeatures(id, ls));

        // Deprecated top-level rail keys -> implicit rail-network feature.
        if (lc.isRails() && lc.getFeature("rail-network") == null) {
            FeatureSpec rail = new FeatureSpec("rail-network");
            rail.setParam("cobweb-chance", ls.getDouble("cobweb-chance", 0.02));
            rail.setParam("broken-track-chance", ls.getDouble("broken-track-chance", 0.0));
            rail.setParam("ghost-cart-chance", ls.getDouble("ghost-cart-chance", 0.3));
            lc.getFeatures().add(rail);
            if (!"floor-torch".equals(lc.getLightStyle())) {
                lc.setLightStyle("floor-torch");
                if (lc.getLightFlickerChance() <= 0) lc.setLightFlickerChance(0.25);
                if (lc.getLightDarkRadius() <= 0) lc.setLightDarkRadius(1);
            }
            plugin.getLogger().warning("Level '" + id + "': deprecated top-level rail keys were converted to a "
                    + "rail-network feature; move them into features: in the level file");
        }

        lc.setLootEnabled(ls.getBoolean("loot.enabled", false));
        lc.setLootChance(ls.getDouble("loot.chance", 0.005));
        String container = ls.getString("loot.container", "CHEST");
        lc.setLootContainer(container == null || container.isBlank() ? "CHEST" : container.trim().toUpperCase());
        lc.setLootEntries(parseLootEntries(id, ls));

        lc.setHazardsEnabled(ls.getBoolean("hazards.enabled", false));
        lc.setHazardChance(ls.getDouble("hazards.chance", 0.003));
        lc.setHazardTypes(parseHazardTypes(id, ls));

        return lc;
    }

    /**
     * Parses the {@code loot.entries} list of a level file into
     * {@link LootEntry} values, skipping and warning about unknown materials.
     *
     * @param levelId the level ID for warning messages
     * @param ls      the level's configuration section
     * @return the parsed loot entries (empty when none are configured)
     */
    private List<LootEntry> parseLootEntries(String levelId, ConfigurationSection ls) {
        List<LootEntry> entries = new ArrayList<>();
        for (Map<?, ?> map : ls.getMapList("loot.entries")) {
            Object raw = map.get("material");
            String name = raw == null ? null : String.valueOf(raw).trim();
            Material material = name == null || name.isEmpty() ? null : Material.matchMaterial(name);
            if (material == null) {
                plugin.getLogger().warning("Unknown loot material '" + name + "' in level " + levelId);
                continue;
            }
            int min = intOf(map.get("min"), 1);
            int max = intOf(map.get("max"), min);
            double chance = doubleOf(map.get("chance"), 1.0);
            entries.add(new LootEntry(material, min, max, chance));
        }
        return entries;
    }

    /**
     * Parses the {@code hazards.types} list of a level file into materials,
     * defaulting to all supported hazard types when omitted.
     *
     * @param levelId the level ID for warning messages
     * @param ls      the level's configuration section
     * @return the allowed hazard types (never null; empty is replaced by all)
     */
    private List<Material> parseHazardTypes(String levelId, ConfigurationSection ls) {
        List<String> names = ls.getStringList("hazards.types");
        if (names.isEmpty()) {
            return List.of(Material.WATER, Material.FIRE, Material.COBWEB);
        }

        List<Material> types = new ArrayList<>();
        for (String name : names) {
            Material mat = Material.matchMaterial(name);
            if (mat == Material.WATER || mat == Material.FIRE || mat == Material.COBWEB) {
                types.add(mat);
            } else {
                plugin.getLogger().warning("Unsupported hazard type '" + name + "' in level " + levelId);
            }
        }
        return types;
    }

    /**
     * Parses a level's {@code features:} list into {@link FeatureSpec} values.
     *
     * <p>Each entry must carry a {@code type}; unknown or unresolvable
     * {@code material} values are warned about and skipped. All other keys are
     * preserved verbatim in the spec's parameter map for the feature handler
     * to interpret.</p>
     *
     * @param levelId the level ID for warning messages
     * @param ls      the level's configuration section
     * @return the parsed feature list (possibly empty)
     */
    private List<FeatureSpec> parseFeatures(String levelId, ConfigurationSection ls) {
        List<FeatureSpec> features = new ArrayList<>();
        for (Map<?, ?> map : ls.getMapList("features")) {
            Object typeObj = map.get("type");
            if (typeObj == null) {
                plugin.getLogger().warning("Feature without a 'type' in level " + levelId + " (skipped)");
                continue;
            }

            FeatureSpec spec = new FeatureSpec(String.valueOf(typeObj).trim());

            Object rawMaterial = map.get("material");
            if (rawMaterial != null) {
                Material mat = Material.matchMaterial(String.valueOf(rawMaterial).trim());
                if (mat == null) {
                    plugin.getLogger().warning("Unknown material '" + rawMaterial + "' for feature '"
                            + spec.getType() + "' in level " + levelId);
                } else {
                    spec.setMaterial(mat);
                }
            }

            if (map.get("chance") instanceof Number chance) {
                spec.setChance(chance.doubleValue());
            }

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (key.equals("type") || key.equals("material") || key.equals("chance")) continue;
                spec.setParam(key, entry.getValue());
            }

            features.add(spec);
        }
        return features;
    }

    /**
     * Resolves a single material name from config, warning when unknown.
     *
     * @param section  the section holding the key
     * @param path     the key path
     * @param fallback the value to use when the key is missing
     * @param levelId  the level ID for warning messages
     * @return the parsed material, or {@code null} when unresolvable
     */
    private Material parseMaterial(ConfigurationSection section, String path, String fallback, String levelId) {
        String name = section.getString(path, fallback);
        Material mat = name == null ? null : Material.matchMaterial(name);
        if (mat == null && name != null) {
            plugin.getLogger().warning("Unknown material '" + name + "' in level " + levelId + " (" + path + ")");
        }
        return mat;
    }

    /**
     * Returns the list of enabled level configs, sorted by ring radius ascending.
     *
     * <p>The centre-most level (smallest {@code min-radius}) comes first. Results are
     * cached after the first call and invalidated when {@link #load()} is called again.</p>
     *
     * @return an unmodifiable list of enabled level configs, centre-out
     */
    public List<LevelConfig> getRadiusSortedLevels() {
        if (radiusSortedCache == null) {
            List<LevelConfig> enabled = new ArrayList<>();
            for (LevelConfig lc : levels.values()) {
                if (lc.isEnabled()) enabled.add(lc);
            }
            enabled.sort((a, b) -> Integer.compare(a.getMinRadius(), b.getMinRadius()));
            radiusSortedCache = enabled;
        }
        return radiusSortedCache;
    }

    /**
     * Returns the list of enabled {@link LevelConfig} data objects, sorted by Y descending.
     *
     * <p>The topmost level (highest minY) comes first. Results are cached after the
     * first call and invalidated when {@link #load()} is called again.</p>
     *
     * @return an unmodifiable list of enabled level configs
     */
    public List<LevelConfig> getEnabledLevels() {
        if (enabledLevelsCache == null) {
            List<LevelConfig> enabled = new ArrayList<>(getRadiusSortedLevels());
            enabled.sort((a, b) -> Integer.compare(b.getMinY(), a.getMinY()));
            enabledLevelsCache = enabled;
        }
        return enabledLevelsCache;
    }

    /**
     * Returns the level whose ring contains the given chunk, or {@code null} if the
     * chunk lies outside every configured ring.
     *
     * <p>The world is arranged as concentric "ripple" rings around spawn (chunk 0,0):
     * each chunk belongs to exactly one level, chosen by the Euclidean distance of the
     * chunk's centre from the world origin measured in blocks. A chunk matches a level
     * when {@code minRadius <= dist} and ({@code maxRadius < 0} or {@code dist < maxRadius}).</p>
     *
     * @param chunkX the chunk's X coordinate
     * @param chunkZ the chunk's Z coordinate
     * @return the level config owning this chunk, or {@code null} for unclaimed chunks
     */
    public LevelConfig getLevelForChunk(int chunkX, int chunkZ) {
        double cx = chunkX * 16.0 + 8.0;
        double cz = chunkZ * 16.0 + 8.0;
        double dist = Math.sqrt(cx * cx + cz * cz);

        for (LevelConfig lc : getRadiusSortedLevels()) {
            if (dist >= lc.getMinRadius()
                    && (lc.getMaxRadius() < 0 || dist < lc.getMaxRadius())) {
                return lc;
            }
        }
        return null;
    }

    /**
     * Returns the transition corridor information for a chunk whose centre lies
     * within the corridor band of an adjacent-level boundary, or {@code null} when
     * the chunk is a pure level chunk.
     *
     * <p>The corridor band extends half the transition width beyond each side of
     * the boundary plus a 16-block chunk margin, so every chunk whose cells can
     * reach the blending zone is generated as (or by) the corridor. Transition
     * chunks generate a sloped walkway whose floor, materials and lighting blend
     * smoothly from the inner level to the outer level.</p>
     *
     * @param chunkX the chunk's X coordinate
     * @param chunkZ the chunk's Z coordinate
     * @return the boundary pair for this chunk, or {@code null} for pure level chunks
     */
    public TransitionInfo getTransitionForChunk(int chunkX, int chunkZ) {
        double cx = chunkX * 16.0 + 8.0;
        double cz = chunkZ * 16.0 + 8.0;
        double dist = Math.sqrt(cx * cx + cz * cz);
        double band = getTransitionWidth() / 2.0 + 16.0;

        List<LevelConfig> rings = getRadiusSortedLevels();
        for (int i = 0; i < rings.size() - 1; i++) {
            LevelConfig from = rings.get(i);
            LevelConfig to = rings.get(i + 1);
            if (!from.isEnabled() || !to.isEnabled()) continue;
            double boundary = to.getMinRadius();
            if (Math.abs(dist - boundary) <= band) {
                return new TransitionInfo(from, to, boundary);
            }
        }
        return null;
    }

    /** Returns the width of the transition corridors, in blocks. */
    public int getTransitionWidth() { return transitionWidth; }

    /** Sets the width of the transition corridors, in blocks. */
    public void setTransitionWidth(int transitionWidth) { this.transitionWidth = transitionWidth; }

    /** Parses a material list config key (e.g. a gradient), warning about unknown names. */
    private List<Material> parseMaterialList(String levelId, ConfigurationSection ls, String path) {
        List<Material> out = new ArrayList<>();
        for (String name : ls.getStringList(path)) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) {
                out.add(mat);
            } else {
                plugin.getLogger().warning("Unknown material '" + name + "' in level " + levelId + " (" + path + ")");
            }
        }
        return out;
    }

    /** Converts a config value to an int, falling back when absent or malformed. */
    private int intOf(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    /** Converts a config value to a double, falling back when absent or malformed. */
    private double doubleOf(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    /**
     * Returns the enabled {@link LiminalLevel} generator instances keyed by level ID.
     *
     * <p>Each level config is mapped to its concrete generator class:</p>
     * <ul>
     * </ul>
     *
     * <p>All levels run through the spec-driven {@link LiminalLevel} engine,
     * so new levels can be added purely through configuration.</p>
     *
     * @return an unmodifiable map of level ID to generator instance (enabled levels only)
     */
    public Map<String, LiminalLevel> getLevelInstances() {
        if (levelInstancesCache == null) {
            Map<String, LiminalLevel> instances = new HashMap<>();
            long seed = generationSeed != 0 ? generationSeed : 0;

            for (LevelConfig lc : getRadiusSortedLevels()) {
                instances.put(lc.getId(), createLevelInstance(lc, seed));
            }

            levelInstancesCache = instances;
        }
        return levelInstancesCache;
    }

    /**
     * Returns the generator instance for a single level ID, or {@code null} when the
     * level is unknown or disabled.
     *
     * @param id the level ID (e.g. "level0")
     * @return the level's generator instance, or {@code null}
     */
    public LiminalLevel getLevelInstanceById(String id) {
        return getLevelInstances().get(id);
    }

    /**
     * Returns all enabled {@link LiminalLevel} generator instances.
     *
     * @return a list of enabled level generator instances
     */
    public List<LiminalLevel> getEnabledLevelInstances() {
        return new ArrayList<>(getLevelInstances().values());
    }

    /**
     * Creates the level engine instance for the given config.
     *
     * <p>All levels run through the single spec-driven engine ({@link LiminalLevel});
     * per-level behaviour comes entirely from the level's YML file. Level IDs
     * remain relevant only as seed components and identifiers.</p>
     *
     * @param lc   the level configuration data
     * @param seed the generation seed
     * @return a new level engine instance
     */
    private LiminalLevel createLevelInstance(LevelConfig lc, long seed) {
        return new LiminalLevel(lc, seed);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** Returns the name of the Liminal world. */
    public String getLiminalWorldName() { return liminalWorldName; }

    /** Returns the fixed generation seed (0 means use world seed). */
    public long getGenerationSeed() { return generationSeed; }

    /** Returns the grid cell size for room placement. */
    public int getGridCellSize() { return gridCellSize; }

    /** Returns the stairwell connection probability. */
    public double getStairwellChance() { return stairwellChance; }

    /** Returns whether mob spawning is enabled. */
    public boolean isMobSpawning() { return mobSpawning; }

    /** Returns whether loot generation is enabled. */
    public boolean isLootGeneration() { return lootGeneration; }

    /** Returns whether environmental hazards are enabled. */
    public boolean isHazards() { return hazards; }

    /** Returns whether hunger is enabled. */
    public boolean isHungerEnabled() { return hungerEnabled; }

    /** Returns whether PvP is enabled. */
    public boolean isPvp() { return pvp; }

    /** Returns the world difficulty string. */
    public String getDifficulty() { return difficulty; }

    /** Returns whether monsters spawn. */
    public boolean isSpawnMonsters() { return spawnMonsters; }

    /** Returns whether animals spawn. */
    public boolean isSpawnAnimals() { return spawnAnimals; }

    /** Returns whether weather is enabled. */
    public boolean isWeatherEnabled() { return weatherEnabled; }

    /** Returns whether BlueMap integration is enabled. */
    public boolean isBlueMapEnabled() { return blueMapEnabled; }

    /** Returns whether BlueMap markers are enabled. */
    public boolean isBlueMapMarkers() { return blueMapMarkers; }

    /** Returns whether BlueMap overlays are enabled. */
    public boolean isBlueMapOverlays() { return blueMapOverlays; }

    /** Returns whether a dedicated BlueMap map is used. */
    public boolean isBlueMapDedicatedMap() { return blueMapDedicatedMap; }

    /** Returns the BlueMap map name. */
    public String getBlueMapName() { return blueMapName; }

    /** Returns the raw map of level ID to {@link LevelConfig}. */
    public Map<String, LevelConfig> getLevels() { return levels; }
}
