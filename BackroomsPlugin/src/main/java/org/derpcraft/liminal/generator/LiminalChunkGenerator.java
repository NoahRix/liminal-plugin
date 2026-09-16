package org.derpcraft.liminal.generator;

import org.derpcraft.liminal.config.LiminalConfig;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.generator.levels.LiminalLevel;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

/**
 * Chunk generator for the Liminal world.
 *
 * <p>This generator delegates the actual block placement to a list of {@link LiminalLevel}
 * instances, one for each enabled level in the configuration. The world is arranged as
 * concentric "ripple" rings around spawn: each chunk belongs to exactly one level, chosen
 * by the chunk centre's distance from the world origin, and that level fills its configured
 * Y range for the whole chunk.</p>
 *
 * <h2>Generation flow</h2>
 * <ol>
 *   <li>Determine which level's ring contains this chunk ({@link LiminalConfig#getLevelForChunk})</li>
 *   <li>Call {@link LiminalLevel#generate} to produce all blocks for that level</li>
 *   <li>Place a bedrock layer at the world's minimum Y</li>
 * </ol>
 *
 * <p>The generator also provides a safe spawn location that places players 2 blocks
 * above the floor of the innermost level, preventing them from spawning inside blocks.</p>
 *
 * @see LiminalLevel
 * @see LiminalConfig
 */
public class LiminalChunkGenerator extends ChunkGenerator {

    /** Plugin configuration holding world settings and level definitions. */
    private final LiminalConfig config;

    /** Biome provider that returns THE_VOID for all positions (no natural biome features). */
    private final LiminalBiomeProvider biomeProvider;

    /** Generates the transition corridors between adjacent level rings. */
    private final TransitionZoneGenerator transitionGenerator;

    /**
     * Constructs a new Liminal chunk generator.
     *
     * @param config the plugin configuration containing level definitions and world settings
     */
    public LiminalChunkGenerator(LiminalConfig config) {
        this.config = config;
        this.biomeProvider = new LiminalBiomeProvider();
        this.transitionGenerator = new TransitionZoneGenerator(config,
                config.getGenerationSeed() != 0 ? config.getGenerationSeed() : 0);
    }

    /**
     * Generates the noise (base blocks) for a chunk by delegating to the level whose
     * ring contains this chunk.
     *
     * <p>The world is arranged as concentric "ripple" rings around spawn: the level
     * generating in a chunk is chosen by the chunk centre's distance from the world
     * origin (see {@link LiminalConfig#getLevelForChunk}). The chosen level then
     * fills its configured Y range with floors, rooms, walls, lighting, and special
     * features. Chunks outside every ring stay empty (bedrock only).</p>
     *
     * @param worldInfo information about the world being generated
     * @param random    the world-specific random (not used; levels use seeded randomness)
     * @param chunkX    the chunk's X coordinate
     * @param chunkZ    the chunk's Z coordinate
     * @param chunkData the mutable chunk data to write blocks into
     */
    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random,
                              int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        long seed = config.getGenerationSeed() != 0 ? config.getGenerationSeed() : worldInfo.getSeed();
        int worldMinY = worldInfo.getMinHeight();
        int worldMaxY = worldInfo.getMaxHeight();

        int chunkStartX = chunkX * 16;
        int chunkStartZ = chunkZ * 16;
        int chunkEndX = chunkStartX + 16;
        int chunkEndZ = chunkStartZ + 16;

