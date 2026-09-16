package org.derpcraft.liminal.config;

import org.bukkit.Material;

/**
 * Stairwell settings for a multi-floor level (parsed from
 * {@code layout.stairwell} in the level file).
 *
 * <p>When present, rooms roll the configured chance to receive a vertical
 * circulation shaft connecting the current floor to the one above. Two
 * styles are supported:</p>
 * <ul>
 *   <li><b>{@code spiral}</b> &mdash; a corner-anchored spiral stairwell
 *       (3x3 shaft, central pillar capped with the light block)</li>
 *   <li><b>{@code ramp}</b> &mdash; a straight 3-wide stair run rising from
 *       the room's centre, with the next deck's floor carved open where the
 *       ramp emerges (parking-garage deck cut-through)</li>
 * </ul>
 *
 * @param chance   probability (0.0&ndash;1.0) that a room gets a shaft
 * @param stairs   stair block used for the steps
 * @param landing  full block used for the ramp landings (falls back to a
 *                 name-derived match of {@code stairs}, then to {@code stairs})
 * @param capLight light block capping the spiral pillar (unused by ramps)
 * @param style    the shaft style: {@code spiral} or {@code ramp}
 */
public record StairwellSpec(double chance, Material stairs, Material landing, Material capLight, String style) {

    /** Spiral stairwell style constant. */
    public static final String STYLE_SPIRAL = "spiral";

    /** Straight ramp style constant. */
    public static final String STYLE_RAMP = "ramp";

    /**
     * Resolves the ramp's landing block: the configured {@code landing}
     * material when set, else the stair material's base block by name
     * ({@code STONE_BRICK_STAIRS} &rarr; {@code STONE_BRICKS}), else the stair
     * material itself.
     */
    public Material landingBlock() {
        if (landing != null) return landing;
        String base = stairs.name().replace("_STAIRS", "");
        Material match = Material.matchMaterial(base + "S");
        if (match == null) match = Material.matchMaterial(base);
        if (match == null || !match.isBlock()) match = stairs;
        return match;
    }

    /** True when this spec selects the ramp style. */
    public boolean isRamp() {
        return STYLE_RAMP.equals(style);
    }
}
