package com.aurorion.magia.spell;

import com.aurorion.magia.compat.VoiceMute;
import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import io.redspace.ironsspellbooks.api.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Silencio derivado dos efeitos: controles sobrepostos nunca liberam a voz um do outro. */
public final class ControlSpells {
    private record Control(String spell, Holder<MobEffect> effect) { }
    private static List<Control> bindings;

    private ControlSpells() { }

    private static List<Control> controls() {
        if (bindings == null) bindings = List.of(new Control("mutatio_ferae", MagiaEffects.POLYMORPH),
                new Control("genua_flecte", MagiaEffects.KNEELING),
                new Control("imperium_mentis", MagiaEffects.DISORIENTED),
                new Control("imperium_mentis", MagiaEffects.DOMINATED),
                new Control("aspectus_captus", MagiaEffects.CAPTIVE),
                new Control("mundus_vacuus", MagiaEffects.SOLITARY),
                new Control("ferrum_ligatum", MagiaEffects.IRON_BOUND),
                new Control("carcer_aquae", MagiaEffects.CAGED));
        return bindings;
    }

    public static int duration(String spell, int finiteTicks) {
        return MagiaConfig.PERSISTENT_CONTROLS.get().contains(spell)
                ? MobEffectInstance.INFINITE_DURATION : finiteTicks;
    }

    public static MutableComponent timeInfo(String spell, String translation, int ticks) {
        return MagiaConfig.PERSISTENT_CONTROLS.get().contains(spell)
                ? Component.translatable("ui.aurorion_magia.ate_reconjurar")
                : Component.translatable(translation, Utils.timeFromTicks(ticks, 1));
    }

    public static boolean isSilenced(LivingEntity entity) {
        return silenceTicks(entity, null) != 0;
    }

    private static int silenceTicks(LivingEntity entity, @Nullable MobEffect removed) {
        int ticks = 0;
        MobEffectInstance voice = entity.getEffect(MagiaEffects.SILENCED);
        if (voice != null && voice.getEffect().value() != removed) ticks = voice.getDuration();
        for (Control control : controls()) {
            if (!MagiaConfig.SILENT_CONTROLS.get().contains(control.spell()) || control.effect().value() == removed) continue;
            MobEffectInstance effect = entity.getEffect(control.effect());
            if (effect == null) continue;
            if (effect.isInfiniteDuration()) return MobEffectInstance.INFINITE_DURATION;
            if (ticks >= 0) ticks = Math.max(ticks, effect.getDuration());
        }
        return ticks;
    }

    public static void syncVoice(LivingEntity entity, @Nullable MobEffect removed) {
        if (!(entity instanceof ServerPlayer)) return;
        int ticks = silenceTicks(entity, removed);
        if (ticks == 0) VoiceMute.unmute(entity.getUUID());
        else VoiceMute.mute(entity.getUUID(), ticks < 0 ? -1 : ticks * 50L);
    }

    /** Correspondencia tambem usada no cliente para manter o visual de um efeito infinito. */
    @Nullable
    public static Holder<MobEffect> visualEffect(SpellVisualPayload.Kind kind, LivingEntity target) {
        return switch (kind) {
            case MUTATIO_FERAE -> MagiaEffects.POLYMORPH;
            case VOX_INTERDICTA -> MagiaEffects.SILENCED;
            case GENUA_FLECTE -> MagiaEffects.KNEELING;
            case ASPECTUS_CAPTUS -> MagiaEffects.CAPTIVE;
            case MUNDUS_VACUUS -> MagiaEffects.SOLITARY;
            case FERRUM_LIGATUM -> MagiaEffects.IRON_BOUND;
            case CARCER_AQUAE -> MagiaEffects.CAGED;
            case IMPERIUM_AURA -> target instanceof Mob ? MagiaEffects.DOMINATED : MagiaEffects.DISORIENTED;
            default -> null;
        };
    }

    public static void sendVisualsTo(ServerPlayer viewer, LivingEntity target) {
        for (var kind : List.of(SpellVisualPayload.Kind.MUTATIO_FERAE, SpellVisualPayload.Kind.VOX_INTERDICTA,
                SpellVisualPayload.Kind.GENUA_FLECTE, SpellVisualPayload.Kind.ASPECTUS_CAPTUS,
                SpellVisualPayload.Kind.MUNDUS_VACUUS, SpellVisualPayload.Kind.FERRUM_LIGATUM,
                SpellVisualPayload.Kind.CARCER_AQUAE, SpellVisualPayload.Kind.IMPERIUM_AURA)) {
            var holder = visualEffect(kind, target);
            var effect = holder == null ? null : target.getEffect(holder);
            if (effect == null) continue;
            MagiaNetwork.sendVisualTo(viewer, kind == SpellVisualPayload.Kind.ASPECTUS_CAPTUS
                    ? Gaze.captorOf(target) : null, target, kind, effect.getDuration());
        }
    }
}
