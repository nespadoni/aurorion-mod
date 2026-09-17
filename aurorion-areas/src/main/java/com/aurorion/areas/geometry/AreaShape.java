package com.aurorion.areas.geometry;

import java.util.ArrayList;
import java.util.List;

/** Circulo exato ou poligono simples (inclusive concavo), extrudido entre duas alturas. */
public final class AreaShape {
    public static final int MAX_VERTICES = 128;
    /**
     * Limite defensivo para dados digitados ou importados. Nao pode ser 2048: o modpack usa
     * Higher Heights 4064 e a selecao nasce com o teto real da dimensao.
     */
    public static final double MAX_ABS_HEIGHT = 30_000_000D;
    private static final double EPSILON = 1e-7;
    private static final double EPSILON_SQUARED = EPSILON * EPSILON;
    /**
     * Vertices em vetores paralelos: {@link #contains} roda por jogador por tick e por evento de
     * dano/alvo/spawn, entao o laco quente le arrays em vez de percorrer uma lista de records.
     */
    private final double[] xs, zs;
    /** Tolerancia ja elevada ao quadrado por aresta: tira 128 {@code Math.hypot} de cada consulta. */
    private final double[] edgeTolerance;
    private final double radius;
    private final Bounds bounds;

    private AreaShape(List<Point2> points, double radius, double minY, double maxY) {
        if (!Double.isFinite(minY) || !Double.isFinite(maxY) || minY >= maxY
                || minY < -MAX_ABS_HEIGHT || maxY > MAX_ABS_HEIGHT) {
            throw new IllegalArgumentException(
                    "Alturas devem ser finitas e satisfazer minimo < maximo (limite absoluto 30000000).");
        }
        int count = points.size();
        xs = new double[count];
        zs = new double[count];
        this.radius = radius;
        double minX = Double.POSITIVE_INFINITY, minZ = minX, maxX = -minX, maxZ = -minX;
        for (int i = 0; i < count; i++) {
            Point2 p = points.get(i);
            xs[i] = p.x(); zs[i] = p.z();
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z()); maxZ = Math.max(maxZ, p.z());
        }
        edgeTolerance = radius > 0 ? null : new double[count];
        if (edgeTolerance != null) {
            for (int i = 0, j = count - 1; i < count; j = i++) edgeTolerance[j] = tolerance(xs[j], zs[j], xs[i], zs[i]);
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
            double dx = x - xs[0], dz = z - zs[0];
            return dx * dx + dz * dz <= radius * radius;
        }
        boolean inside = false;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            double ax = xs[j], az = zs[j], bx = xs[i], bz = zs[i];
            if (onSegment(ax, az, bx, bz, x, z, edgeTolerance[j])) return true;
            if ((az > z) != (bz > z) && x < (bx - ax) * (z - az) / (bz - az) + ax) inside = !inside;
        }
        return inside;
    }

    private static boolean intersects(Point2 a, Point2 b, Point2 c, Point2 d) {
        double abC = cross(a, b, c), abD = cross(a, b, d), cdA = cross(c, d, a), cdB = cross(c, d, b);
        if ((abC > 0 && abD < 0 || abC < 0 && abD > 0) && (cdA > 0 && cdB < 0 || cdA < 0 && cdB > 0)) return true;
        double ab = tolerance(a.x(), a.z(), b.x(), b.z()), cd = tolerance(c.x(), c.z(), d.x(), d.z());
        return onSegment(a.x(), a.z(), b.x(), b.z(), c.x(), c.z(), ab)
                || onSegment(a.x(), a.z(), b.x(), b.z(), d.x(), d.z(), ab)
                || onSegment(c.x(), c.z(), d.x(), d.z(), a.x(), a.z(), cd)
                || onSegment(c.x(), c.z(), d.x(), d.z(), b.x(), b.z(), cd);
    }

    /**
     * Forma quadrada de {@code EPSILON * max(1, comprimento)}: elevar os dois lados ao quadrado
     * preserva a comparacao porque ambos sao nao negativos, e {@code max(1, l)^2 == max(1, l^2)}.
     */
    private static double tolerance(double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        return EPSILON_SQUARED * Math.max(1, dx * dx + dz * dz);
    }

    private static boolean onSegment(double ax, double az, double bx, double bz, double x, double z, double tolerance) {
        double cross = (bx - ax) * (z - az) - (bz - az) * (x - ax);
        return cross * cross <= tolerance
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
    /** Fora do caminho quente: persistencia, previa e descricao. Reconstruido sob demanda. */
    public List<Point2> points() {
        List<Point2> out = new ArrayList<>(xs.length);
        for (int i = 0; i < xs.length; i++) out.add(new Point2(xs[i], zs[i]));
        return List.copyOf(out);
    }
    public int vertexCount() { return xs.length; }
    public double radius() { return radius; }
    public Bounds bounds() { return bounds; }
}
