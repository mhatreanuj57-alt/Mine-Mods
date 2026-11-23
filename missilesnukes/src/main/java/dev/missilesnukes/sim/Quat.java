package dev.missilesnukes.sim;

/** Minimal quaternion (x,y,z,w) in doubles. Pure Java so it can be unit-tested without Minecraft. */
public final class Quat {
    public double x, y, z, w;

    public Quat() { this(0, 0, 0, 1); }
    public Quat(double x, double y, double z, double w) { this.x = x; this.y = y; this.z = z; this.w = w; }
    public Quat copy() { return new Quat(x, y, z, w); }
    public Quat set(Quat o) { x = o.x; y = o.y; z = o.z; w = o.w; return this; }

    public static Quat identity() { return new Quat(); }

    public static Quat axisAngle(double ax, double ay, double az, double angle) {
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-12) return identity();
        double s = Math.sin(angle * 0.5) / len;
        return new Quat(ax * s, ay * s, az * s, Math.cos(angle * 0.5));
    }

    /** this = this * b (apply b first, then this). */
    public Quat mul(Quat b) {
        double nx = w * b.x + x * b.w + y * b.z - z * b.y;
        double ny = w * b.y - x * b.z + y * b.w + z * b.x;
        double nz = w * b.z + x * b.y - y * b.x + z * b.w;
        double nw = w * b.w - x * b.x - y * b.y - z * b.z;
        x = nx; y = ny; z = nz; w = nw;
        return this;
    }

    public Quat normalize() {
        double n = Math.sqrt(x * x + y * y + z * z + w * w);
        if (n < 1e-12) { x = y = z = 0; w = 1; return this; }
        x /= n; y /= n; z /= n; w /= n;
        return this;
    }

    /** Rotates vector (vx,vy,vz); result written into out[0..2]. */
    public void rotate(double vx, double vy, double vz, double[] out) {
        double tx = 2 * (y * vz - z * vy);
        double ty = 2 * (z * vx - x * vz);
        double tz = 2 * (x * vy - y * vx);
        out[0] = vx + w * tx + (y * tz - z * ty);
        out[1] = vy + w * ty + (z * tx - x * tz);
        out[2] = vz + w * tz + (x * ty - y * tx);
    }

    /** Body +Y axis (the missile's nose direction) in world space. */
    public void noseAxis(double[] out) { rotate(0, 1, 0, out); }

    /**
     * Turns the nose axis towards (tx,ty,tz) by at most maxAngle radians (and by at most blend*angle),
     * using only the minimal rotation, so no roll is introduced and there are no singular flips.
     * Returns the angle applied.
     */
    public double turnNoseTowards(double tx, double ty, double tz, double maxAngle, double blend) {
        double[] n = new double[3];
        noseAxis(n);
        double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tl < 1e-12) return 0;
        tx /= tl; ty /= tl; tz /= tl;
        double dot = Math.max(-1, Math.min(1, n[0] * tx + n[1] * ty + n[2] * tz));
        double angle = Math.acos(dot);
        if (angle < 1e-9) return 0;
        double ax = n[1] * tz - n[2] * ty, ay = n[2] * tx - n[0] * tz, az = n[0] * ty - n[1] * tx;
        if (ax * ax + ay * ay + az * az < 1e-18) {
            ax = 1; ay = 0; az = 0;
            if (Math.abs(n[0]) > 0.9) { ax = 0; ay = 0; az = 1; }
            double d = ax * n[0] + ay * n[1] + az * n[2];
            ax -= d * n[0]; ay -= d * n[1]; az -= d * n[2];
        }
        double step = Math.min(angle * blend, maxAngle);
        Quat d = axisAngle(ax, ay, az, step);
        d.mul(this);
        set(d).normalize();
        return step;
    }

    /** Applies a world-frame angular velocity (radians per tick, vector form) for one tick. */
    public Quat integrate(double wx, double wy, double wz) {
        double mag = Math.sqrt(wx * wx + wy * wy + wz * wz);
        if (mag < 1e-12) return this;
        Quat d = axisAngle(wx, wy, wz, mag);
        d.mul(this);
        return set(d).normalize();
    }
}
