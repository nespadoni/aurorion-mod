package com.aurorion.magia.client;

import com.aurorion.magia.config.MagiaClientConfig;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.network.SpellVisualPayload.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Estado e particulas dos visuais de magia, inteiramente no cliente.
 *
 * <p>O servidor manda so "visual X, destas entidades/deste ponto, por N ticks"
 * ({@link SpellVisualPayload}). Daqui para frente cada cliente gera as proprias particulas e o
 * {@link SigilRenderer} desenha os selos, acompanhando as entidades que o cliente ja conhece. Nada
 * volta para o servidor e nada e enviado por particula.
 *
 * <h2>Orcamento</h2>
 *
 * <ul>
 *   <li>No maximo {@value #MAX_ACTIVE} visuais ao mesmo tempo; o excedente nao e desenhado.</li>
 *   <li>A opcao "Particulas" do vanilla divide tudo: Todas = 1, Reduzidas = 1/2, Minimas = 1/4.</li>
 *   <li>Fora de {@code visualDistance}, nenhuma particula; os selos seguem ate 64 blocos.</li>
 * </ul>
 */
public final class ClientSpellVisuals {
    private static final int MAX_ACTIVE = 64;
    private static final int MAX_BEAM_POINTS = 36;
    private static final float MAX_EXTRA = 64;
    private static final int MAX_TTL = 12_000;
    /** Ate onde os vultos da Presenca Aterradora rondam, por mais largo que seja o raio do medo. */
    private static final double WISP_RADIUS = 6;

    static final ParticleOptions BLOOD = dust(0.62f, 0.02f, 0.05f, 0.9f);
    static final ParticleOptions SHADOW = dust(0.07f, 0.0f, 0.03f, 1.1f);
    static final ParticleOptions JADE = dust(0.55f, 1.0f, 0.74f, 0.8f);
    static final ParticleOptions SILVER = dust(0.88f, 0.92f, 0.96f, 0.7f);
    static final ParticleOptions CRIMSON = dust(0.8f, 0.12f, 0.16f, 1.0f);
    static final ParticleOptions VIOLET = dust(0.62f, 0.35f, 1.0f, 0.9f);
    static final ParticleOptions LILAC = dust(0.8f, 0.72f, 0.95f, 0.8f);
    static final ParticleOptions IRON = dust(0.6f, 0.62f, 0.68f, 0.9f);
    static final ParticleOptions VOID = dust(0.03f, 0.0f, 0.06f, 1.4f);
    static final ParticleOptions DEATH = dust(0.23f, 1.0f, 0.42f, 1.1f);
    static final ParticleOptions DEATH_DARK = dust(0.02f, 0.3f, 0.1f, 1.2f);
    static final ParticleOptions FROST_WHITE = dust(0.92f, 0.97f, 1.0f, 0.9f);
    static final ParticleOptions WATER = dust(0.30f, 0.62f, 0.92f, 0.9f);
    /** Preto e grande: e a fumaça da aura de terror, que escurece em vez de brilhar. */
    static final ParticleOptions DREAD = dust(0.02f, 0.0f, 0.03f, 1.8f);
    /** O ar visivel das magias de vento. */
    static final ParticleOptions WIND = dust(0.85f, 1.0f, 0.94f, 0.7f);

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final List<Active> VIEW = Collections.unmodifiableList(ACTIVE);

    private ClientSpellVisuals() {
    }

    /** Leitura para o renderer, na mesma thread. */
    static List<Active> active() {
        return VIEW;
    }

    public static void accept(SpellVisualPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        // Um ponto que nao e numero travaria o calculo de particulas; descarta em vez de desenhar.
        Vec3 at = payload.pos();
        if (!Double.isFinite(at.x) || !Double.isFinite(at.y) || !Double.isFinite(at.z)) return;

        switch (payload.kind()) {
            case VINCULUM_SNAP -> {
                Active chain = find(Kind.VINCULUM, payload.targetId(), null);
                if (chain != null) chain.flash = 1;
                snapBurst(level, payload);
                return;
            }
            case SIGILLUM_BREAK -> ACTIVE.removeIf(a -> a.kind == Kind.SIGILLUM && a.pos.distanceToSqr(payload.pos()) < 0.01);
            case SIGILLUM_DENY -> {
                Active seal = find(Kind.SIGILLUM, -1, payload.pos());
                if (seal != null) {
                    seal.flash = 1;
                    burst(level, payload.pos(), VIOLET, 10, 0.12);
                    return;
                }
            }
            default -> {
            }
        }

        Active existing = find(payload.kind(), payload.kind().anchoredToPoint() ? -1 : payload.targetId(),
                payload.kind().anchoredToPoint() ? payload.pos() : null);
        if (existing != null && (payload.kind().anchoredToPoint() || existing.casterId == payload.casterId())) {
            // Pulso seguinte da mesma canalizacao: estica a vida, nao duplica o visual.
            existing.ticksLeft = Math.max(existing.ticksLeft, payload.ttl());
            return;
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
            active.age++;
            active.flash = Math.max(0, active.flash - 0.12f);
            LivingEntity target = active.target(level);
            if (--active.ticksLeft < 0 || !active.kind.anchoredToPoint() && target == null) {
                it.remove();
                continue;
            }
            Vec3 at = target != null ? target.position() : active.pos;
            if (at.distanceToSqr(eye) > maxDistance * maxDistance) continue;
            particles(level, active, target, stride, time);
        }
    }

    /** O visual deste tipo esta mirando a entidade {@code id} agora? (Mao do Algoz segurando voce.) */
    static boolean targets(Kind kind, int id) {
        for (Active active : ACTIVE) {
            if (active.kind == kind && active.targetId == id) return true;
        }
        return false;
    }

    /** Quem prende o olhar da entidade {@code id} (Aspectus Captus), se alguem prende. */
    @Nullable
    static Entity captorOf(ClientLevel level, int id) {
        for (Active active : ACTIVE) {
            if (active.kind == Kind.ASPECTUS_CAPTUS && active.targetId == id) return active.caster(level);
        }
        return null;
    }

    /** Peso (0..1) da escuridao do Lux Vorata no ponto da camera; o maior entre as zonas ativas. */
    static float darkness(Vec3 camera) {
        float weight = 0;
        for (Active active : ACTIVE) {
            if (active.kind != Kind.LUX_VORATA) continue;
            double radius = active.extra;
            double distance = Math.sqrt(camera.distanceToSqr(active.pos));
            float inside = (float) Mth.clamp((radius + 3 - distance) / 3, 0, 1);
            weight = Math.max(weight, inside * Mth.clamp(active.age / 10f, 0, 1) * Mth.clamp(active.ticksLeft / 30f, 0, 1));
        }
        return weight;
    }

    /**
     * Peso (0..1) da nevoa de terror na camera: 0 fora de qualquer aura, 1 colado em quem a carrega.
     *
     * <p>A posicao vem da <b>entidade</b>, e nao do ponto do pacote: o portador anda, e a nevoa tem
     * que andar com ele sem uma mensagem por tick. O pulso da aura so renova o prazo de validade.
     *
     * <p>Quem chama decide <i>a quem</i> isso se aplica — so quem esta com {@code apavorado} ve a
     * nevoa; o dono da aura enxerga a propria praça normalmente.
     */
    static float terror(ClientLevel level, Vec3 camera) {
        float weight = 0;
        for (Active active : ACTIVE) {
            if (active.kind != Kind.TERROR_AURA) continue;
            LivingEntity owner = active.target(level);
            if (owner == null) continue;
            double radius = Math.max(1, active.extra);
            double distance = Math.sqrt(owner.position().distanceToSqr(camera));
            // A borda do raio e onde o veu comeca; dos 60% para dentro, ja e parede preta. O trecho de
            // rampa e curto de proposito: a Escuridao que o servidor poe nao tem meio-tom, e uma nevoa
            // que so fechasse colado no vilao deixaria o mundo apagado com o horizonte limpo.
            float inside = (float) Mth.clamp((radius - distance) / (radius * 0.4), 0, 1);
            weight = Math.max(weight, inside * Mth.clamp(active.ticksLeft / 20f, 0, 1));
        }
        return weight;
    }

    // --- Particulas por magia -----------------------------------------------------------------

    private static void particles(ClientLevel level, Active a, @Nullable LivingEntity target, int stride, long time) {
        RandomSource random = level.random;
        switch (a.kind) {
            case CRUCIATUS_BEAM -> {
                if (a.caster(level) instanceof LivingEntity caster && target != null && time % stride == 0) {
                    beam(level, castingHand(caster), chest(target), stride, time, BLOOD, SHADOW, 0.14);
                }
            }
            case IMPERIUM_AURA -> {
                if (target == null) return;
                if (a.age == 1) spiral(level, target, stride, JADE, SILVER);
                else if (time % (2L * stride) == 0) halo(level, target, time);
            }
            case VINCULUM -> {
                if (target == null) return;
                float tension = a.tension(target.position());
                if (tension > 0 && time % (2L * stride) == 0) {
                    Vec3 from = a.pos.add(0, 0.2, 0);
                    Vec3 to = waist(target);
                    for (int i = 0; i < 3; i++) {
                        Vec3 p = from.lerp(to, random.nextDouble());
                        level.addParticle(CRIMSON, p.x, p.y, p.z, 0, 0, 0);
                    }
                }
                if (time % 12 == 0) level.addParticle(ParticleTypes.CRIMSON_SPORE, a.pos.x, a.pos.y + 0.1, a.pos.z, 0, 0.02, 0);
            }
            case TRANSPOSITIO -> {
                LivingEntity caster = a.caster(level) instanceof LivingEntity c ? c : null;
                if (a.age == 1) {
                    if (caster != null && target != null) arc(level, caster.position(), target.position(), stride);
                    column(level, a.pos, 20 / stride);
                    if (caster != null) column(level, caster.position(), 20 / stride);
                } else if (a.age < 12 && time % stride == 0) {
                    if (target != null) rise(level, target.position(), ParticleTypes.REVERSE_PORTAL);
                    if (caster != null) rise(level, caster.position(), ParticleTypes.REVERSE_PORTAL);
                }
            }
            case MANUS_GRIP -> {
                if (!(a.caster(level) instanceof LivingEntity caster) || target == null || time % stride != 0) return;
                beam(level, castingHand(caster), waist(target), stride * 3, time, VIOLET, LILAC, 0.3);
                if (random.nextInt(2) == 0) {
                    Vec3 p = waist(target).add(offset(random, target.getBbWidth()));
                    level.addParticle(ParticleTypes.ENCHANT, p.x, p.y, p.z, 0, -0.1, 0);
                }
            }
            case MANUS_VACUA -> {
                if (a.age != 1) return;
                burst(level, a.pos, SILVER, 14 / stride, 0.15);
                for (int i = 0; i < 6 / stride + 1; i++) {
                    level.addParticle(ParticleTypes.ENCHANTED_HIT, a.pos.x, a.pos.y, a.pos.z,
                            random.nextGaussian() * 0.2, 0.15, random.nextGaussian() * 0.2);
                }
            }
            case GENUA_FLECTE -> {
                if (target == null) return;
                if (a.age < 14 && time % stride == 0) {
                    Vec3 top = target.position().add(0, target.getBbHeight() + 0.4, 0);
                    for (int i = 0; i < 3; i++) {
                        Vec3 p = top.add(offset(random, target.getBbWidth()));
                        level.addParticle(LILAC, p.x, p.y, p.z, 0, -0.25, 0);
                    }
                } else if (time % (10L * stride) == 0) {
                    Vec3 p = target.position().add(offset(random, 1.0).multiply(1, 0, 1));
                    level.addParticle(ParticleTypes.SOUL, p.x, p.y + 0.05, p.z, 0, 0.02, 0);
                }
            }
            case VOX_INTERDICTA -> {
                if (target == null || time % (2L * stride) != 0) return;
                Vec3 neck = neck(target);
                double angle = time * 0.4;
                double radius = target.getBbWidth() * 0.45 + 0.08;
                level.addParticle(VOID, neck.x + Math.cos(angle) * radius, neck.y, neck.z + Math.sin(angle) * radius, 0, 0.01, 0);
                if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, neck.x, neck.y, neck.z, 0, 0.02, 0);
            }
            case SIGILLUM -> {
                if (time % 40 == 0) level.addParticle(ParticleTypes.WITCH, a.pos.x, a.pos.y, a.pos.z, 0, 0.03, 0);
            }
            case SIGILLUM_BREAK -> {
                if (a.age == 1) burst(level, a.pos, VIOLET, 20 / stride, 0.2);
            }
            case DEIECTIO -> {
                if (target == null) return;
                if (!a.landed && a.age > 2 && target.onGround()) {
                    a.landed = true;
                    a.landedAge = a.age;
                    impact(level, target, stride);
                } else if (!a.landed && time % stride == 0) {
                    for (int i = 0; i < 3; i++) {
                        Vec3 p = target.position().add(offset(random, target.getBbWidth() + 0.3)).add(0, target.getBbHeight(), 0);
                        level.addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z, 0, -0.6, 0);
                    }
                }
            }
            case LUX_VORATA -> {
                double radius = a.extra;
                if (a.age <= 12) {
                    // A luz sendo sugada para o centro.
                    for (int i = 0; i < 8 / stride + 1; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        Vec3 p = a.pos.add(Math.cos(angle) * radius, 0.3 + random.nextDouble() * 1.5, Math.sin(angle) * radius);
                        Vec3 v = a.pos.add(0, 1, 0).subtract(p).scale(0.08);
                        level.addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, v.x, v.y, v.z);
                    }
                } else if (time % (3L * stride) == 0) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double r = Math.sqrt(random.nextDouble()) * radius;
                    level.addParticle(VOID, a.pos.x + Math.cos(angle) * r, a.pos.y + 0.2 + random.nextDouble(),
                            a.pos.z + Math.sin(angle) * r, 0, 0.01, 0);
                }
            }
            case FERRUM_LIGATUM -> {
                if (target == null || time % (3L * stride) != 0) return;
                double angle = time * 0.25;
                double radius = target.getBbWidth() * 0.6 + 0.12;
                for (int band = 0; band < 2; band++) {
                    double y = target.getY() + target.getBbHeight() * (band == 0 ? 0.35 : 0.72);
                    double a2 = band == 0 ? angle : -angle;
                    level.addParticle(IRON, target.getX() + Math.cos(a2) * radius, y, target.getZ() + Math.sin(a2) * radius, 0, 0, 0);
                }
                if (random.nextInt(6) == 0) {
                    Vec3 p = chest(target).add(offset(random, 0.3));
                    level.addParticle(ParticleTypes.CRIT, p.x, p.y, p.z, 0, 0, 0);
                }
            }
            case TEMPUS_SISTERE -> {
                double radius = a.extra;
                if (a.age <= 14) {
                    // A onda que para o tempo, abrindo do centro ate a borda.
                    double ring = radius * a.age / 14.0;
                    int count = Math.max(8, (int) (ring * 6) / stride);
                    for (int i = 0; i < count; i++) {
                        double angle = i * Math.PI * 2 / count;
                        level.addParticle(i % 3 == 0 ? FROST_WHITE : ParticleTypes.SNOWFLAKE,
                                a.pos.x + Math.cos(angle) * ring, a.pos.y + 0.3, a.pos.z + Math.sin(angle) * ring, 0, 0, 0);
                    }
                } else if (time % (4L * stride) == 0) {
                    for (int i = 0; i < 3; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        double r = Math.sqrt(random.nextDouble()) * radius;
                        level.addParticle(i == 0 ? ParticleTypes.END_ROD : ParticleTypes.SNOWFLAKE,
                                a.pos.x + Math.cos(angle) * r, a.pos.y + 0.3 + random.nextDouble() * 2.5,
                                a.pos.z + Math.sin(angle) * r, 0, i == 0 ? 0 : -0.01, 0);
                    }
                }
            }
            case MORTEM_DICO -> {
                Vec3 chest = a.pos.add(0, a.extra * 0.6, 0);
                if (a.age == 1) {
                    if (a.caster(level) instanceof LivingEntity caster) {
                        beam(level, castingHand(caster), chest, 1, time, DEATH, DEATH_DARK, 0.05);
                    }
                    burst(level, chest, DEATH, 30 / stride, 0.25);
                    burst(level, chest, ParticleTypes.SOUL, 10 / stride + 1, 0.08);
                    level.addParticle(ParticleTypes.FLASH, chest.x, chest.y, chest.z, 0, 0, 0);
                } else if (a.age < 30 && time % (2L * stride) == 0) {
                    Vec3 p = a.pos.add(offset(random, 1.2).multiply(1, 0, 1));
                    level.addParticle(ParticleTypes.SOUL, p.x, p.y + 0.1, p.z, 0, 0.06, 0);
                    level.addParticle(DEATH_DARK, p.x, p.y + 0.2, p.z, 0, 0.02, 0);
                }
            }
            case ASPECTUS_CAPTUS -> {
                if (target == null) return;
                Vec3 eyes = target.getEyePosition();
                if (a.age == 1) burst(level, eyes, VIOLET, 12 / stride + 1, 0.06);
                if (time % (3L * stride) == 0 && a.caster(level) instanceof LivingEntity caster) {
                    // O fio do olhar: um ponto escuro correndo dos olhos do cativo ate os do captor.
                    Vec3 to = caster.getEyePosition();
                    double t = (time % 12) / 12.0;
                    Vec3 p = eyes.lerp(to, t);
                    level.addParticle(VOID, p.x, p.y, p.z, 0, 0, 0);
                    level.addParticle(ParticleTypes.WITCH, eyes.x, eyes.y + 0.35, eyes.z, 0, 0.01, 0);
                }
            }
            case DEIECTIO_AREA -> {
                double radius = a.extra;
                if (a.age <= 12) {
                    // A onda abrindo do centro ate a borda, rasgando o chao.
                    double ring = radius * a.age / 12.0;
                    int count = Math.max(10, (int) (ring * 5) / stride);
                    for (int i = 0; i < count; i++) {
                        double angle = i * Math.PI * 2 / count;
                        double x = a.pos.x + Math.cos(angle) * ring;
                        double z = a.pos.z + Math.sin(angle) * ring;
                        BlockState under = level.getBlockState(BlockPos.containing(x, a.pos.y - 0.5, z));
                        level.addParticle(under.isAir() ? ParticleTypes.CLOUD : new BlockParticleOption(ParticleTypes.BLOCK, under),
                                x, a.pos.y + 0.1, z, 0, 0.25, 0);
                    }
                } else if (time % (2L * stride) == 0) {
                    // Rastros de vento descendo dentro do raio: ninguem se sustenta ali.
                    for (int i = 0; i < 3 / stride + 1; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        double r = Math.sqrt(random.nextDouble()) * radius;
                        level.addParticle(LILAC, a.pos.x + Math.cos(angle) * r, a.pos.y + 1 + random.nextDouble() * 3,
                                a.pos.z + Math.sin(angle) * r, 0, -0.6, 0);
                    }
                }
            }
            case ASPECTUS_AREA -> {
                double radius = a.extra;
                if (time % (2L * stride) != 0) return;
                // Tudo converge para quem conjurou: o olhar da praca inteira, em particula.
                for (int i = 0; i < 2 / stride + 1; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    Vec3 from = a.pos.add(Math.cos(angle) * radius, 0.8 + random.nextDouble() * 1.4, Math.sin(angle) * radius);
                    Vec3 velocity = a.pos.add(0, 1.4, 0).subtract(from).scale(0.04);
                    level.addParticle(VIOLET, from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
                }
                level.addParticle(ParticleTypes.WITCH, a.pos.x, a.pos.y + 2.2, a.pos.z, 0, 0.02, 0);
            }
            case MORTEM_AREA -> {
                double radius = a.extra;
                if (a.age == 1) {
                    burst(level, a.pos.add(0, 1, 0), DEATH, 40 / stride, 0.4);
                    level.addParticle(ParticleTypes.FLASH, a.pos.x, a.pos.y + 1, a.pos.z, 0, 0, 0);
                } else if (time % (2L * stride) == 0) {
                    // As almas subindo de todo o circulo, uma a uma.
                    for (int i = 0; i < 3 / stride + 1; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        double r = Math.sqrt(random.nextDouble()) * radius;
                        double x = a.pos.x + Math.cos(angle) * r;
                        double z = a.pos.z + Math.sin(angle) * r;
                        level.addParticle(ParticleTypes.SOUL, x, a.pos.y + 0.1, z, 0, 0.07, 0);
                        level.addParticle(DEATH_DARK, x, a.pos.y + 0.3, z, 0, 0.03, 0);
                    }
                }
            }
            case MUNDUS_VACUUS -> {
                if (target == null) return;
                if (a.age == 1) {
                    burst(level, chest(target), VOID, 26 / stride, 0.3);
                    ring(level, waist(target), 1.2, 20 / stride, ParticleTypes.SQUID_INK, 0.12);
                } else if (time % (3L * stride) == 0) {
                    // O veu fechando: pontos de vazio girando rente ao corpo, sem brilho nenhum.
                    double angle = time * 0.18;
                    double r = target.getBbWidth() * 0.7 + 0.35;
                    level.addParticle(VOID, target.getX() + Math.cos(angle) * r,
                            target.getY() + target.getBbHeight() * (0.2 + (time % 40) / 40.0 * 0.8),
                            target.getZ() + Math.sin(angle) * r, 0, 0, 0);
                }
            }
            case DOLOR_UNIVERSUS -> {
                if (target == null || time % (2L * stride) != 0) return;
                double radius = a.extra;
                Vec3 hand = castingHand(target);
                int beams = 0;
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(radius), e -> e != target && e.isAlive() && !e.onGround())) {
                    if (++beams > 8) break;
                    beam(level, hand, chest(victim), stride * 3, time, BLOOD, SHADOW, 0.16);
                }
                double angle = random.nextDouble() * Math.PI * 2;
                level.addParticle(CRIMSON, target.getX() + Math.cos(angle) * radius, target.getY() + 0.1,
                        target.getZ() + Math.sin(angle) * radius, 0, 0.05, 0);
            }
            case SUBMERSIO -> {
                if (target == null || time % (2L * stride) != 0) return;
                // As bolhas escapando da boca: e o unico sinal, de fora, de que alguem esta se
                // afogando em pe no meio da rua.
                Vec3 mouth = target.position().add(0, target.getEyeHeight() - 0.1, 0);
                for (int i = 0; i < 2 / stride + 1; i++) {
                    Vec3 p = mouth.add(offset(random, 0.25));
                    level.addParticle(ParticleTypes.BUBBLE, p.x, p.y, p.z,
                            random.nextGaussian() * 0.01, 0.06, random.nextGaussian() * 0.01);
                }
                if (time % (8L * stride) == 0) {
                    Vec3 p = waist(target).add(offset(random, target.getBbWidth()));
                    level.addParticle(ParticleTypes.FALLING_WATER, p.x, p.y, p.z, 0, 0, 0);
                }
            }
            case UNDA_MAGNA -> {
                // Uma unica leva, no instante da conjuracao: a onda passa e acaba.
                double radius = Math.abs(a.extra);
                boolean circle = a.extra > 0;
                if (a.age > 12) return;
                double front = radius * (a.age / 12.0);
                int points = (int) Math.max(8, front * 6) / stride;
                // Sem agachar, a onda so abre no arco a frente: a direcao vem do corpo de quem
                // conjurou, que o cliente ja conhece — nao ha vetor nenhum viajando pela rede.
                double facing = a.caster(level) instanceof LivingEntity caster
                        ? Math.toRadians(caster.yBodyRot + 90)
                        : 0;
                for (int i = 0; i < points; i++) {
                    double angle = circle
                            ? random.nextDouble() * Math.PI * 2
                            : facing + (random.nextDouble() - 0.5) * (Math.PI * 2 / 3);
                    double x = a.pos.x + Math.cos(angle) * front;
                    double z = a.pos.z + Math.sin(angle) * front;
                    level.addParticle(ParticleTypes.SPLASH, x, a.pos.y + 0.2 + random.nextDouble() * 0.9, z,
                            Math.cos(angle) * 0.25, 0.12, Math.sin(angle) * 0.25);
                    if (i % 3 == 0) level.addParticle(WATER, x, a.pos.y + 0.6, z, 0, 0.05, 0);
                }
            }
            case CARCER_AQUAE -> {
                if (target == null) return;
                double radius = target.getBbWidth() * 0.9 + 0.45;
                Vec3 center = waist(target);
                if (a.age == 1) {
                    ring(level, center, radius, 24 / stride, ParticleTypes.SPLASH, 0.15);
                } else if (time % (2L * stride) == 0) {
                    // A casca girando: dois pontos em orbitas opostas, na altura que sobe e desce.
                    double angle = time * 0.16;
                    double lift = Math.sin(time * 0.07) * target.getBbHeight() * 0.35;
                    level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP,
                            center.x + Math.cos(angle) * radius, center.y + lift, center.z + Math.sin(angle) * radius,
                            0, 0.02, 0);
                    level.addParticle(WATER,
                            center.x - Math.cos(angle) * radius, center.y - lift, center.z - Math.sin(angle) * radius,
                            0, 0, 0);
                }
            }
            case TERROR_AURA -> {
                if (target == null) return;
                // O medo alcança 30 blocos, mas a <b>aura</b> e o que exala do corpo: os vultos rondam
                // perto, senao eles nasceriam a trinta blocos de distancia e ninguem os ligaria a
                // pessoa. Quem sente o alcance inteiro e a nevoa e a batida do coracao, nao isto.
                double radius = Math.min(a.extra, WISP_RADIUS);
                // Duas camadas: a fumaça que sobe do corpo e os vultos que rondam o circulo. Nenhuma
                // sai do servidor — o payload da aura e um so por segundo, e tudo daqui e local.
                if (time % stride == 0) {
                    Vec3 p = target.position().add(offset(random, target.getBbWidth() * 1.6).multiply(1, 0, 1));
                    level.addParticle(DREAD, p.x, p.y + random.nextDouble() * target.getBbHeight(), p.z,
                            0, 0.02 + random.nextDouble() * 0.03, 0);
                    level.addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.1, p.z, 0, 0.015, 0);
                }
                if (time % (3L * stride) == 0) {
                    // Um vulto: nasce na borda do raio, na altura de um corpo, e desliza de lado.
                    double angle = random.nextDouble() * Math.PI * 2;
                    double r = radius * (0.45 + random.nextDouble() * 0.55);
                    double x = target.getX() + Math.cos(angle) * r;
                    double z = target.getZ() + Math.sin(angle) * r;
                    double y = target.getY() + 0.6 + random.nextDouble() * 1.6;
                    Vec3 sideways = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(0.08);
                    level.addParticle(DREAD, x, y, z, sideways.x, 0.01, sideways.z);
                    if (random.nextInt(4) == 0) {
                        level.addParticle(ParticleTypes.SOUL, x, y, z, sideways.x * 0.5, 0.02, sideways.z * 0.5);
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void snapBurst(ClientLevel level, SpellVisualPayload payload) {
        if (!(level.getEntity(payload.targetId()) instanceof LivingEntity target)) return;
        Vec3 from = payload.pos().add(0, 0.2, 0);
        Vec3 to = waist(target);
        int points = (int) Mth.clamp(from.distanceTo(to) * 3, 6, 30);
        for (int i = 0; i < points; i++) {
            Vec3 p = from.lerp(to, i / (double) points);
            level.addParticle(i % 2 == 0 ? CRIMSON : ParticleTypes.CRIT, p.x, p.y, p.z, 0, 0.02, 0);
        }
    }

    /**
     * Da mao ao alvo. Os pontos torcem em helice em volta da linha reta, com amplitude zero nas
     * pontas — parece um raio preso nos dois corpos, nao uma nuvem.
     */
    private static void beam(ClientLevel level, Vec3 from, Vec3 to, int stride, long time,
                             ParticleOptions main, ParticleOptions dark, double twist) {
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
            double amplitude = Math.sin(t * Math.PI) * twist;
            double angle = t * 14 + phase;
            Vec3 at = from.add(delta.scale(t))
                    .add(side.scale(Math.cos(angle) * amplitude))
                    .add(normal.scale(Math.sin(angle) * amplitude));
            level.addParticle(i % 3 == 0 ? dark : main, at.x, at.y, at.z, 0, 0, 0);
        }
    }

    /** Duas voltas descendo da cabeca aos pes, com faiscas no topo. */
    private static void spiral(ClientLevel level, LivingEntity target, int stride, ParticleOptions a, ParticleOptions b) {
        double height = target.getBbHeight();
        double radius = target.getBbWidth() * 0.75 + 0.15;
        Vec3 base = target.position();
        int points = 48 / stride;
        for (int i = 0; i < points; i++) {
            double t = i / (double) points;
            double angle = t * Math.PI * 4;
            level.addParticle(i % 2 == 0 ? a : b, base.x + Math.cos(angle) * radius, base.y + height * (1.05 - t),
                    base.z + Math.sin(angle) * radius, 0, -0.02, 0);
        }
        for (int i = 0; i < 6 / stride + 1; i++) {
            level.addParticle(ParticleTypes.END_ROD, base.x, base.y + height + 0.2, base.z,
                    (level.random.nextDouble() - 0.5) * 0.08, 0.04, (level.random.nextDouble() - 0.5) * 0.08);
        }
    }

    private static void halo(ClientLevel level, LivingEntity target, long time) {
        double radius = target.getBbWidth() * 0.55 + 0.1;
        double y = target.getY() + target.getBbHeight() + 0.15;
        double angle = time * 0.35;
        level.addParticle(JADE, target.getX() + Math.cos(angle) * radius, y, target.getZ() + Math.sin(angle) * radius, 0, 0, 0);
        level.addParticle(SILVER, target.getX() - Math.cos(angle) * radius, y, target.getZ() - Math.sin(angle) * radius, 0, 0, 0);
    }

    private static void ring(ClientLevel level, Vec3 center, double radius, int count, ParticleOptions particle, double speed) {
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            level.addParticle(particle, center.x + cos * radius, center.y, center.z + sin * radius, cos * speed, 0, sin * speed);
        }
    }

    private static void burst(ClientLevel level, Vec3 at, ParticleOptions particle, int count, double speed) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            level.addParticle(particle, at.x, at.y, at.z,
                    random.nextGaussian() * speed, random.nextGaussian() * speed, random.nextGaussian() * speed);
        }
    }

    /** Arco de portal ligando as duas pontas da troca. */
    private static void arc(ClientLevel level, Vec3 from, Vec3 to, int stride) {
        int points = Math.max(6, (int) (from.distanceTo(to) * 3) / stride);
        double lift = Math.min(3, from.distanceTo(to) * 0.25);
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            Vec3 p = from.lerp(to, t).add(0, 1 + Math.sin(t * Math.PI) * lift, 0);
            level.addParticle(ParticleTypes.PORTAL, p.x, p.y, p.z, 0, 0, 0);
        }
    }

    private static void column(ClientLevel level, Vec3 at, int count) {
        for (int i = 0; i < count; i++) {
            Vec3 p = at.add(offset(level.random, 0.5).multiply(1, 0, 1)).add(0, level.random.nextDouble() * 2, 0);
            level.addParticle(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 0, 0.05, 0);
        }
    }

    private static void rise(ClientLevel level, Vec3 at, ParticleOptions particle) {
        Vec3 p = at.add(offset(level.random, 0.6).multiply(1, 0, 1));
        level.addParticle(particle, p.x, p.y + 0.1, p.z, 0, 0.08, 0);
    }

    /** Aterrissagem do Deiectio: poeira do bloco de baixo em anel, e um estouro. */
    private static void impact(ClientLevel level, LivingEntity target, int stride) {
        BlockPos below = target.blockPosition().below();
        BlockState state = level.getBlockState(below);
        Vec3 at = target.position();
        if (!state.isAir()) {
            ring(level, at.add(0, 0.1, 0), 0.3, 24 / stride, new BlockParticleOption(ParticleTypes.BLOCK, state), 0.35);
        }
        ring(level, at.add(0, 0.2, 0), 0.5, 12 / stride, ParticleTypes.POOF, 0.2);
    }

    static Vec3 castingHand(LivingEntity caster) {
        return castingHand(caster, 1.0f);
    }

    /**
     * Aproximacao da mao de conjurar: altura do ombro, deslocada para o lado do braco principal e um
     * pouco a frente. Sem ler o modelo animado — seria caro e dependeria do renderer de cada mob.
     */
    static Vec3 castingHand(LivingEntity caster, float partial) {
        float yaw = Mth.lerp(partial, caster.yBodyRotO, caster.yBodyRot) * Mth.DEG_TO_RAD;
        double sideSign = caster instanceof Player player && player.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(sideSign * caster.getBbWidth() * 0.6);
        Vec3 look = caster.getViewVector(partial).scale(0.45);
        return caster.getPosition(partial).add(0, caster.getBbHeight() * 0.72, 0).add(right).add(look);
    }

    static Vec3 chest(Entity target) {
        return target.position().add(0, target.getBbHeight() * 0.62, 0);
    }

    static Vec3 waist(Entity target) {
        return target.position().add(0, target.getBbHeight() * 0.5, 0);
    }

    static Vec3 neck(Entity target) {
        return target.position().add(0, target.getEyeHeight() - 0.2, 0);
    }

    private static Vec3 offset(RandomSource random, double spread) {
        return new Vec3((random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * spread,
                (random.nextDouble() - 0.5) * spread);
    }

    private static ParticleOptions dust(float r, float g, float b, float scale) {
        return new DustParticleOptions(new Vector3f(r, g, b), scale);
    }

    @Nullable
    private static Active find(Kind kind, int targetId, @Nullable Vec3 pos) {
        for (Active active : ACTIVE) {
            if (active.kind != kind) continue;
            if (pos != null ? active.pos.distanceToSqr(pos) < 0.01 : active.targetId == targetId) return active;
        }
        return null;
    }

    /** Um visual vivo. Campos lidos pelo {@link SigilRenderer}. */
    static final class Active {
        final Kind kind;
        final int casterId;
        final int targetId;
        final Vec3 pos;
        final float extra;
        final int ttl;
        int ticksLeft;
        int age;
        /** Clarao momentaneo (puxao da corrente, lacre recusando); decai sozinho. */
        float flash;
        boolean landed;
        int landedAge;

        Active(SpellVisualPayload payload) {
            this.kind = payload.kind();
            this.casterId = payload.casterId();
            this.targetId = payload.targetId();
            this.pos = payload.pos();
            // Tetos defensivos: raio/intensidade ate 64 (o maior raio real e 18) e vida ate 10 min (o
            // lacre mais longo e 9). Um valor absurdo no pacote nao vira um laco de particulas sem fim.
            float extra = payload.extra();
            this.extra = Float.isFinite(extra) ? Mth.clamp(extra, -MAX_EXTRA, MAX_EXTRA) : 0;
            this.ttl = Mth.clamp(payload.ttl(), 0, MAX_TTL);
            this.ticksLeft = this.ttl;
        }

        @Nullable
        LivingEntity target(ClientLevel level) {
            return targetId >= 0 && level.getEntity(targetId) instanceof LivingEntity living && living.isAlive() ? living : null;
        }

        @Nullable
        Entity caster(ClientLevel level) {
            return casterId >= 0 ? level.getEntity(casterId) : null;
        }

        /** Some nos ultimos 10 ticks em vez de desaparecer de uma vez. */
        float fade(float partial) {
            return Mth.clamp((ticksLeft - partial) / 10f, 0, 1) * Mth.clamp((age + partial) / 4f, 0, 1);
        }

        /** Vinculum: 0 dentro de 70% do raio, 1 na borda. */
        float tension(Vec3 at) {
            double distance = Math.sqrt(at.distanceToSqr(pos));
            return (float) Mth.clamp((distance - extra * 0.7) / (extra * 0.3), 0, 1);
        }
    }
}
