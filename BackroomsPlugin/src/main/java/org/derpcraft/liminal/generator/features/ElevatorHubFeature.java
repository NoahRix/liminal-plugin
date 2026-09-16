package org.derpcraft.liminal.generator.features;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.derpcraft.liminal.config.FeatureSpec;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.generator.levels.LiminalLevel;

/**
 * Glass-enclosed elevator hub at the ramp's emergence point.
 *
 * <p>Places a 3×3×3 glass enclosure with an elevator entrance (iron doors + button)
 * at the step-off cell of a ramp. The hub provides a visual landing zone and
 * connects to the ramp below.</p>
 *
 * <p>Level file parameters:</p>
 * <ul>
 *   <li>{@code glass} &ndash; the glass material (default {@code GLASS})</li>
 *   <li>{@code doors} &ndash; the door material (default {@code IRON_BLOCK})</li>
 *   <li>{@code button} &ndash; the call button material (default {@code STONE_BUTTON})</li>
 * </ul>
 */
public final class ElevatorHubFeature implements IntegralFeature {

    @Override
    public String type() {
        return "elevator-hub";
    }

    @Override
    public void generate(ChunkGenerator.ChunkData chunkData, LiminalLevel.RoomLayout layout,
                         int floorY, int ceilingY, int chunkStartX, int chunkStartZ,
                         LevelConfig config, long seed, FeatureSpec spec, LiminalLevel level) {
        int[] ramp = level.getPendingRampAnchor();
        if (ramp == null) return;

        Material glass = spec.getMaterial("glass", Material.GLASS);
        Material doors = spec.getMaterial("doors", Material.IRON_BLOCK);
        Material button = spec.getMaterial("button", Material.STONE_BUTTON);

        // The hub is placed at the ramp's step-off cell (anchor + 8 in Z)
        int anchorX = ramp[0];
        int anchorZ = ramp[1];
        int hubX = anchorX;
        int hubZ = anchorZ + 8;

        // Build a 3×3×3 glass enclosure centered on (hubX, floorY, hubZ)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int worldX = hubX + dx;
                int worldZ = hubZ + dz;
                int localX = worldX - chunkStartX;
                int localZ = worldZ - chunkStartZ;

                if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) continue;

                // Walls: glass from floorY+1 to floorY+3 (3 blocks tall)
                if (dx != 0 || dz != 0) {
                    for (int y = floorY + 1; y <= floorY + 3; y++) {
                        chunkData.setBlock(localX, y, localZ, glass);
                    }
                } else {
                    // Center: air (interior)
                    for (int y = floorY + 1; y <= floorY + 3; y++) {
                        chunkData.setBlock(localX, y, localZ, Material.AIR);
                    }
                }
            }
        }

        // Elevator entrance on the south side (facing +Z)
        int doorX = hubX;
        int doorZ = hubZ + 1;
        int doorLocalX = doorX - chunkStartX;
        int doorLocalZ = doorZ - chunkStartZ;

        if (doorLocalX >= 0 && doorLocalX <= 15 && doorLocalZ >= 0 && doorLocalZ <= 15) {
            // Replace the south glass wall with iron doors (2 blocks tall)
            chunkData.setBlock(doorLocalX, floorY + 1, doorLocalZ, doors);
            chunkData.setBlock(doorLocalX, floorY + 2, doorLocalZ, doors);
            // Leave floorY+3 as glass (lintel)
        }

        // Call button beside the doors (on the east wall, facing west)
        int buttonX = hubX + 1;
        int buttonZ = hubZ;
        int buttonLocalX = buttonX - chunkStartX;
        int buttonLocalZ = buttonZ - chunkStartZ;

        if (buttonLocalX >= 0 && buttonLocalX <= 15 && buttonLocalZ >= 0 && buttonLocalZ <= 15) {
            org.bukkit.block.data.type.Switch data =
                    (org.bukkit.block.data.type.Switch) button.createBlockData();
            data.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.WALL);
            data.setFacing(org.bukkit.block.BlockFace.WEST);
            chunkData.setBlock(buttonLocalX, floorY + 2, buttonLocalZ, data);
        }
    }
}
