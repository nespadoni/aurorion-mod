package com.aurorion.areas.command;

import com.aurorion.areas.geometry.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

final class AreaSelection {
    final ResourceLocation dimension;
    final List<Point2> points = new ArrayList<>();
    double minY, maxY, radius;
    Point2 center;
    AreaSelection(ServerPlayer player) {
        dimension = player.level().dimension().location();
        minY = player.level().getMinBuildHeight();
        maxY = player.level().getMaxBuildHeight();
    }
    void heights(double min, double max) {
        if (min < -AreaShape.MAX_ABS_HEIGHT || max > AreaShape.MAX_ABS_HEIGHT || min >= max
                || !Double.isFinite(min) || !Double.isFinite(max))
            throw new IllegalArgumentException(
                    "Use mínimo < máximo; ambos devem estar entre -30000000 e 30000000.");
        minY = min; maxY = max;
    }
    void point(double x, double z) {
        if (radius > 0) throw new IllegalArgumentException("Seleção circular. Use /area selecao para iniciar um polígono.");
        if (points.size() >= AreaShape.MAX_VERTICES) throw new IllegalArgumentException("Máximo de 128 pontos.");
        Point2 point = new Point2(x, z);
        if (!points.isEmpty() && points.getLast().equals(point)) throw new IllegalArgumentException("Ponto repetido.");
        points.add(point);
    }
    AreaShape shape() {
        return radius > 0 ? AreaShape.circle(center.x(), center.z(), radius, minY, maxY)
                : AreaShape.polygon(points, minY, maxY);
    }
}
