package com.aurorion.areas.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import java.util.List;

public record AmbientProfile(ResourceLocation id, Sounds sounds, Pulse pulse, Attack attack, Fog fog) {
    public record Interval(int min, int max) {
        public static final Codec<Interval> CODEC = RecordCodecBuilder.<Interval>create(i -> i.group(
                Codec.intRange(1, 3600).fieldOf("min_seconds").forGetter(Interval::min),
                Codec.intRange(1, 3600).fieldOf("max_seconds").forGetter(Interval::max)
        ).apply(i, Interval::new)).validate(value -> value.min <= value.max ? DataResult.success(value)
                : DataResult.error(() -> "min_seconds maior que max_seconds"));
        public long next(long now, RandomSource random) { return now + 20L * (min + random.nextInt(max - min + 1)); }
    }
    public record Sounds(List<ResourceLocation> events, Interval interval, float volume, float pitch, int distance) {
        public static final Sounds NONE = new Sounds(List.of(), new Interval(8, 18), .5F, .8F, 7);
        public static final Codec<Sounds> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf(0, 32).fieldOf("events").forGetter(Sounds::events),
                Interval.CODEC.optionalFieldOf("interval", NONE.interval).forGetter(Sounds::interval),
                Codec.floatRange(.01F, 2).optionalFieldOf("volume", .5F).forGetter(Sounds::volume),
                Codec.floatRange(.5F, 2).optionalFieldOf("pitch", .8F).forGetter(Sounds::pitch),
                Codec.intRange(2, 16).optionalFieldOf("distance", 7).forGetter(Sounds::distance)
        ).apply(i, Sounds::new));
        public Sounds { events = List.copyOf(events); }
    }
    public record Pulse(int darknessSeconds, int blindnessSeconds, float vignette, Interval interval) {
        public static final Pulse NONE = new Pulse(0, 0, 0, new Interval(20, 40));
        public static final Codec<Pulse> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 30).optionalFieldOf("darkness_seconds", 0).forGetter(Pulse::darknessSeconds),
                Codec.intRange(0, 15).optionalFieldOf("blindness_seconds", 0).forGetter(Pulse::blindnessSeconds),
                Codec.floatRange(0, .9F).optionalFieldOf("vignette", 0F).forGetter(Pulse::vignette),
                Interval.CODEC.optionalFieldOf("interval", NONE.interval).forGetter(Pulse::interval)
        ).apply(i, Pulse::new));
    }
    public record Attack(float damage, boolean lethal, Interval interval) {
        public static final Attack NONE = new Attack(0, false, new Interval(30, 60));
        public static final Codec<Attack> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 10).optionalFieldOf("damage", 0F).forGetter(Attack::damage),
                Codec.BOOL.optionalFieldOf("lethal", false).forGetter(Attack::lethal),
                Interval.CODEC.optionalFieldOf("interval", NONE.interval).forGetter(Attack::interval)
        ).apply(i, Attack::new));
    }
    public record Fog(float distance, int color) {
        public static final Fog NONE = new Fog(0, 0x0D1715);
        private static final Codec<Integer> COLOR = Codec.STRING.comapFlatMap(s -> {
            try {
                if (!s.matches("#[0-9a-fA-F]{6}")) return DataResult.error(() -> "Cor deve ser #RRGGBB.");
                return DataResult.success(Integer.parseInt(s.substring(1), 16));
            } catch (RuntimeException e) { return DataResult.error(() -> "Cor invalida."); }
        }, c -> String.format(java.util.Locale.ROOT, "#%06X", c));
        public static final Codec<Fog> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 256).optionalFieldOf("distance", 0F).forGetter(Fog::distance),
                COLOR.optionalFieldOf("color", NONE.color).forGetter(Fog::color)
        ).apply(i, Fog::new));
    }
    public static Codec<AmbientProfile> codec(ResourceLocation id) {
        return RecordCodecBuilder.create(i -> i.group(
                Sounds.CODEC.optionalFieldOf("sounds", Sounds.NONE).forGetter(AmbientProfile::sounds),
                Pulse.CODEC.optionalFieldOf("pulse", Pulse.NONE).forGetter(AmbientProfile::pulse),
                Attack.CODEC.optionalFieldOf("attack", Attack.NONE).forGetter(AmbientProfile::attack),
                Fog.CODEC.optionalFieldOf("fog", Fog.NONE).forGetter(AmbientProfile::fog)
        ).apply(i, (sounds, pulse, attack, fog) -> new AmbientProfile(id, sounds, pulse, attack, fog)));
    }
}
