package com.aurorion.magia.passive;

import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * <b>Lua Minguante</b> — Iacy, representante do Minguantis. O Escanor ao contrario: o poder dela nasce
 * com a noite, cresce ate a meia-noite e vai embora com o amanhecer.
 *
 * <h2>Fases</h2>
 *
 * <p>O poder e a profundidade da noite (0 no por do sol e na aurora, 1 a meia-noite) vezes a lua. A
 * Minguantis e a lua que mingua: nas fases minguantes ela vale tanto quanto a cheia; na nova, cai.
 *
 * <table>
 *   <tr><th>Fase</th><th>Poder</th><th>Bencao (x fase+1)</th><th>Restauracao</th></tr>
 *   <tr><td>0</td><td>comeco da noite</td><td>+25% dano, +2 armadura, +5% velocidade</td><td>Regeneracao I</td></tr>
 *   <tr><td>3</td><td>meia-noite de lua cheia/minguante</td><td>+100% dano, +8 armadura, +20% velocidade</td><td>Regeneracao II</td></tr>
 * </table>
 *
 * <p>Quem fica perto dela a noite — ela inclusive — recebe a restauracao, num raio de {@code raioCura}
 * mais {@code raioCuraPorFase} por fase. Em dimensao sem ceu (Nether, End, bolsos) nao ha noite.
 *
 * <h2>Custo</h2>
 *
 * <p>O relogio e o marcador infinito {@code presenca_lunar}, so nela: um pulso a cada
 * {@value #INTERVAL_TICKS} ticks. Por pulso, de noite: uma volta em {@code level.players()} e uma
 * distancia por jogador; os efeitos so sao renovados quando estao perto de vencer. De dia: nada alem de
 * ler a hora.
 */
public final class WaningMoon {
    public static final int INTERVAL_TICKS = 40;
    private static final int EFFECT_TICKS = INTERVAL_TICKS * 3 + 10;
    private static final int RENEW_BELOW = INTERVAL_TICKS + 10;
    /** Visao noturna abaixo de 10 s pisca na tela; por isso dura mais que o resto. */
    private static final int NIGHT_VISION_TICKS = 260;

    private WaningMoon() {
    }

    /** Liga ou desliga o marcador conforme a pessoa tem ou nao a passiva. Login, respawn, concessao. */
    public static void restore(ServerPlayer player) {
        if (Passives.has(player, Passive.WANING_MOON)) {
            if (!player.hasEffect(MagiaEffects.LUNAR_PRESENCE)) {
                player.addEffect(new MobEffectInstance(MagiaEffects.LUNAR_PRESENCE, MobEffectInstance.INFINITE_DURATION,
                        0, false, false, false));
            }
            pulse(player);
        } else {
            // So quem tem: removeEffect posta o MobEffectEvent.Remove mesmo sem o efeito, e o fim da
            // presenca lunar volta para ca — sem o teste, um laco de tarefas para todo jogador no login.
            if (player.hasEffect(MagiaEffects.LUNAR_PRESENCE)) player.removeEffect(MagiaEffects.LUNAR_PRESENCE);
            if (player.hasEffect(MagiaEffects.LUNAR_BLESSING)) player.removeEffect(MagiaEffects.LUNAR_BLESSING);
            if (player.hasEffect(MagiaEffects.SUNLIT)) player.removeEffect(MagiaEffects.SUNLIT);
        }
    }

    /** Um pulso, pelo tick do marcador. {@code false} tira o marcador (passiva revogada). */
    public static boolean pulse(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer owner) || !(owner.level() instanceof ServerLevel level)) return false;
        if (!Passives.has(owner, Passive.WANING_MOON)) return false;
        if (!owner.isAlive() || owner.isSpectator()) return true;
        // Estamos dentro do tick de efeitos dela: por efeito nela agora faria o vanilla pular os outros
        // efeitos deste tick (ConcurrentModification engolida). A bencao vai para o fim do tick.
        owner.server.tell(new TickTask(owner.server.getTickCount(), () -> {
            if (owner.isAlive() && owner.level() == level && Passives.has(owner, Passive.WANING_MOON)) apply(owner, level);
        }));
        return true;
    }

    private static void apply(ServerPlayer owner, ServerLevel level) {
        int phase = phase(level);
        if (phase < 0) {
            if (MagiaConfig.LUNAR_DAY_PENALTY.get() && skyCycle(level) && level.isDay()) {
                renew(owner, MagiaEffects.SUNLIT, 0);
            }
            return;
        }
        if (owner.hasEffect(MagiaEffects.SUNLIT)) owner.removeEffect(MagiaEffects.SUNLIT);
        renew(owner, MagiaEffects.LUNAR_BLESSING, phase);
        if (MagiaConfig.LUNAR_NIGHT_VISION.get()) {
            MobEffectInstance vision = owner.getEffect(MobEffects.NIGHT_VISION);
            if (vision == null || (!vision.isInfiniteDuration() && vision.getDuration() < NIGHT_VISION_TICKS - INTERVAL_TICKS * 2)) {
                owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, false, false, false));
            }
        }

        double radius = MagiaConfig.LUNAR_HEAL_RADIUS.get() + MagiaConfig.LUNAR_HEAL_RADIUS_STEP.get() * phase;
        double radiusSqr = radius * radius;
        int regen = phase >= 2 ? 1 : 0;
        Vec3 center = owner.position();
        for (ServerPlayer other : level.players()) {
            if (!other.isAlive() || other.isSpectator() || other.position().distanceToSqr(center) > radiusSqr) continue;
            renew(other, MobEffects.REGENERATION, regen);
        }
        MagiaNetwork.sendVisual(owner, owner, SpellVisualPayload.Kind.LUNAR_AURA, EFFECT_TICKS, center, (float) radius);
    }

    /** A tecla, para quem so tem a lua: diz em que fase a noite esta. */
    public static void report(ServerPlayer owner) {
        int phase = owner.level() instanceof ServerLevel level ? phase(level) : -1;
        owner.displayClientMessage(Component.translatable(phase < 0
                        ? "aurorion_magia.lua.dia" : "aurorion_magia.lua.fase", phase + 1)
                .withStyle(phase < 0 ? ChatFormatting.GRAY : ChatFormatting.AQUA), true);
    }

    /** -1 sem noite (dia, ou dimensao sem ceu); 0 a 3 conforme o poder. */
    static int phase(ServerLevel level) {
        if (!skyCycle(level)) return -1;
        long time = Math.floorMod(level.getDayTime(), 24000L);
        if (time < 13000 || time > 23000) return -1;
        double depth = 1 - Math.abs(time - 18000) / 5000.0;
        double power = depth * moon(level.getMoonPhase());
        return power >= 0.75 ? 3 : power >= 0.5 ? 2 : power >= 0.25 ? 1 : 0;
    }

    /** Fase 0 e a cheia; 1, 2 e 3 minguam; 4 e a nova; 5 a 7 crescem. */
    private static double moon(int moonPhase) {
        return switch (moonPhase) {
            case 0, 1, 2, 3 -> 1.0;
            case 4 -> 0.6;
            default -> 0.85;
        };
    }

    private static boolean skyCycle(ServerLevel level) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime();
    }

    private static void renew(ServerPlayer target, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = target.getEffect(effect);
        if (current != null && current.getAmplifier() > amplifier) return;
        if (current != null && current.getAmplifier() == amplifier
                && (current.isInfiniteDuration() || current.getDuration() > RENEW_BELOW)) return;
        target.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, amplifier, false, false, true));
    }
}
