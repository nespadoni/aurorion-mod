package com.aurorion.areas.geometry;

public record Point2(double x, double z) {
    public Point2 {
        if (!Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) {
            throw new IllegalArgumentException("Coordenada fora dos limites do mundo.");
        }
    }
}
