package org.derpcraft.liminal.generator.features;

import org.bukkit.Material;
import org.derpcraft.liminal.config.FeatureSpec;

import java.util.Random;

/**
 * Floor pool feature: shallow pools of a liquid (water puddles, lava seeps)
 * scattered across room floors.
 *
 * <p>Level file parameters:</p>
 * <ul>
 *   <li>{@code material} &ndash; liquid to place (default {@code WATER})</li>
 *   <li>{@code chance} &ndash; probability per block (default {@code 0.008})</li>
 * </ul>
 */
public final class FloorPoolFeature implements PerBlockFeature {

    @Override
    public String type() {
        return "floor-pool";
    }

    @Override
    public void apply(FeatureContext context, FeatureSpec spec, Random random) {
        double chance = spec.getChance() >= 0 ? spec.getChance() : 0.008;
        if (random.nextDouble() >= chance) return;

        Material material = spec.getMaterial() != null ? spec.getMaterial() : Material.WATER;
        context.chunkData().setBlock(context.localX(), context.floorY() + 1, context.localZ(), material);
    }
}
