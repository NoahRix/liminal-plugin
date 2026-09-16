package org.derpcraft.liminal.generator.features;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.derpcraft.liminal.config.FeatureSpec;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.generator.levels.LiminalLevel;

/**
 * Parking deck trim: curbs along the room's perimeter and bumpers at intervals
 * along the walls. Curbs are a 1-block-high step up from the floor; bumpers are
 * yellow concrete blocks marking parking boundaries.
 *
 * <p>Level file parameters:</p>
 * <ul>
 *   <li>{@code curb-material} &ndash; the curb block (default {@code POLISHED_ANDESITE})</li>
 *   <li>{@code bumper-material} &ndash; the bumper block (default {@code YELLOW_CONCRETE})</li>
 *   <li>{@code bumper-spacing} &ndash; spacing between bumpers along walls (default {@code 8})</li>
 * </ul>
 */
public final class DeckTrimFeature implements IntegralFeature {

    @Override
    public String type() {
        return "deck-trim";
    }

    @Override
    public void generate(ChunkGenerator.ChunkData chunkData, LiminalLevel.RoomLayout layout,
                         int floorY, int ceilingY, int chunkStartX, int chunkStartZ,
                         LevelConfig config, long seed, FeatureSpec spec, LiminalLevel level) {
        if (!layout.overlapsRoom()) return;

        Material curbMat = spec.getMaterial("curb-material", Material.POLISHED_ANDESITE);
        Material bumperMat = spec.getMaterial("bumper-material", Material.YELLOW_CONCRETE);
        int bumperSpacing = Math.max(2, spec.getInt("bumper-spacing", 8));

        // Curbs along the interior-adjacent line of each wall (1 block high)
        placeCurbLine(chunkData, layout, floorY, chunkStartX, chunkStartZ, curbMat, true, layout.roomStartZ() + 1,
                layout.roomStartX() + 1, layout.roomEndX() - 2);
        placeCurbLine(chunkData, layout, floorY, chunkStartX, chunkStartZ, curbMat, true, layout.roomEndZ() - 2,
                layout.roomStartX() + 1, layout.roomEndX() - 2);
        placeCurbLine(chunkData, layout, floorY, chunkStartX, chunkStartZ, curbMat, false, layout.roomStartX() + 1,
                layout.roomStartZ() + 1, layout.roomEndZ() - 2);
        placeCurbLine(chunkData, layout, floorY, chunkStartX, chunkStartZ, curbMat, false, layout.roomEndX() - 2,
                layout.roomStartZ() + 1, layout.roomEndZ() - 2);

        // Bumpers at intervals along the interior-adjacent line (not the wall itself)
        placeBumperLine(chunkData, layout, floorY, chunkStartX, chunkStartZ, bumperMat, bumperSpacing, true,
                layout.roomStartZ() + 1, layout.roomStartX() + 2, layout.roomEndX() - 3);
        placeBumperLine(chunkData, layout, floorY, chunkStartX, chunkStartZ, bumperMat, bumperSpacing, true,
                layout.roomEndZ() - 2, layout.roomStartX() + 2, layout.roomEndX() - 3);
        placeBumperLine(chunkData, layout, floorY, chunkStartX, chunkStartZ, bumperMat, bumperSpacing, false,
                layout.roomStartX() + 1, layout.roomStartZ() + 2, layout.roomEndZ() - 3);
        placeBumperLine(chunkData, layout, floorY, chunkStartX, chunkStartZ, bumperMat, bumperSpacing, false,
                layout.roomEndX() - 2, layout.roomStartZ() + 2, layout.roomEndZ() - 3);
    }

    private void placeCurbLine(ChunkGenerator.ChunkData chunkData, LiminalLevel.RoomLayout layout,
                               int floorY, int chunkStartX, int chunkStartZ, Material mat,
                               boolean runsAlongX, int fixedCoord, int from, int to) {
        for (int along = from; along <= to; along++) {
            int worldX = runsAlongX ? along : fixedCoord;
            int worldZ = runsAlongX ? fixedCoord : along;
            int localX = worldX - chunkStartX;
            int localZ = worldZ - chunkStartZ;
            if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) continue;
            if (chunkData.getType(localX, floorY + 1, localZ) == Material.AIR) {
                chunkData.setBlock(localX, floorY + 1, localZ, mat);
            }
        }
    }

    private void placeBumperLine(ChunkGenerator.ChunkData chunkData, LiminalLevel.RoomLayout layout,
                                 int floorY, int chunkStartX, int chunkStartZ, Material mat,
                                 int spacing, boolean runsAlongX, int fixedCoord, int from, int to) {
        for (int along = from; along <= to; along += spacing) {
            int worldX = runsAlongX ? along : fixedCoord;
            int worldZ = runsAlongX ? fixedCoord : along;
            int localX = worldX - chunkStartX;
            int localZ = worldZ - chunkStartZ;
            if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) continue;
            // Place bumpers at floorY+2 (raised above curbs at floorY+1)
            if (chunkData.getType(localX, floorY + 2, localZ) == Material.AIR) {
                chunkData.setBlock(localX, floorY + 2, localZ, mat);
            }
        }
    }
}
