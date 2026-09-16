package org.derpcraft.liminal.generator.features;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.derpcraft.liminal.config.FeatureSpec;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.generator.levels.LiminalLevel;

/**
 * Floor drains: iron bars scattered on the floor at low density, simulating
 * drainage grates in the parking deck.
 *
 * <p>Level file parameters:</p>
 * <ul>
 *   <li>{@code material} &ndash; the drain block (default {@code IRON_BARS})</li>
 *   <li>{@code chance} &ndash; probability per floor block (default {@code 0.001})</li>
 * </ul>
 */
public final class FloorDrainFeature implements PerBlockFeature {

    @Override
    public String type() {
        return "floor-drains";
    }

    @Override
    public void apply(FeatureContext context, FeatureSpec spec, java.util.Random random) {
        Material mat = spec.getMaterial("material", Material.IRON_BARS);
        double chance = spec.getChance() >= 0 ? spec.getChance() : 0.001;

        if (random.nextDouble() >= chance) return;
        if (context.chunkData().getType(context.localX(), context.floorY() + 1, context.localZ()) != Material.AIR) return;

        context.chunkData().setBlock(context.localX(), context.floorY() + 1, context.localZ(), mat);
    }
}
