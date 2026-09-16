package org.derpcraft.liminal.generator.features;

import org.bukkit.generator.ChunkGenerator;
import org.derpcraft.liminal.generator.levels.LiminalLevel;

/**
 * Per-block context handed to {@link PerBlockFeature} handlers.
 *
 * @param chunkData the mutable chunk data
 * @param layout    the room layout overlapping this chunk
 * @param localX    local chunk X of the block
 * @param localZ    local chunk Z of the block
 * @param globalX   world X of the block
 * @param globalZ   world Z of the block
 * @param floorY    the Y coordinate of the floor surface
 * @param ceilingY  the Y coordinate of the ceiling surface
 */
public record FeatureContext(
        ChunkGenerator.ChunkData chunkData,
        LiminalLevel.RoomLayout layout,
        int localX, int localZ,
        int globalX, int globalZ,
        int floorY, int ceilingY) {

    /**
     * True when the block lies strictly inside the room (not on a wall or
     * doorway column).
     *
     * @return whether the block is in the room interior
     */
    public boolean insideRoom() {
        return globalX > layout.roomStartX() && globalX < layout.roomEndX() - 1
                && globalZ > layout.roomStartZ() && globalZ < layout.roomEndZ() - 1;
    }
}
