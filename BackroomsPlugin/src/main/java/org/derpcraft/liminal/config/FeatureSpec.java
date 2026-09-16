package org.derpcraft.liminal.config;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A single feature entry parsed from a level file's {@code features:} list.
 *
 * <p>Features are the spec-driven building blocks of a level: the engine reads
 * this list and delegates to registered feature handlers by {@link #getType()}.
 * Three well-known keys are extracted from every entry ({@code type},
 * {@code material}, {@code chance}); everything else is kept in the parameter
 * map for the handler to interpret (e.g. {@code max-length}, {@code grid},
 * nested {@code station} blocks).</p>
 *
 * <p>Handlers define their own defaults for missing values, so a minimal
 * entry like {@code - type: floor-pool} is valid.</p>
 *
 * @see org.derpcraft.liminal.generator.levels.LiminalLevel
 */
public final class FeatureSpec {

    /** The feature type key (must match a registered handler). */
    private final String type;

    /** Optional primary material for the feature (e.g. CHAIN, WATER). */
    private Material material;

    /**
     * Optional roll probability (0.0&ndash;1.0). {@code -1} means "not set";
     * handlers fall back to their documented default.
     */
    private double chance = -1.0;

    /** Remaining parameters, keyed exactly as written in the YAML. */
    private final Map<String, Object> params = new HashMap<>();

    /**
     * Constructs a feature spec.
     *
     * @param type the feature type key
     */
    public FeatureSpec(String type) {
        this.type = type;
    }

    /** Returns the feature type key. */
    public String getType() { return type; }

    /** Returns the primary material, or {@code null} when unset. */
    public Material getMaterial() { return material; }

    /** Sets the primary material. */
    public void setMaterial(Material material) { this.material = material; }

    /** Returns the configured chance, or {@code -1} when unset. */
    public double getChance() { return chance; }

    /** Sets the roll probability. */
    public void setChance(double chance) { this.chance = chance; }

    /** Stores a raw parameter value. */
    public void setParam(String key, Object value) { params.put(key, value); }

    /**
     * Reads an integer parameter.
     *
     * @param key      the parameter key
     * @param fallback the value to use when absent or malformed
     * @return the parameter value
     */
    public int getInt(String key, int fallback) {
        Object value = params.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    /**
     * Reads a double parameter.
     *
     * @param key      the parameter key
     * @param fallback the value to use when absent or malformed
     * @return the parameter value
     */
    public double getDouble(String key, double fallback) {
        Object value = params.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    /**
     * Reads a material parameter, falling back when absent or unresolvable.
     *
     * @param key      the parameter key
     * @param fallback the material to use when the value cannot be resolved
     * @return the parsed material
     */
    public Material getMaterial(String key, Material fallback) {
        Object value = params.get(key);
        if (value == null) return fallback;
        Material mat = Material.matchMaterial(String.valueOf(value).trim());
        return mat != null ? mat : fallback;
    }

    /**
     * Reads a nested mapping parameter (e.g. a rail feature's {@code station}).
     *
     * @param key the parameter key
     * @return the nested map, or an empty map when absent
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String key) {
        Object value = params.get(key);
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    /**
     * Reads a list of material names (e.g. a pipe feature's aging mix),
     * falling back when absent, empty, or unresolvable.
     *
     * @param key      the parameter key
     * @param fallback the materials to use when the value yields nothing
     * @return the parsed materials
     */
    public List<Material> getMaterialList(String key, List<Material> fallback) {
        Object value = params.get(key);
        if (!(value instanceof List<?> list)) return fallback;

        List<Material> out = new ArrayList<>();
        for (Object item : list) {
            if (item == null) continue;
            Material mat = Material.matchMaterial(String.valueOf(item).trim());
            if (mat != null) out.add(mat);
        }
        return out.isEmpty() ? fallback : out;
    }
}
