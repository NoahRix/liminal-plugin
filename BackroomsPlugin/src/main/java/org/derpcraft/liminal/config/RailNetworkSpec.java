package org.derpcraft.liminal.config;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Typed view over a level's {@code rail-network} feature spec.
 *
 * <p>Rail network geometry (grid spacing, line offset, power cadence, station
 * cadence, viaduct dimensions), station construction materials, and the
 * supplies found in station chests and ghost minecarts are all read from the
 * level file. Missing values fall back to the historical defaults.</p>
 *
 * @see org.derpcraft.liminal.generator.features.RailNetworkFeature
 */
public final class RailNetworkSpec {

    /** Default station chest contents (the historical hardcoded supplies). */
    public static final List<LootEntry> DEFAULT_SUPPLIES = List.of(
            new LootEntry(Material.RAIL, 4, 13, 0.6),
            new LootEntry(Material.POWERED_RAIL, 2, 7, 0.6),
            new LootEntry(Material.REDSTONE_TORCH, 2, 7, 0.6),
            new LootEntry(Material.REDSTONE, 1, 8, 0.6),
            new LootEntry(Material.COAL, 3, 10, 0.6),
            new LootEntry(Material.BOOK, 1, 2, 0.6));

    /** Default ghost chest-cart contents (the historical hardcoded supplies). */
    public static final List<LootEntry> DEFAULT_CART_SUPPLIES = List.of(
            new LootEntry(Material.RAIL, 4, 11, 0.5),
            new LootEntry(Material.POWERED_RAIL, 2, 5, 0.5),
            new LootEntry(Material.REDSTONE_TORCH, 2, 5, 0.5),
            new LootEntry(Material.REDSTONE, 1, 6, 0.5),
            new LootEntry(Material.COAL, 3, 8, 0.5),
            new LootEntry(Material.BOOK, 1, 1, 0.5),
            new LootEntry(Material.NAME_TAG, 1, 1, 0.5));

    /** The underlying feature spec. */
    private final FeatureSpec spec;

    /**
     * Wraps a rail-network feature spec.
     *
     * @param spec the parsed {@code rail-network} feature entry
     */
    public RailNetworkSpec(FeatureSpec spec) {
        this.spec = spec;
    }

    /** Returns the raw feature spec. */
    public FeatureSpec getSpec() { return spec; }

    /** Returns the grid spacing of the rail network in blocks. */
    public int grid() { return spec.getInt("grid", 32); }

    /** Returns the line offset within each grid cell. */
    public int lineOffset() { return spec.getInt("line-offset", 16); }

    /** Returns how often (in blocks) a powered rail appears along a line. */
    public int powerEvery() { return spec.getInt("power-every", 8); }

    /** Returns how often (in blocks) a station appears along an X line. */
    public int stationEvery() { return spec.getInt("station-every", 96); }

    /** Returns the viaduct beam height above the floor-level rail. */
    public int viaductHeight() { return spec.getInt("viaduct-height", 4); }

    /** Returns the half-length of the viaduct approach ramps. */
    public int viaductHalf() { return spec.getInt("viaduct-half", 4); }

    /** Returns the cobweb probability beside rails. */
    public double cobwebChance() { return spec.getDouble("cobweb-chance", 0.02); }

    /** Returns the broken-track probability (0 = tracks always intact). */
    public double brokenTrackChance() { return spec.getDouble("broken-track-chance", 0.0); }

    /** Returns the ghost minecart probability per rail chunk. */
    public double ghostCartChance() { return spec.getDouble("ghost-cart-chance", 0.35); }

    /** Returns the station platform material. */
    public Material platform() { return material("platform", Material.POLISHED_BASALT); }

    /** Returns the station platform length in blocks (along the line). */
    public int platformLength() { return stationInt("platform-length", 6); }

    /** Returns the station platform depth in blocks (away from the track). */
    public int platformDepth() { return stationInt("platform-depth", 3); }

    /** Returns the station sign material. */
    public Material signMaterial() { return material("sign", Material.OAK_SIGN); }

    /** Returns the number of sign posts per station. */
    public int signCount() { return stationInt("sign-count", 2); }

    /** Returns the station chest material. */
    public Material chestMaterial() { return material("chest", Material.CHEST); }

    /** Returns the station torch material. */
    public Material torchMaterial() { return material("torch", Material.REDSTONE_TORCH); }

    /** Returns the supplies found in station chests. */
    public List<LootEntry> supplies() {
        return LootEntry.parseList(station().get("supplies"), DEFAULT_SUPPLIES);
    }

    /** Returns the supplies found in ghost chest carts. */
    public List<LootEntry> cartSupplies() {
        return LootEntry.parseList(station().get("cart-supplies"), DEFAULT_CART_SUPPLIES);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Returns the nested {@code station} parameter map (possibly empty). */
    private Map<String, Object> station() {
        return spec.getMap("station");
    }

    /** Reads an integer from the station map. */
    private int stationInt(String key, int fallback) {
        Object value = station().get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    /** Reads a material from the station map, falling back when unresolvable. */
    private Material material(String key, Material fallback) {
        Object value = station().get(key);
        if (value == null) return fallback;
        Material mat = Material.matchMaterial(String.valueOf(value).trim());
        return mat != null ? mat : fallback;
    }
}
