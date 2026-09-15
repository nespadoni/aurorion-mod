package com.aurorion.areas.geometry;

import java.util.ArrayList;
import java.util.List;

/** Uniao das partes menos todos os recortes. Cada parte pode ter sua propria altura. */
public final class AreaVolume {
    public static final int MAX_PARTS = 32;
    private final List<AreaShape> parts, holes;
    private final Bounds bounds;

    public AreaVolume(List<AreaShape> parts, List<AreaShape> holes) {
        if (parts.isEmpty() || parts.size() + holes.size() > MAX_PARTS) {
            throw new IllegalArgumentException("Area precisa de uma parte e aceita ate " + MAX_PARTS + " formas.");
        }
        this.parts = List.copyOf(parts);
        this.holes = List.copyOf(holes);
        Bounds box = parts.getFirst().bounds();
        for (int i = 1; i < parts.size(); i++) box = box.union(parts.get(i).bounds());
        bounds = box;
    }
    public boolean contains(double x, double y, double z) {
        if (!bounds.contains(x, y, z)) return false;
        boolean included = false;
        for (int i = 0; i < parts.size(); i++) if (parts.get(i).contains(x, y, z)) { included = true; break; }
        if (!included) return false;
        for (int i = 0; i < holes.size(); i++) if (holes.get(i).contains(x, y, z)) return false;
        return true;
    }
    public AreaVolume append(AreaShape shape, boolean subtract) {
        List<AreaShape> changed = new ArrayList<>(subtract ? holes : parts);
        changed.add(shape);
        return new AreaVolume(subtract ? parts : changed, subtract ? changed : holes);
    }
    public AreaVolume removePart(int index, boolean subtract) {
        List<AreaShape> changed = new ArrayList<>(subtract ? holes : parts);
        if (index < 0 || index >= changed.size()) throw new IllegalArgumentException("Indice de forma inexistente.");
        changed.remove(index);
        return new AreaVolume(subtract ? parts : changed, subtract ? changed : holes);
    }
    public List<AreaShape> parts() { return parts; }
    public List<AreaShape> holes() { return holes; }
    public Bounds bounds() { return bounds; }
}
