package org.derpcraft.liminal.generator.features;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.derpcraft.liminal.config.FeatureSpec;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.generator.levels.LiminalLevel;

/**
 * Parking-deck support columns: a regular grid of full-height pillars holding
 * the ceiling. Columns only replace air, so walls, ramps and other features'
 * blocks are never overwritten. Fully deterministic.
 *
 * <p>Level file parameters:</p>
 * <ul>
 *   <li>{@code material} &ndash; the pillar block (default {@code LIGHT_GRAY_CONCRETE})</li>
 *   <li>{@code spacing} &ndash; grid spacing in blocks, both axes (default {@code 6})</li>
 * </ul>
 */
public final class SupportPillarsFeature implements IntegralFeature {

    @Override
    public String type() {
        return "support-pillars";
    }

    @Override
    public void generate(ChunkGenerator.ChunkData chunkData, LiminalLevel.RoomLayout layout,
                         int floorY, int ceilingY, int chunkStartX, int chunkStartZ,
                         LevelConfig config, long seed, FeatureSpec spec, LiminalLevel level) {
        Material material = spec.getMaterial("material", Material.LIGHT_GRAY_CONCRETE);
        int spacing = Math.max(2, spec.getInt("spacing", 6));

        int[] ramp = level.getPendingRampAnchor();

        for (int localX = 0; localX < 16; localX++) {
            if (Math.floorMod(chunkStartX + localX, spacing) != 0) continue;
            for (int localZ = 0; localZ < 16; localZ++) {
                if (Math.floorMod(chunkStartZ + localZ, spacing) != 0) continue;
                if (ramp != null && inRampZone(chunkStartX + localX, chunkStartZ + localZ, ramp)) continue;
                for (int y = floorY + 1; y < ceilingY; y++) {
                    if (chunkData.getType(localX, y, localZ) == Material.AIR) {
                        chunkData.setBlock(localX, y, localZ, material);
                    }
                }
            }
        }
    }

    /**
     * True when the given world column falls inside the ramp's emergence zone
     * (the 3-wide, 8-long trench carved through the next deck's slab).
     */
    private boolean inRampZone(int worldX, int worldZ, int[] ramp) {
        int anchorX = ramp[0];
        int anchorZ = ramp[1];
        return worldX >= anchorX - 1 && worldX <= anchorX + 1
                && worldZ >= anchorZ && worldZ <= anchorZ + 7;
    }
}
