package com.aurorion.areas.command;

import com.aurorion.areas.geometry.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Prepared once per staff request, bounded to 96 private particles each half-second for 30 seconds. */
final class AreaPreview {
    private static final DustParticleOptions BORDER = new DustParticleOptions(new Vector3f(.2F, .9F, 1), 1);
    private static final DustParticleOptions CUTOUT = new DustParticleOptions(new Vector3f(1, .3F, .2F), 1);
    private record Sample(double x, double y, double z, boolean hole) {}
    private final ResourceLocation dimension;
    private final List<Sample> samples = new ArrayList<>();
    private final long until;
    private int cursor;
    AreaPreview(ServerPlayer player, ResourceLocation dimension, AreaVolume volume) {
        this.dimension = dimension;
        until = player.serverLevel().getGameTime() + 600;
        int each = Math.max(3, 96 / (volume.parts().size() + volume.holes().size()));
        for (AreaShape shape : volume.parts()) sample(shape, false, each, player.getY());
        for (AreaShape shape : volume.holes()) sample(shape, true, each, player.getY());
    }
    private void sample(AreaShape shape, boolean hole, int count, double playerY) {
        double y = Math.clamp(playerY, shape.bounds().minY(), shape.bounds().maxY()) + .08;
        var points = shape.points();
        if (shape.isCircle()) {
            Point2 center = points.getFirst();
            for (int i = 0; i < count; i++) {
                double angle = i * Math.PI * 2 / count;
                samples.add(new Sample(center.x() + Math.cos(angle) * shape.radius(), y,
                        center.z() + Math.sin(angle) * shape.radius(), hole));
            }
            return;
        }
        // Uniform arc-length sampling covers short and long edges without exceeding the budget.
        double[] lengths = new double[points.size()];
        double perimeter = 0;
        for (int i = 0; i < points.size(); i++) {
            var a = points.get(i); var b = points.get((i + 1) % points.size());
            perimeter += lengths[i] = Math.hypot(b.x() - a.x(), b.z() - a.z());
        }
        int edge = 0;
        double start = 0;
        for (int i = 0; i < count; i++) {
            double distance = perimeter * i / count;
            while (edge < lengths.length - 1 && start + lengths[edge] < distance) start += lengths[edge++];
            double t = (distance - start) / lengths[edge];
            var a = points.get(edge); var b = points.get((edge + 1) % points.size());
            samples.add(new Sample(a.x() + (b.x() - a.x()) * t, y, a.z() + (b.z() - a.z()) * t, hole));
        }
    }
    boolean tick(ServerPlayer player) {
        if (!player.hasPermissions(2) || !dimension.equals(player.level().dimension().location())
                || player.serverLevel().getGameTime() >= until) return false;
        if (player.tickCount % 10 != 0) return true;
        for (int i = 0, budget = Math.min(96, samples.size()); i < budget; i++) {
            Sample p = samples.get(cursor++ % samples.size());
            player.serverLevel().sendParticles(player, p.hole ? CUTOUT : BORDER, true,
                    p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        return true;
    }
}
