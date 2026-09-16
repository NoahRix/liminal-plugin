package org.derpcraft.liminal.generator.features;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.derpcraft.liminal.config.FeatureSpec;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.generator.levels.LiminalLevel;

/**
 * Parking deck floor stripes: straight lines of a marking material laid into
 * the deck floor on a fixed grid (a line every {@code spacing} blocks along
 * X, running the full length of the deck).
 *
 * <p>Only the level's own floor material is repainted, so ramps, wall bases
 * and other features' blocks are never overwritten. Fully deterministic —
 * no randomness — so stripes line up across chunk borders.</p>
 *
 * <p>Level file parameters:</p>
 * <ul>
 *   <li>{@code material} &ndash; the marking block (default {@code WHITE_CONCRETE})</li>
 *   <li>{@code spacing} &ndash; blocks between stall lines (default {@code 4})</li>
 * </ul>
 */
public final class ParkingLinesFeature implements IntegralFeature {

    @Override
    public String type() {
        return "parking-lines";
    }

    @Override
    public void generate(ChunkGenerator.ChunkData chunkData, LiminalLevel.RoomLayout layout,
                         int floorY, int ceilingY, int chunkStartX, int chunkStartZ,
                         LevelConfig config, long seed, FeatureSpec spec, LiminalLevel level) {
        Material material = spec.getMaterial("material", Material.WHITE_CONCRETE);
        int spacing = Math.max(1, spec.getInt("spacing", 4));
        Material floorMaterial = config.getFloorMaterial();

        for (int localX = 0; localX < 16; localX++) {
            if (Math.floorMod(chunkStartX + localX, spacing) != 0) continue;
            for (int localZ = 0; localZ < 16; localZ++) {
                if (chunkData.getType(localX, floorY, localZ) == floorMaterial) {
                    chunkData.setBlock(localX, floorY, localZ, material);
                }
            }
        }
    }
}
