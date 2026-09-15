package com.aurorion.areas.profile;

import com.aurorion.areas.AurorionAreas;
import com.aurorion.core.datapack.DatapackRegistry;
import java.util.Comparator;

public final class AreaProfiles {
    private static long revision;
    public static final DatapackRegistry<RulePreset> RULES = new DatapackRegistry<>(
            "aurorion/area_rules", AurorionAreas.LOGGER, RulePreset::codec, Comparator.comparing(RulePreset::id));
    public static final DatapackRegistry<AmbientProfile> AMBIENCE = new DatapackRegistry<>(
            "aurorion/area_ambience", AurorionAreas.LOGGER, AmbientProfile::codec, Comparator.comparing(AmbientProfile::id))
            .onReload(values -> revision++);
    private AreaProfiles() {}
    public static long revision() { return revision; }
}
