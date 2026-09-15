package com.aurorion.areas.geometry;

import java.util.List;

/** Circulo exato ou poligono simples (inclusive concavo), extrudido entre duas alturas. */
public final class AreaShape {
    public static final int MAX_VERTICES = 128;
    private static final double EPSILON = 1e-7;
    private final List<Point2> points;
    private final double radius;
    private final Bounds bounds;

    private AreaShape(List<Point2> points, double radius, double minY, double maxY) {
        if (!Double.isFinite(minY) || !Double.isFinite(maxY) || minY >= maxY || minY < -2048 || maxY > 2048) {
            throw new IllegalArgumentException("Alturas devem satisfazer -2048 <= minimo < maximo <= 2048.");
        }
        this.points = List.copyOf(points);
        this.radius = radius;
        double minX = Double.POSITIVE_INFINITY, minZ = minX, maxX = -minX, maxZ = -minX;
        for (Point2 p : points) {
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z()); maxZ = Math.max(maxZ, p.z());
        }
        bounds = new Bounds(minX - radius, minY, minZ - radius, maxX + radius, maxY, maxZ + radius);
    }

    public static AreaShape circle(double x, double z, double radius, double minY, double maxY) {
        if (!Double.isFinite(radius) || radius < .5 || radius > 100_000) {
            throw new IllegalArgumentException("Raio deve estar entre 0.5 e 100000 blocos.");
        }
        return new AreaShape(List.of(new Point2(x, z)), radius, minY, maxY);
    }

    public static AreaShape polygon(List<Point2> points, double minY, double maxY) {
        if (points.size() < 3 || points.size() > MAX_VERTICES) {
            throw new IllegalArgumentException("Poligono precisa de 3 a " + MAX_VERTICES + " vertices.");
        }
        double area = 0;
        Point2 origin = points.getFirst();
        for (int i = 0; i < points.size(); i++) {
            Point2 a = points.get(i), b = points.get((i + 1) % points.size());
            if (distanceSquared(a, b) < EPSILON * EPSILON) {
                throw new IllegalArgumentException("Vertices consecutivos repetidos.");
            }
            area += cross(origin, a, b);
            for (int j = i + 1; j < points.size(); j++) {
                if (j == i + 1 || i == 0 && j == points.size() - 1) continue;
                Point2 c = points.get(j), d = points.get((j + 1) % points.size());
                if (intersects(a, b, c, d)) throw new IllegalArgumentException("As arestas do poligono se cruzam.");
            }
        }
        if (Math.abs(area) < EPSILON) throw new IllegalArgumentException("Poligono sem area.");
        return new AreaShape(points, 0, minY, maxY);
    }

    public boolean contains(double x, double y, double z) {
        if (!bounds.contains(x, y, z)) return false;
        if (isCircle()) {
            double dx = x - points.getFirst().x(), dz = z - points.getFirst().z();
            return dx * dx + dz * dz <= radius * radius;
        }
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            Point2 a = points.get(j), b = points.get(i);
            if (onSegment(a.x(), a.z(), b.x(), b.z(), x, z)) return true;
            if ((a.z() > z) != (b.z() > z)
                    && x < (b.x() - a.x()) * (z - a.z()) / (b.z() - a.z()) + a.x()) inside = !inside;
        }
        return inside;
    }

    private static boolean intersects(Point2 a, Point2 b, Point2 c, Point2 d) {
        double abC = cross(a, b, c), abD = cross(a, b, d), cdA = cross(c, d, a), cdB = cross(c, d, b);
        if ((abC > 0 && abD < 0 || abC < 0 && abD > 0) && (cdA > 0 && cdB < 0 || cdA < 0 && cdB > 0)) return true;
        return onSegment(a.x(), a.z(), b.x(), b.z(), c.x(), c.z())
                || onSegment(a.x(), a.z(), b.x(), b.z(), d.x(), d.z())
                || onSegment(c.x(), c.z(), d.x(), d.z(), a.x(), a.z())
                || onSegment(c.x(), c.z(), d.x(), d.z(), b.x(), b.z());
    }

    private static boolean onSegment(double ax, double az, double bx, double bz, double x, double z) {
        double cross = (bx - ax) * (z - az) - (bz - az) * (x - ax);
        double length = Math.hypot(bx - ax, bz - az);
        return Math.abs(cross) <= EPSILON * Math.max(1, length)
                && x >= Math.min(ax, bx) - EPSILON && x <= Math.max(ax, bx) + EPSILON
                && z >= Math.min(az, bz) - EPSILON && z <= Math.max(az, bz) + EPSILON;
    }
    private static double cross(Point2 a, Point2 b, Point2 c) {
        return (b.x() - a.x()) * (c.z() - a.z()) - (b.z() - a.z()) * (c.x() - a.x());
    }
    private static double distanceSquared(Point2 a, Point2 b) {
        return (a.x() - b.x()) * (a.x() - b.x()) + (a.z() - b.z()) * (a.z() - b.z());
    }
    public boolean isCircle() { return radius > 0; }
    public List<Point2> points() { return points; }
    public double radius() { return radius; }
    public Bounds bounds() { return bounds; }
}
