package org.derpcraft.liminal.generator.features;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.ChunkGenerator;
import org.derpcraft.liminal.config.FeatureSpec;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.generator.levels.LiminalLevel;

import java.util.Random;

/**
 * Deck number signs ({@code P1}, {@code P2}, ...) mounted on parking garage
 * walls. The generator places the wall signs facing the room; the populator
 * recognises them and writes the deck number (see
 * {@code LiminalPopulator#decorateGarageSigns}), the same split the rails
 * feature uses for its station messages.
 *
 * <p>Level file parameters:</p>
 * <ul>
 *   <li>{@code chance} &ndash; probability per wall (default {@code 0.4})</li>
 *   <li>{@code material} &ndash; the wall sign material (default {@code OAK_WALL_SIGN})</li>
 *   <li>{@code height} &ndash; height above the deck floor (default {@code 2})</li>
 * </ul>
 */
public final class DeckSignsFeature implements IntegralFeature {

    @Override
    public String type() {
        return "deck-signs";
    }

    @Override
    public void generate(ChunkGenerator.ChunkData chunkData, LiminalLevel.RoomLayout layout,
                         int floorY, int ceilingY, int chunkStartX, int chunkStartZ,
                         LevelConfig config, long seed, FeatureSpec spec, LiminalLevel level) {
        if (!layout.overlapsRoom()) {
            return;
        }

        double chance = spec.getChance() >= 0 ? spec.getChance() : 0.4;
        Material material = spec.getMaterial("material", Material.OAK_WALL_SIGN);
        int height = Math.max(1, spec.getInt("height", 2));

        long roomSeed = seed
                ^ ((long) layout.roomStartX() * 0x2B3C4D5EL)
                ^ ((long) layout.roomStartZ() * 0x5E4D3C2BL)
                ^ config.getIdHashCode() ^ 0x6F7E8D9CL;
        Random wallRand = new Random(roomSeed);

        for (int side = 0; side < 4; side++) {
            if (wallRand.nextDouble() >= chance) continue;

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
            int along = (from + to) / 2;

            // The wall block must still exist behind the sign.
            int wx = runsAlongX ? along : wallCoord;
            int wz = runsAlongX ? wallCoord : along;
            int x = runsAlongX ? along : innerCoord;
            int z = runsAlongX ? innerCoord : along;
            int lx = x - chunkStartX;
            int lz = z - chunkStartZ;
            int wlxx = wx - chunkStartX;
            int wlzz = wz - chunkStartZ;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15) continue;
            if (wlxx >= 0 && wlxx <= 15 && wlzz >= 0 && wlzz <= 15
                    && chunkData.getType(wlxx, floorY + height, wlzz) == Material.AIR) continue;

            BlockFace inward = switch (side) {
                case 0 -> BlockFace.SOUTH;
                case 1 -> BlockFace.NORTH;
                case 2 -> BlockFace.EAST;
                default -> BlockFace.WEST;
            };
            org.bukkit.block.data.type.WallSign data =
                    (org.bukkit.block.data.type.WallSign) material.createBlockData();
            data.setFacing(inward);
            chunkData.setBlock(lx, floorY + height, lz, data);
        }
    }
}
