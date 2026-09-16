package org.derpcraft.liminal.generator.features;

import org.bukkit.generator.ChunkGenerator;
import org.derpcraft.liminal.config.FeatureSpec;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.generator.levels.LiminalLevel;

/**
 * A feature that owns its own generation pass for a chunk (e.g. an entire
 * rail network). Unlike {@link PerBlockFeature} handlers, integral features
 * run once per floor with their own deterministic random stream, so their
 * internal algorithm is free-form.
 */
public interface IntegralFeature {

    /**
     * Returns the feature type key this handler serves (the {@code type}
     * value in the level file's {@code features:} list).
     *
     * @return the feature type key
     */
    String type();

    /**
     * Runs the feature for one floor of the chunk.
     *
     * @param chunkData   the mutable chunk data
     * @param layout      the room layout overlapping this chunk
     * @param floorY      the Y coordinate of the floor surface
     * @param ceilingY    the Y coordinate of the ceiling surface
     * @param chunkStartX world X of the chunk's western edge
     * @param chunkStartZ world Z of the chunk's northern edge
     * @param config      the owning level's configuration
     * @param seed        the global generation seed
     * @param spec        the feature configuration
     * @param level       the owning level instance
     */
    void generate(ChunkGenerator.ChunkData chunkData, LiminalLevel.RoomLayout layout,
                  int floorY, int ceilingY, int chunkStartX, int chunkStartZ,
                  LevelConfig config, long seed, FeatureSpec spec, LiminalLevel level);
}
