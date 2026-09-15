package com.aurorion.areas;

import com.aurorion.areas.geometry.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AreaGeometryTest {
    @Test void circleUsesActualRadiusAndClosedVerticalLimits() {
        var shape = AreaShape.circle(10, -20, 5, 60, 80);
        assertTrue(shape.contains(13, 60, -16));
        assertTrue(shape.contains(10, 80, -20));
        assertFalse(shape.contains(14, 70, -16));
        assertFalse(shape.contains(10, 80.01, -20));
    }
    @Test void concaveSchoolAnnexDoesNotProtectItsBoundingBoxNotch() {
        var shape = AreaShape.polygon(List.of(new Point2(0, 0), new Point2(8, 0), new Point2(8, 2),
                new Point2(2, 2), new Point2(2, 8), new Point2(0, 8)), 0, 100);
        assertTrue(shape.contains(1, 50, 7));
        assertTrue(shape.contains(7, 50, 1));
        assertFalse(shape.contains(5, 50, 5));
        assertTrue(shape.contains(2, 50, 5));
    }
    @Test void disjointPartsAndHeightLimitedHoles() {
        var volume = new AreaVolume(List.of(AreaShape.circle(0, 0, 10, 0, 100),
                AreaShape.circle(30, 0, 3, 0, 100)), List.of(AreaShape.circle(0, 0, 2, 20, 30)));
        assertTrue(volume.contains(30, 50, 0));
        assertFalse(volume.contains(20, 50, 0));
        assertFalse(volume.contains(0, 25, 0));
        assertFalse(volume.contains(2, 25, 0)); // Hole boundaries are also excluded.
        assertTrue(volume.contains(0, 31, 0));
    }
    @Test void invalidContoursAreRejectedBeforePublishing() {
        assertThrows(IllegalArgumentException.class, () -> AreaShape.polygon(
                List.of(new Point2(0, 0), new Point2(4, 4), new Point2(0, 4), new Point2(4, 0)), 0, 10));
        assertThrows(IllegalArgumentException.class, () -> AreaShape.polygon(
                List.of(new Point2(0, 0), new Point2(1, 1), new Point2(2, 2)), 0, 10));
        assertThrows(IllegalArgumentException.class, () -> AreaShape.circle(0, 0, Double.NaN, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> AreaShape.circle(0, 0, 1, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new Point2(Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new AreaVolume(List.of(), List.of()));
    }
}
