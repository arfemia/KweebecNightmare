package com.ziggfreed.kweebec.arena;

/**
 * A plain position + facing in arena-local world coordinates. Kept free of any
 * engine type so the arena layout stays pure-Java + unit-testable; the round
 * engine converts an Anchor to a {@code com.hypixel.hytale.math.vector.Transform}
 * / JOML vector at the call site.
 *
 * @param x    world block X (entity stands at this point)
 * @param y    world block Y (floor surface)
 * @param z    world block Z
 * @param yaw  facing yaw in radians
 */
public record Anchor(double x, double y, double z, float yaw) {

    public Anchor(double x, double y, double z) {
        this(x, y, z, 0f);
    }

    /** Squared horizontal (XZ) distance to a point - cheap proximity test for shrines/gate. */
    public double horizontalDistanceSq(double px, double pz) {
        double dx = px - x;
        double dz = pz - z;
        return dx * dx + dz * dz;
    }
}
