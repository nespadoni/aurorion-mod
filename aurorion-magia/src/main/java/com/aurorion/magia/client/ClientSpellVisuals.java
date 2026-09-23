package com.aurorion.magia.client;

import com.aurorion.magia.config.MagiaClientConfig;
import com.aurorion.magia.network.SpellVisualPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Feixes e auras das magias, inteiramente no cliente.
 *
 * <p>O servidor manda so "visual X entre A e B por N ticks" ({@link SpellVisualPayload}); daqui para
 * frente cada cliente gera as proprias particulas, acompanhando as entidades que ele ja conhece. Nada
 * volta para o servidor e nada e enviado por particula.
 *
 * <h2>Orcamento</h2>
 *
 * <ul>
 *   <li>No maximo {@value #MAX_ACTIVE} visuais ao mesmo tempo. Uma briga de trinta magos nao vira
 *       trezentos feixes; o excedente simplesmente nao e desenhado.</li>
 *   <li>Feixe: ate {@value #MAX_BEAM_POINTS} pontos por tick, proporcional ao comprimento.</li>
 *   <li>A opcao "Particulas" do vanilla divide tudo: Todas = 1, Reduzidas = 1/2, Minimas = 1/4.</li>
 *   <li>Fora de {@code visualDistance} do olho do jogador, nada e gerado.</li>
 * </ul>
 *
 * <p>Tudo roda no {@code ClientTickEvent}, na thread do cliente, sem alocacao por particula alem do
 * que o proprio motor de particulas faz.
 */
public final class ClientSpellVisuals {
    private static final int MAX_ACTIVE = 48;
    private static final int MAX_BEAM_POINTS = 36;
    private static final int SPIRAL_POINTS = 48;

    private static final ParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.62f, 0.02f, 0.05f), 0.9f);
    private static final ParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.07f, 0.0f, 0.03f), 1.1f);
    private static final ParticleOptions JADE = new DustParticleOptions(new Vector3f(0.55f, 1.0f, 0.74f), 0.8f);
    private static final ParticleOptions SILVER = new DustParticleOptions(new Vector3f(0.88f, 0.92f, 0.96f), 0.7f);

    private static final List<Active> ACTIVE = new ArrayList<>();

    private ClientSpellVisuals() {
    }

    public static void accept(SpellVisualPayload payload) {
        for (Active active : ACTIVE) {
            if (active.kind == payload.kind() && active.casterId == payload.casterId()
                    && active.targetId == payload.targetId()) {
                // Pulso seguinte da mesma canalizacao: so estica a vida, nao duplica o feixe.
                active.ticksLeft = payload.ttl();
                return;
            }
        }
        if (ACTIVE.size() < MAX_ACTIVE) ACTIVE.add(new Active(payload));
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static void tick() {
        if (ACTIVE.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }
        if (minecraft.isPaused()) return;

        int stride = switch (minecraft.options.particles().get()) {
            case ALL -> 1;
            case DECREASED -> 2;
            case MINIMAL -> 4;
        };
        double maxDistance = MagiaClientConfig.VISUAL_DISTANCE.get();
        Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition();
        long time = level.getGameTime();

        for (Iterator<Active> it = ACTIVE.iterator(); it.hasNext(); ) {
            Active active = it.next();
            Entity target = level.getEntity(active.targetId);
            if (--active.ticksLeft < 0 || !(target instanceof LivingEntity living) || !living.isAlive()) {
                it.remove();
                continue;
            }
            if (living.distanceToSqr(eye) > maxDistance * maxDistance) continue;

            switch (active.kind) {
                case CRUCIATUS_BEAM -> {
                    Entity caster = level.getEntity(active.casterId);
                    if (caster instanceof LivingEntity source && time % stride == 0) beam(level, source, living, stride, time);
                }
                case IMPERIUM_AURA -> {
                    if (!active.impactShown) {
                        spiral(level, living, stride);
                        active.impactShown = true;
                    } else if (time % (2L * stride) == 0) {
                        halo(level, living, time);
                    }
                }
            }
        }
    }

    /**
     * Da mao de conjurar ao peito do alvo. Os pontos torcem em helice em volta da linha reta, com
     * amplitude zero nas pontas — parece um raio preso nos dois corpos, nao uma nuvem.
     */
    private static void beam(ClientLevel level, LivingEntity caster, LivingEntity target, int stride, long time) {
        Vec3 from = castingHand(caster);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.62, 0);
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.2) return;

        Vec3 direction = delta.scale(1 / length);
        Vec3 side = direction.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 normal = side.cross(direction);

        int points = Math.max(4, Math.min(MAX_BEAM_POINTS, (int) (length * 2.5)) / stride);
        double phase = (time % 40) * 0.6;
        for (int i = 0; i < points; i++) {
            double t = (i + level.random.nextDouble()) / points;
            double amplitude = Math.sin(t * Math.PI) * 0.14;
            double angle = t * 14 + phase;
            Vec3 at = from.add(delta.scale(t))
                    .add(side.scale(Math.cos(angle) * amplitude))
                    .add(normal.scale(Math.sin(angle) * amplitude));
            level.addParticle(i % 3 == 0 ? SHADOW : BLOOD, at.x, at.y, at.z, 0, 0, 0);
        }

        // Ferida: um respingo escuro no ponto de impacto.
        level.addParticle(SHADOW,
                to.x + (level.random.nextDouble() - 0.5) * 0.3,
                to.y + (level.random.nextDouble() - 0.5) * 0.3,
                to.z + (level.random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
    }

    /** Duas voltas descendo da cabeca aos pes, jade e prata alternados, com faiscas no topo. Uma vez so. */
    private static void spiral(ClientLevel level, LivingEntity target, int stride) {
        double height = target.getBbHeight();
        double radius = target.getBbWidth() * 0.75 + 0.15;
        Vec3 base = target.position();

        int points = SPIRAL_POINTS / stride;
        for (int i = 0; i < points; i++) {
            double t = i / (double) points;
            double angle = t * Math.PI * 4;
            level.addParticle(i % 2 == 0 ? JADE : SILVER,
                    base.x + Math.cos(angle) * radius,
                    base.y + height * (1.05 - t),
                    base.z + Math.sin(angle) * radius, 0, -0.02, 0);
        }
        for (int i = 0; i < 6 / stride + 1; i++) {
            level.addParticle(ParticleTypes.END_ROD,
                    base.x, base.y + height + 0.2, base.z,
                    (level.random.nextDouble() - 0.5) * 0.08, 0.04, (level.random.nextDouble() - 0.5) * 0.08);
        }
    }

    /** Aura na cabeca enquanto durar: dois pontos girando em anel, meio por cima do cranio. */
    private static void halo(ClientLevel level, LivingEntity target, long time) {
        double radius = target.getBbWidth() * 0.55 + 0.1;
        double y = target.getY() + target.getBbHeight() + 0.15;
        double angle = time * 0.35;
        level.addParticle(JADE, target.getX() + Math.cos(angle) * radius, y, target.getZ() + Math.sin(angle) * radius, 0, 0, 0);
        level.addParticle(SILVER, target.getX() - Math.cos(angle) * radius, y, target.getZ() - Math.sin(angle) * radius, 0, 0, 0);
    }

    /**
     * Aproximacao da mao de conjurar: altura do ombro, deslocada para o lado do braco principal e um
     * pouco a frente. Sem ler o modelo animado — seria caro e dependeria do renderer de cada mob.
     */
    private static Vec3 castingHand(LivingEntity caster) {
        float yaw = caster.yBodyRot * Mth.DEG_TO_RAD;
        double sideSign = caster instanceof Player player && player.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(sideSign * caster.getBbWidth() * 0.6);
        Vec3 look = caster.getViewVector(1.0f).scale(0.45);
        return caster.position().add(0, caster.getBbHeight() * 0.72, 0).add(right).add(look);
    }

    private static final class Active {
        final SpellVisualPayload.Kind kind;
        final int casterId;
        final int targetId;
        int ticksLeft;
        boolean impactShown;

        Active(SpellVisualPayload payload) {
            this.kind = payload.kind();
            this.casterId = payload.casterId();
            this.targetId = payload.targetId();
            this.ticksLeft = payload.ttl();
        }
    }
}