        LiminalConfig.TransitionInfo transition = config.getTransitionForChunk(chunkX, chunkZ);
        if (transition != null) {
            // Corridor chunk: sloped walkway with gradient materials, perimeter
            // walls, signage, and (when the outer level has rails) climbing rail.
            transitionGenerator.generate(chunkData, chunkStartX, chunkStartZ, transition);
        } else {
            LevelConfig ring = config.getLevelForChunk(chunkX, chunkZ);
            if (ring != null && ring.isEnabled()) {
                LiminalLevel level = config.getLevelInstanceById(ring.getId());
                if (level != null) {
                    level.generate(chunkData, chunkStartX, chunkStartZ, chunkEndX, chunkEndZ, worldMinY, worldMaxY);
                    generateCutoffWalls(chunkData, chunkX, chunkZ, ring);
                }
            }
        }

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                chunkData.setBlock(localX, worldMinY, localZ, Material.BEDROCK);
            }
        }
    }

    /**
     * Returns whether vanilla structures (villages, temples, etc.) should generate.
     *
     * <p>Always returns {@code true} to allow the generator to function, though the
     * Liminal world typically has no vanilla structures due to the custom generation.</p>
     *
     * @return {@code true}
     */
    @Override
    public boolean shouldGenerateStructures() {
        return true;
    }

    /**
     * Seals multi-floor levels at their ring cutoff with a wall built flush to
     * the level's own outermost chunk edges.
     *
     * <p>Levels that stack floors (e.g. Level 0's four-storey lobby) would
     * otherwise end at an open edge where their slabs meet the transition
     * corridor: the corridor's own perimeter wall is only corridor-height, so
     * every upper floor spills into open air at the cutoff. This method walks
     * the chunk's neighbours; every face adjacent to a transition chunk gets a
     * wall column running from just above the ground floor's ceiling slab up
     * through the top floor's ceiling &mdash; exactly where the level's slabs
     * end, so there is no gap between structure and wall.</p>
     *
     * <p>Ground level stays untouched: the ground floor flows into the
     * corridor apron and through the corridor's doorway openings as before.
     * Single-floor levels are a no-op (their cutoff equals the corridor's own
     * ceiling). Diagonal-only adjacencies (a corridor chunk touching only the
     * chunk corner) get a single corner column so players cannot squeeze
     * diagonally past the wall.</p>
     *
     * @param chunkData the mutable chunk data
     * @param chunkX    the chunk's X coordinate
     * @param chunkZ    the chunk's Z coordinate
     * @param level     this chunk's level configuration
     */
    private void generateCutoffWalls(@NotNull ChunkData chunkData, int chunkX, int chunkZ, @NotNull LevelConfig level) {
        int baseY = groundCeilingSlabY(level) + 1;
        int topY = topFloorCeilingY(level);
        if (topY < baseY) return; // single-floor levels are sealed by the corridor wall

        boolean north = isTransitionChunk(chunkX, chunkZ - 1);
        boolean south = isTransitionChunk(chunkX, chunkZ + 1);
        boolean west = isTransitionChunk(chunkX - 1, chunkZ);
        boolean east = isTransitionChunk(chunkX + 1, chunkZ);
        boolean northEast = isTransitionChunk(chunkX + 1, chunkZ - 1);
        boolean northWest = isTransitionChunk(chunkX - 1, chunkZ - 1);
        boolean southEast = isTransitionChunk(chunkX + 1, chunkZ + 1);
        boolean southWest = isTransitionChunk(chunkX - 1, chunkZ + 1);

        if (!north && !south && !west && !east
                && !northEast && !northWest && !southEast && !southWest) {
            return; // interior chunk
        }

        Material wallMat = level.getWallMaterial();

        if (north) fillWallFace(chunkData, 0, 0, 15, 0, baseY, topY, wallMat);
        if (south) fillWallFace(chunkData, 0, 15, 15, 15, baseY, topY, wallMat);
        if (west) fillWallFace(chunkData, 0, 0, 0, 15, baseY, topY, wallMat);
        if (east) fillWallFace(chunkData, 15, 0, 15, 15, baseY, topY, wallMat);

        // Corner pinches: a corridor chunk touching only this chunk's corner.
        if (!east && !south && southEast) fillWallFace(chunkData, 15, 15, 15, 15, baseY, topY, wallMat);
        if (!east && !north && northEast) fillWallFace(chunkData, 15, 0, 15, 0, baseY, topY, wallMat);
        if (!west && !south && southWest) fillWallFace(chunkData, 0, 15, 0, 15, baseY, topY, wallMat);
        if (!west && !north && northWest) fillWallFace(chunkData, 0, 0, 0, 0, baseY, topY, wallMat);
    }

    /**
     * True when the given neighbour chunk belongs to a transition corridor.
     *
     * @param chunkX the neighbour chunk's X coordinate
     * @param chunkZ the neighbour chunk's Z coordinate
     * @return whether the neighbour is a corridor chunk
     */
    private boolean isTransitionChunk(int chunkX, int chunkZ) {
        return config.getTransitionForChunk(chunkX, chunkZ) != null;
    }

    /**
     * Fills a wall between two chunk-local corners (inclusive), spanning the
     * given Y range.
     *
     * @param chunkData the mutable chunk data
     * @param fromX     local X of one corner
     * @param fromZ     local Z of one corner
     * @param toX       local X of the other corner
     * @param toZ       local Z of the other corner
     * @param baseY     the lowest wall Y (inclusive)
     * @param topY      the highest wall Y (inclusive)
     * @param material  the wall material
     */
    private void fillWallFace(@NotNull ChunkData chunkData, int fromX, int fromZ, int toX, int toZ,
                              int baseY, int topY, @NotNull Material material) {
        for (int x = Math.min(fromX, toX); x <= Math.max(fromX, toX); x++) {
            for (int z = Math.min(fromZ, toZ); z <= Math.max(fromZ, toZ); z++) {
                for (int y = baseY; y <= topY; y++) {
                    chunkData.setBlock(x, y, z, material);
                }
            }
        }
    }

    /**
     * Returns the Y of the given level's ground floor's ceiling slab.
     *
     * @param level the level configuration
     * @return the ground ceiling slab Y
     */
    private int groundCeilingSlabY(LevelConfig level) {
        return level.getMinY() + level.getElevationStep() + level.getFloorOffset() + level.getCeilingHeight();
    }

    /**
     * Returns the ceiling Y of the given level's topmost floor (its full
     * structural height, including stacked floors and the elevation step).
     *
     * @param level the level configuration
     * @return the top floor's ceiling slab Y
     */
    private int topFloorCeilingY(LevelConfig level) {
        int floorHeight = level.getCeilingHeight() + 1;
        return level.getMinY() + level.getElevationStep()
                + (level.getFloors() - 1) * floorHeight
                + level.getFloorOffset() + level.getCeilingHeight();
    }

    /**
     * Returns the list of block populators for this generator.
     *
     * <p>The populator fills in station sign text and loot on Level 3, and
     * places ambient loot/hazards in the other levels' rooms.</p>
     *
     * @param world the world being generated
     * @return the Liminal block populator
     */
    @Override
    public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return List.of(new LiminalPopulator(config));
    }

    /** {@inheritDoc} */
    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random,
                                int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
    }

    /** {@inheritDoc} */
    @Override
    public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random,
                                int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
    }

    /** {@inheritDoc} */
    @Override
    public void generateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random,
                              int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
    }

    /**
     * Returns the base height for heightmap calculations.
     *
     * <p>Returns a fixed value of {@code worldMinY + 10} since the Liminal world
     * has no natural terrain height variation.</p>
     *
     * @param worldInfo information about the world
     * @param random    the world-specific random
     * @param x         the block X coordinate
     * @param z         the block Z coordinate
     * @param heightMap the heightmap type being queried
     * @return a fixed base height
     */
    @Override
    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random,
                             int x, int z, @NotNull org.bukkit.HeightMap heightMap) {
        return worldInfo.getMinHeight() + 10;
    }

    /**
     * Returns the biome provider for this generator.
     *
     * <p>The Liminal world uses {@link LiminalBiomeProvider} which returns
     * {@code THE_VOID} for all positions, preventing natural biome features.</p>
     *
     * @param worldInfo information about the world
     * @return the biome provider
     */
    @Override
    public @NotNull BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return biomeProvider;
    }

    /** {@inheritDoc} */
    @Override
    public boolean shouldGenerateNoise() { return false; }
    /** {@inheritDoc} */
    @Override
    public boolean shouldGenerateSurface() { return false; }
    /** {@inheritDoc} */
    @Override
    public boolean shouldGenerateBedrock() { return false; }
    /** {@inheritDoc} */
    @Override
    public boolean shouldGenerateCaves() { return false; }
    /** {@inheritDoc} */
    @Override
    public boolean shouldGenerateDecorations() { return false; }

    /**
     * Returns whether mobs should spawn in the Liminal world.
     *
     * @return {@code true} if mob spawning is enabled in the configuration
     */
    @Override
    public boolean shouldGenerateMobs() { return config.isMobSpawning(); }

    /**
     * Returns a safe spawn location for players entering the Liminal world.
     *
     * <p>The spawn point sits in the centre chunk (0,0), which always belongs to the
     * innermost ring (Level 0). The player's feet are placed one block above that
     * level's floor surface, at the centre of the spawn chunk (8.5, 8.5).</p>
     *
     * @param world  the Liminal world
     * @param random the world-specific random
     * @return a safe spawn location on the innermost level's floor
     */
    @Override
    public @NotNull Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        int spawnY = world.getMinHeight() + 4;

        LevelConfig centreConfig = config.getLevelForChunk(0, 0);
        LiminalLevel centreLevel = centreConfig != null
                ? config.getLevelInstanceById(centreConfig.getId()) : null;

        if (centreLevel != null) {
            // Floor surface includes the level's elevation step; feet one block above it.
            spawnY = centreLevel.getConfig().getFloorSurfaceY() + 1;
        }

        return new Location(world, 8.5, spawnY, 8.5);
    }

    /**
     * Returns the plugin configuration used by this generator.
     *
     * @return the Liminal configuration
     */
    public LiminalConfig getConfig() {
        return config;
    }
}
