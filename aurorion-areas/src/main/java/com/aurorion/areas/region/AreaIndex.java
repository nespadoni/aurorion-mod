package com.aurorion.areas.region;

import com.aurorion.areas.geometry.Bounds;
import com.aurorion.areas.rules.Decision;
import com.aurorion.areas.rules.ResolvedRules;
import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** BVH de caixas; tamanho depende da quantidade de areas, nunca dos chunks cobertos. */
public final class AreaIndex {
    @Nullable private final Node root;
    public AreaIndex(List<AreaRegion> regions) {
        AreaRegion[] entries = regions.stream().filter(AreaRegion::enabled).toArray(AreaRegion[]::new);
        root = entries.length == 0 ? null : build(entries, 0, entries.length);
    }
    public void resolve(double x, double y, double z, ResolvedRules result) {
        if (root != null) root.resolve(x, y, z, result);
    }
    @Nullable public AreaRegion owner(double x, double y, double z, String key, @Nullable UUID actor) {
        return root == null ? null : root.owner(x, y, z, key, actor, null);
    }
    private static Node build(AreaRegion[] entries, int start, int end) {
        if (end - start == 1) return new Node(entries[start].volume().bounds(), entries[start], null, null);
        Bounds bounds = entries[start].volume().bounds();
        for (int i = start + 1; i < end; i++) bounds = bounds.union(entries[i].volume().bounds());
        boolean splitX = bounds.maxX() - bounds.minX() >= bounds.maxZ() - bounds.minZ();
        Arrays.sort(entries, start, end, Comparator.comparingDouble(r -> splitX
                ? r.volume().bounds().minX() + r.volume().bounds().maxX()
                : r.volume().bounds().minZ() + r.volume().bounds().maxZ()));
        int middle = (start + end) >>> 1;
        return new Node(bounds, null, build(entries, start, middle), build(entries, middle, end));
    }
    private record Node(Bounds bounds, @Nullable AreaRegion region, @Nullable Node left, @Nullable Node right) {
        void resolve(double x, double y, double z, ResolvedRules result) {
            if (!bounds.contains(x, y, z)) return;
            if (region != null) {
                if (region.volume().contains(x, y, z)) result.include(region);
            } else {
                left.resolve(x, y, z, result); right.resolve(x, y, z, result);
            }
        }
        @Nullable AreaRegion owner(double x, double y, double z, String key, @Nullable UUID actor, @Nullable AreaRegion best) {
            if (!bounds.contains(x, y, z)) return best;
            if (region != null) {
                return region.beats(best) && region.decision(key, actor) != Decision.INHERIT && region.volume().contains(x, y, z)
                        ? region : best;
            }
            return right.owner(x, y, z, key, actor, left.owner(x, y, z, key, actor, best));
        }
    }
}
