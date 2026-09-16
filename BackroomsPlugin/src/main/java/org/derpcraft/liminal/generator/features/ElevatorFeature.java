package org.derpcraft.liminal.generator.features;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.ChunkGenerator;
import org.derpcraft.liminal.config.FeatureSpec;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.generator.levels.LiminalLevel;

import java.util.Random;

/**
 * Fake elevator entrances: recessed two-panel iron doors set into room walls,
 * with an iron lintel above and a stone call button beside them. The doors
 * never open — the elevator is as abandoned as everything else.
 *
 * <p>All decisions derive from the room's grid geometry and a per-room seed,
 * so entrances stay identical across the chunk borders a large room spans.</p>
 *
 * <p>Level file parameters:</p>
 * <ul>
 *   <li>{@code chance} &ndash; probability per wall (default {@code 0.25})</li>
 *   <li>{@code doors} &ndash; the door material (default {@code IRON_BLOCK})</li>
 *   <li>{@code button} &ndash; the call button material (default {@code STONE_BUTTON})</li>
 * </ul>
 */
public final class ElevatorFeature implements IntegralFeature {

    @Override
    public String type() {
        return "elevators";
    }

    @Override
    public void generate(ChunkGenerator.ChunkData chunkData, LiminalLevel.RoomLayout layout,
                         int floorY, int ceilingY, int chunkStartX, int chunkStartZ,
                         LevelConfig config, long seed, FeatureSpec spec, LiminalLevel level) {
        if (!layout.overlapsRoom()) {
            return;
        }

        double chance = spec.getChance() >= 0 ? spec.getChance() : 0.25;
        Material doors = spec.getMaterial("doors", Material.IRON_BLOCK);
        Material button = spec.getMaterial("button", Material.STONE_BUTTON);

        long roomSeed = seed
                ^ ((long) layout.roomStartX() * 0x1F2E3D4CL)
                ^ ((long) layout.roomStartZ() * 0x4C3B2A19L)
                ^ config.getIdHashCode() ^ 0x5D4C3B2AL;
        Random wallRand = new Random(roomSeed);

        for (int side = 0; side < 4; side++) {
            if (wallRand.nextDouble() >= chance) continue;
            placeElevator(chunkData, layout, side, floorY, chunkStartX, chunkStartZ, doors, button);
        }
    }

    /**
     * Places one elevator entrance on the given wall: two door columns, a
     * lintel, and a call button mounted on the wall beside the doors.
     *
     * @param chunkData   the mutable chunk data
     * @param layout      the room layout
     * @param side        the wall (0 = north, 1 = south, 2 = west, 3 = east)
     * @param floorY      the Y coordinate of the floor surface
     * @param chunkStartX world X of the chunk's western edge
     * @param chunkStartZ world Z of the chunk's northern edge
     * @param doors       the door material
     * @param button      the call button material
     */
    private void placeElevator(ChunkGenerator.ChunkData chunkData, LiminalLevel.RoomLayout layout,
                               int side, int floorY, int chunkStartX, int chunkStartZ,
                               Material doors, Material button) {
        // Interior-adjacent line and wall line, as in PipesFeature.
        boolean runsAlongX = side < 2;
        int wallCoord = switch (side) {
            case 0 -> layout.roomStartZ();
            case 1 -> layout.roomEndZ() - 1;
            case 2 -> layout.roomStartX();
            default -> layout.roomEndX() - 1;
        };
        int innerCoord = switch (side) {
            case 0 -> layout.roomStartZ() + 1;
            case 1 -> layout.roomEndZ() - 2;
            case 2 -> layout.roomStartX() + 1;
            default -> layout.roomEndX() - 2;
        };
        int from = runsAlongX ? layout.roomStartX() + 1 : layout.roomStartZ() + 1;
        int to = runsAlongX ? layout.roomEndX() - 2 : layout.roomEndZ() - 2;

        int mid = (from + to) / 2;
        int doorA = mid;
        int doorB = Math.min(mid + 1, to);
        if (doorA == doorB) return; // wall too short for a 2-wide entrance

        BlockFace inward = switch (side) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.NORTH;
            case 2 -> BlockFace.EAST;
            default -> BlockFace.WEST;
        };

        for (int y = floorY + 1; y <= floorY + 3; y++) {
            set(chunkData, doorA, wallCoord, y, runsAlongX, chunkStartX, chunkStartZ, doors);
            set(chunkData, doorB, wallCoord, y, runsAlongX, chunkStartX, chunkStartZ, doors);
        }
        // Lintel above the doors.
        set(chunkData, doorA, wallCoord, floorY + 4, runsAlongX, chunkStartX, chunkStartZ, doors);
        set(chunkData, doorB, wallCoord, floorY + 4, runsAlongX, chunkStartX, chunkStartZ, doors);

        // Call button beside the doors, mounted on the wall, facing the room.
        int buttonAlong = doorA - 1 >= from ? doorA - 1 : doorB + 1;
        if (buttonAlong > to) return;
        int buttonWallX = runsAlongX ? buttonAlong : wallCoord;
        int buttonWallZ = runsAlongX ? wallCoord : buttonAlong;
        int buttonInnerX = runsAlongX ? buttonAlong : innerCoord;
        int buttonInnerZ = runsAlongX ? innerCoord : buttonAlong;

        int bx = buttonInnerX - chunkStartX;
        int bz = buttonInnerZ - chunkStartZ;
        if (bx < 0 || bx > 15 || bz < 0 || bz > 15) return;
        // The wall must actually be behind the button position.
        int wx = buttonWallX - chunkStartX;
        int wz = buttonWallZ - chunkStartZ;
        if (wx < 0 || wx > 15 || wz < 0 || wz > 15) return;
        if (chunkData.getType(wx, floorY + 2, wz) == Material.AIR) return;

        org.bukkit.block.data.type.Switch data =
                (org.bukkit.block.data.type.Switch) button.createBlockData();
        data.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.WALL);
        data.setFacing(inward);
        chunkData.setBlock(bx, floorY + 2, bz, data);
    }

    /** Writes a block if the target cell falls inside this chunk. */
    private void set(ChunkGenerator.ChunkData chunkData, int along, int wallCoord, int y,
                     boolean runsAlongX, int chunkStartX, int chunkStartZ, Material material) {
        int x = runsAlongX ? along : wallCoord;
        int z = runsAlongX ? wallCoord : along;
        int lx = x - chunkStartX;
        int lz = z - chunkStartZ;
        if (lx < 0 || lx > 15 || lz < 0 || lz > 15) return;
        chunkData.setBlock(lx, y, lz, material);
    }
}
