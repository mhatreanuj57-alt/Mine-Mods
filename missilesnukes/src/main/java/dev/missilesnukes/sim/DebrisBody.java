package dev.missilesnukes.sim;

/**
 * A detached stage (engine section or spent central body) moving on its own: gravity, altitude-dependent drag,
 * and free tumbling (angular velocity applied to its attitude each tick). Pure Java.
 */
public final class DebrisBody {
    public double px, py, pz, vx, vy, vz;
    public final Quat q = new Quat();
    public double wx, wy, wz;            // angular velocity, radians per tick (world frame)
    private final double dragPerMass;    // cdA / mass
    public long age;

    public DebrisBody(double cdA, double mass) { this.dragPerMass = cdA / mass; }

    public void step() {
        age++;
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double k = dragPerMass * Atmosphere.density(py);
        double ax = -k * speed * vx, ay = -Flight.G - k * speed * vy, az = -k * speed * vz;
        px += vx + 0.5 * ax; py += vy + 0.5 * ay; pz += vz + 0.5 * az;
        vx += ax; vy += ay; vz += az;
        q.integrate(wx, wy, wz);
        double damp = 1.0 - 0.15 * k * speed - 0.0004; // air slowly bleeds spin
        wx *= damp; wy *= damp; wz *= damp;
    }
}
