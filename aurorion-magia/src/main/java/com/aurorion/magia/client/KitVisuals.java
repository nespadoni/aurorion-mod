package com.aurorion.magia.client;

import com.aurorion.magia.client.ClientSpellVisuals.Active;
import com.aurorion.magia.network.SpellVisualPayload.Kind;
import com.aurorion.magia.spell.MorsExProfundisSpell;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import static com.aurorion.magia.client.ClientSpellVisuals.burst;
import static com.aurorion.magia.client.ClientSpellVisuals.castingHand;
import static com.aurorion.magia.client.ClientSpellVisuals.chest;
import static com.aurorion.magia.client.ClientSpellVisuals.dust;
import static com.aurorion.magia.client.ClientSpellVisuals.neck;
import static com.aurorion.magia.client.ClientSpellVisuals.offset;
import static com.aurorion.magia.client.ClientSpellVisuals.ring;
import static com.aurorion.magia.client.ClientSpellVisuals.waist;

/**
 * Os visuais das magias de kit (Lux, Bardo, Blitz, Garen, Fiddlesticks, Gragas, Lulu, Pyke, Vladimir,
 * Zed, Ziggs, Sova, a Esfera e a Possessao), separados do {@link ClientSpellVisuals} so para nao
 * dobrar o tamanho dele. Mesmas regras: tudo no cliente, a partir do pacote de visual, com o orcamento
 * de particulas dividido pela opcao "Particulas" do vanilla ({@code stride}).
 *
 * <p>Tres entradas, chamadas pelos {@code default} dos switches de quem ja existia: as particulas (por
 * tick), a tinta e a luz (por quadro, do {@link SigilRenderer}).
 */
final class KitVisuals {
    static final ParticleOptions LIGHT = dust(1.0f, 0.97f, 0.75f, 1.6f);
    static final ParticleOptions GOLD = dust(1.0f, 0.8f, 0.3f, 1.1f);
    static final ParticleOptions SPIRIT = dust(0.55f, 0.85f, 1.0f, 1.3f);
    static final ParticleOptions CROW = dust(0.02f, 0.0f, 0.03f, 2.2f);
    static final ParticleOptions PLAGUE = dust(0.5f, 0.0f, 0.08f, 1.4f);
    static final ParticleOptions TEAL = dust(0.2f, 0.85f, 0.75f, 1.2f);
    static final ParticleOptions STEEL = dust(0.7f, 0.72f, 0.78f, 0.8f);

    private static final int GOLD_RGB = 0xFFCC4D;
    private static final int LIGHT_RGB = 0xFFF6C8;
    private static final int SPIRIT_RGB = 0x8CD8FF;
    private static final int SPARK_RGB = 0x7FD0FF;
    private static final int BLOOD_RGB = 0xB0101E;
    private static final int SHADOW_RGB = 0x8A0A14;
    private static final int FIRE_RGB = 0xFF6A1A;
    private static final int TEAL_RGB = 0x33D9BF;
    private static final int VOID_RGB = 0x5A1A7A;

    private KitVisuals() {
    }

    // --- Quem nao e desenhado -----------------------------------------------------------------

    /** Virou guaxinim (Capricho): o corpo nao e desenhado, e o {@code PolymorphRender} poe o bicho no lugar. */
    static boolean polymorphed(Entity entity) {
        return ClientSpellVisuals.targets(Kind.MUTATIO_FERAE, entity.getId());
    }

    /**
     * Corpos escondidos alem da invisibilidade do vanilla (que ainda desenha armadura e item na mao):
     * quem possui alguem, e quem esta dentro da Poca de Sangue. Para quem possui, o corpo possuido
     * tambem some — a camera dele esta dentro da cabeca do outro.
     */
    static boolean hidden(Entity entity) {
        // Roda para cada vivo desenhado, a cada quadro: sem visual ativo, sai na primeira linha.
        if (ClientSpellVisuals.active().isEmpty()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        int self = minecraft.player == null ? -1 : minecraft.player.getId();
        for (Active a : ClientSpellVisuals.active()) {
            if (a.ended) continue;
            if (a.kind == Kind.POSSESSIO_CORPORIS) {
                if (a.casterId == entity.getId()) return true;
                if (a.casterId == self && a.targetId == entity.getId() && minecraft.options.getCameraType().isFirstPerson()) {
                    return true;
                }
            } else if (a.kind == Kind.LACUS_SANGUINIS && a.targetId == entity.getId()) {
                return true;
            }
        }
        return false;
    }

    // --- Particulas ---------------------------------------------------------------------------

    static void particles(ClientLevel level, Active a, @Nullable LivingEntity target, int stride, long time) {
        RandomSource random = level.random;
        switch (a.kind) {
            case LUX_FINALIS, FUROR_VENATORIS -> {
                if (a.age > 4 || !(a.caster(level) instanceof LivingEntity caster)) return;
                boolean lux = a.kind == Kind.LUX_FINALIS;
                Vec3 from = castingHand(caster);
                int points = (int) Math.min(64, from.distanceTo(a.pos) * 1.2) / stride;
                for (int i = 0; i < points; i++) {
                    Vec3 p = from.lerp(a.pos, random.nextDouble()).add(offset(random, a.extra));
                    level.addParticle(lux ? LIGHT : SPIRIT, p.x, p.y, p.z, 0, 0.01, 0);
                    if (i % 4 == 0) level.addParticle(lux ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                            p.x, p.y, p.z, 0, 0, 0);
                }
            }
            case LUX_DETONATE -> {
                if (target == null || a.age != 1) return;
                Vec3 at = chest(target);
                burst(level, at, LIGHT, 18 / stride + 1, 0.2);
                burst(level, at, ParticleTypes.END_ROD, 8 / stride + 1, 0.12);
                level.addParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0, 0, 0);
            }
            case TEMPERIES_FATI -> {
                if (target == null || time % (2L * stride) != 0) return;
                Vec3 at = waist(target);
                Vec3 p = at.add(new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                        .normalize().scale(dome(target)));
                level.addParticle(GOLD, p.x, p.y, p.z, 0, 0, 0);
                if (a.age == 1) burst(level, at, ParticleTypes.END_ROD, 12 / stride + 1, 0.15);
            }
            case CAMPUS_STATICUS, SAGITTA_PULSE -> {
                if (a.age > 10 || time % stride != 0) return;
                double r = a.extra * a.age / 10.0;
                int count = Math.max(8, (int) (r * 4) / stride);
                ring(level, a.pos.add(0, a.kind == Kind.SAGITTA_PULSE ? 0.3 : 1.0, 0), r, count,
                        a.kind == Kind.SAGITTA_PULSE ? SPIRIT : ParticleTypes.ELECTRIC_SPARK, 0.02);
            }
            case STATIC_MARK -> {
                if (target == null || time % stride != 0) return;
                Vec3 head = neck(target).add(0, 0.5, 0);
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, head.x + random.nextGaussian() * 0.3, head.y,
                        head.z + random.nextGaussian() * 0.3, 0, 0.05, 0);
                if (a.ttl <= 8 && a.age == 1) burst(level, chest(target), ParticleTypes.ELECTRIC_SPARK, 16 / stride + 1, 0.3);
            }
            case MANUS_RAPAX -> {
                if (target == null || !(a.caster(level) instanceof LivingEntity caster) || time % stride != 0) return;
                Vec3 from = castingHand(caster), to = waist(target);
                for (int i = 0; i < 4; i++) {
                    Vec3 p = from.lerp(to, random.nextDouble());
                    level.addParticle(i % 2 == 0 ? STEEL : ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0, 0, 0);
                }
            }
            case IUSTITIA_DEMACIAE -> {
                if (target == null) return;
                if (a.age == SWORD_FALL_TICKS) {
                    Vec3 feet = target.position();
                    BlockState under = level.getBlockState(BlockPos.containing(feet.x, feet.y - 0.5, feet.z));
                    if (!under.isAir()) {
                        ring(level, feet.add(0, 0.1, 0), 0.4, 28 / stride, new BlockParticleOption(ParticleTypes.BLOCK, under), 0.45);
                    }
                    burst(level, chest(target), GOLD, 30 / stride + 1, 0.3);
                    level.addParticle(ParticleTypes.FLASH, feet.x, feet.y + 1, feet.z, 0, 0, 0);
                } else if (a.age > SWORD_FALL_TICKS && time % (3L * stride) == 0) {
                    Vec3 p = target.position().add(offset(random, 1.0).multiply(1, 0, 1));
                    level.addParticle(ParticleTypes.END_ROD, p.x, p.y + 0.2, p.z, 0, 0.05, 0);
                }
            }
            case PROCELLA_CORVORUM -> {
                if (target == null || time % stride != 0) return;
                // Corvos: borroes pretos girando em volta, em alturas diferentes, deixando rastro.
                int crows = 8 / stride + 1;
                double radius = Math.max(2, a.extra * 0.75);
                for (int i = 0; i < crows; i++) {
                    double angle = time * 0.25 + i * Math.PI * 2 / crows;
                    double r = radius * (0.7 + 0.3 * Math.sin(time * 0.1 + i));
                    double y = target.getY() + 0.8 + 1.4 * Math.abs(Math.sin(time * 0.07 + i * 1.3));
                    level.addParticle(CROW, target.getX() + Math.cos(angle) * r, y, target.getZ() + Math.sin(angle) * r, 0, 0, 0);
                }
                if (random.nextInt(4) == 0) {
                    Vec3 p = target.position().add(offset(random, radius * 2).multiply(1, 0, 1));
                    level.addParticle(ParticleTypes.SMOKE, p.x, p.y + 1, p.z, 0, 0.02, 0);
                }
            }
            case MESSIS_UBERRIMA, TRANSFUSIO -> {
                if (target == null || !(a.caster(level) instanceof LivingEntity caster) || time % stride != 0) return;
                boolean blood = a.kind == Kind.TRANSFUSIO;
                Vec3 from = chest(target), to = chest(caster);
                Vec3 flow = to.subtract(from).scale(0.08);
                for (int i = 0; i < (blood && a.extra > 0 ? 4 : 2); i++) {
                    Vec3 p = from.lerp(to, random.nextDouble() * 0.3);
                    level.addParticle(blood ? ClientSpellVisuals.BLOOD : ParticleTypes.SOUL, p.x, p.y, p.z, flow.x, flow.y, flow.z);
                }
            }
            case SPHAERA_CHARGE -> {
                if (target == null || time % stride != 0) return;
                // A energia vindo de longe e entrando na esfera.
                Vec3 center = sphereCenter(target, a.extra, 1);
                for (int i = 0; i < 3; i++) {
                    Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian()).normalize();
                    Vec3 p = center.add(dir.scale(4 + random.nextDouble() * 4));
                    Vec3 v = center.subtract(p).scale(0.09);
                    level.addParticle(i == 0 ? ParticleTypes.ELECTRIC_SPARK : SPIRIT, p.x, p.y, p.z, v.x, v.y, v.z);
                }
            }
            case SPHAERA_BLAST, PYROBOLUS_BLAST, DOLIUM_BLAST -> {
                if (a.age == 1) {
                    level.addParticle(a.kind == Kind.DOLIUM_BLAST ? ParticleTypes.EXPLOSION : ParticleTypes.EXPLOSION_EMITTER,
                            a.pos.x, a.pos.y + 0.5, a.pos.z, 0, 0, 0);
                    ParticleOptions main = a.kind == Kind.SPHAERA_BLAST ? SPIRIT : ParticleTypes.FLAME;
                    burst(level, a.pos.add(0, 0.8, 0), main, (int) (a.extra * 8) / stride + 1, 0.35);
                    if (a.kind == Kind.DOLIUM_BLAST) {
                        burst(level, a.pos.add(0, 0.8, 0), new BlockParticleOption(ParticleTypes.BLOCK,
                                Blocks.BARREL.defaultBlockState()), 20 / stride + 1, 0.3);
                    }
                } else if (a.age < 20 && time % (2L * stride) == 0) {
                    Vec3 p = a.pos.add(offset(random, a.extra * 1.5).multiply(1, 0.3, 1));
                    level.addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.5, p.z, 0, 0.05, 0);
                }
            }
            case MUTATIO_FERAE -> {
                if (target == null) return;
                if (a.age == 1 || a.ticksLeft == 1) {
                    burst(level, waist(target), ParticleTypes.POOF, 16 / stride + 1, 0.1);
                    burst(level, waist(target), ParticleTypes.HAPPY_VILLAGER, 6 / stride + 1, 0.2);
                }
            }
            case MORS_EX_PROFUNDIS -> {
                float yaw = (a.extra - (int) a.extra) * 360f;
                double radius = (int) a.extra;
                if (a.age < MorsExProfundisSpell.DELAY) {
                    if (time % (2L * stride) == 0) xLine(level, random, a.pos, yaw, radius, ParticleTypes.BUBBLE_POP, 4);
                } else if (a.age == MorsExProfundisSpell.DELAY) {
                    xLine(level, random, a.pos, yaw, radius, ParticleTypes.SPLASH, 40 / stride);
                    xLine(level, random, a.pos, yaw, radius, TEAL, 24 / stride);
                }
            }
            case AESTUS_CHARGE, LACUS_SANGUINIS -> {
                if (target == null || time % stride != 0) return;
                double radius = a.kind == Kind.LACUS_SANGUINIS ? a.extra : 1.0 + a.extra;
                double angle = time * 0.5;
                for (int i = 0; i < 2; i++) {
                    double t = angle + i * Math.PI;
                    level.addParticle(ClientSpellVisuals.BLOOD, target.getX() + Math.cos(t) * radius, target.getY() + 0.2,
                            target.getZ() + Math.sin(t) * radius, 0, 0.04, 0);
                }
            }
            case AESTUS_BURST -> {
                if (a.age > 8) return;
                ring(level, a.pos.add(0, 0.6, 0), a.extra * a.age / 8.0, Math.max(8, (int) (a.extra * 4) / stride),
                        ClientSpellVisuals.BLOOD, 0.05);
            }
            case PESTIS_AREA -> {
                if (time % (2L * stride) != 0) return;
                double angle = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * a.extra;
                level.addParticle(PLAGUE, a.pos.x + Math.cos(angle) * r, a.pos.y + 0.2, a.pos.z + Math.sin(angle) * r, 0, 0.06, 0);
                level.addParticle(ParticleTypes.CRIMSON_SPORE, a.pos.x + Math.cos(angle) * r, a.pos.y + 0.5,
                        a.pos.z + Math.sin(angle) * r, 0, 0.02, 0);
            }
            case PESTIS_MARK -> {
                if (target == null || time % (4L * stride) != 0) return;
                Vec3 p = neck(target).add(offset(random, target.getBbWidth()));
                level.addParticle(ParticleTypes.DRIPPING_DRIPSTONE_LAVA, p.x, p.y, p.z, 0, 0, 0);
                level.addParticle(PLAGUE, p.x, p.y, p.z, 0, -0.02, 0);
            }
            case UMBRA_SWAP -> {
                if (a.age != 1) return;
                burst(level, a.pos.add(0, 1, 0), ClientSpellVisuals.SHADOW, 20 / stride + 1, 0.15);
                burst(level, a.pos.add(0, 1, 0), ParticleTypes.REVERSE_PORTAL, 12 / stride + 1, 0.1);
            }
            case SECTIO_UMBRAE -> {
                if (a.age != 1) return;
                ring(level, a.pos.add(0, 1, 0), a.extra * 0.8, 10 / stride + 2, ParticleTypes.SWEEP_ATTACK, 0);
                ring(level, a.pos.add(0, 0.9, 0), a.extra, 16 / stride + 2, ClientSpellVisuals.CRIMSON, 0.05);
            }
            case SIGNUM_MORTIS -> {
                if (target == null || time % (3L * stride) != 0) return;
                Vec3 p = target.position().add(offset(random, target.getBbWidth() + 0.4)).add(0, target.getBbHeight() * 0.5, 0);
                level.addParticle(ClientSpellVisuals.SHADOW, p.x, p.y, p.z, 0, 0.01, 0);
            }
            case SIGNUM_POP -> {
                if (target == null || a.age != 1) return;
                burst(level, chest(target), ClientSpellVisuals.CRIMSON, 24 / stride + 1, 0.25);
                ring(level, chest(target), 0.6, 6 / stride + 2, ParticleTypes.SWEEP_ATTACK, 0.1);
            }
            case PYROBOLUS_TARGET -> {
                if (time % (3L * stride) != 0) return;
                double angle = random.nextDouble() * Math.PI * 2;
                level.addParticle(ParticleTypes.FLAME, a.pos.x + Math.cos(angle) * a.extra, a.pos.y + 0.1,
                        a.pos.z + Math.sin(angle) * a.extra, 0, 0.03, 0);
            }
            case SAGITTA_SHOCK -> {
                if (a.age != 1) return;
                burst(level, a.pos.add(0, 0.5, 0), ParticleTypes.ELECTRIC_SPARK, 30 / stride + 1, 0.35);
                level.addParticle(ParticleTypes.FLASH, a.pos.x, a.pos.y + 0.5, a.pos.z, 0, 0, 0);
            }
            case POSSESSIO_CORPORIS -> {
                if (target == null || time % (3L * stride) != 0) return;
                Vec3 p = target.position().add(offset(random, target.getBbWidth() + 0.3)).add(0, target.getBbHeight() * 0.6, 0);
                level.addParticle(random.nextBoolean() ? ParticleTypes.SOUL : ClientSpellVisuals.VOID, p.x, p.y, p.z, 0, 0.02, 0);
            }
            default -> {
            }
        }
    }

    /** Particulas ao longo dos dois bracos do X do Pyke. */
    private static void xLine(ClientLevel level, RandomSource random, Vec3 center, float yaw, double radius,
                              ParticleOptions particle, int count) {
        double angle = yaw * Mth.DEG_TO_RAD;
        for (int arm = 0; arm < 2; arm++) {
            double a = angle + (arm == 0 ? Math.PI / 4 : -Math.PI / 4);
            Vec3 axis = new Vec3(-Math.sin(a), 0, Math.cos(a));
            for (int i = 0; i < count / 2 + 1; i++) {
                Vec3 p = center.add(axis.scale((random.nextDouble() * 2 - 1) * radius));
                level.addParticle(particle, p.x, p.y + 0.15, p.z, 0, 0.12, 0);
            }
        }
    }

    // --- Tinta --------------------------------------------------------------------------------

    static void ink(PoseStack pose, VertexConsumer out, Vec3 camera, ClientLevel level, Active a, float partial) {
        float fade = a.fade(partial);
        switch (a.kind) {
            case INCUS_SHADOW -> {
                // A sombra da bigorna, crescendo e escurecendo conforme ela chega.
                float t = Mth.clamp((a.age + partial) / Math.max(1, a.ttl - 4), 0, 1);
                SigilRenderer.at(pose, camera, a.pos.add(0, .02, 0));
                SigilGeometry.solidDisc(out, pose.last().pose(), .25F + .45F * t, 0, 0x000000, (.25F + .55F * t) * fade, .1F * fade, 24);
                pose.popPose();
            }
            case PESTIS_AREA -> {
                float grow = Mth.clamp((a.age + partial) / 8f, 0, 1);
                SigilRenderer.at(pose, camera, a.pos.add(0, .02, 0));
                SigilGeometry.solidDisc(out, pose.last().pose(), a.extra * grow, 0, 0x2A0006, .45F * fade, .1F * fade, 40);
                pose.popPose();
            }
            default -> {
            }
        }
    }

    // --- Luz ----------------------------------------------------------------------------------

    /** Quantos ticks a espada do Garen leva para descer do ceu ate o alvo. */
    private static final int SWORD_FALL_TICKS = 6;

    static void glow(PoseStack pose, VertexConsumer out, Vec3 camera, ClientLevel level, Active a, float partial) {
        LivingEntity target = a.target(level);
        float fade = a.fade(partial);
        float life = a.age + partial;
        switch (a.kind) {
            case LUX_FINALIS, FUROR_VENATORIS -> {
                if (!(a.caster(level) instanceof LivingEntity caster)) return;
                Vec3 from = castingHand(caster, partial);
                // O feixe e grosso no disparo e afina ate sumir.
                float t = Mth.clamp(life / a.ttl, 0, 1);
                float width = a.extra * (1 - t) * (a.kind == Kind.LUX_FINALIS ? 1.2F : 0.6F);
                int color = a.kind == Kind.LUX_FINALIS ? LIGHT_RGB : SPIRIT_RGB;
                SigilRenderer.at(pose, camera, from);
                beam(out, pose.last().pose(), a.pos.subtract(from), width, color, .35F * fade);
                beam(out, pose.last().pose(), a.pos.subtract(from), width * .35F, 0xFFFFFF, .9F * fade);
                pose.popPose();
            }
            case MANUS_RAPAX, MESSIS_UBERRIMA, TRANSFUSIO -> {
                if (target == null || !(a.caster(level) instanceof LivingEntity caster)) return;
                Vec3 from = castingHand(caster, partial);
                Vec3 to = target.getPosition(partial).add(0, target.getBbHeight() * .55, 0);
                int color = switch (a.kind) {
                    case MANUS_RAPAX -> 0xFFD34A;
                    case MESSIS_UBERRIMA -> VOID_RGB;
                    default -> BLOOD_RGB;
                };
                SigilRenderer.at(pose, camera, from);
                beam(out, pose.last().pose(), to.subtract(from), a.kind == Kind.MANUS_RAPAX ? .08F : .05F, color, .7F * fade);
                pose.popPose();
            }
            case TEMPERIES_FATI -> {
                if (target == null) return;
                SigilRenderer.at(pose, camera, target.getPosition(partial).add(0, target.getBbHeight() * .5, 0));
                Matrix4f m = pose.last().pose();
                float radius = dome(target);
                sphere(out, m, radius, GOLD_RGB, .16F * fade, 10, 20);
                sphere(out, m, radius * .98F, 0xFFF0B0, .08F * fade, 10, 20);
                pose.popPose();
                SigilRenderer.at(pose, camera, target.getPosition(partial).add(0, .03, 0));
                SigilRenderer.spin(pose, life, 1.2F);
                SigilRenderer.layered(out, pose, SigilGeometry.SEAL_CLOCK, radius + .2F, GOLD_RGB, 0xFFF4CC, fade);
                pose.popPose();
            }
            case CAMPUS_STATICUS, SAGITTA_PULSE, SECTIO_UMBRAE, AESTUS_BURST, SPHAERA_BLAST, PYROBOLUS_BLAST,
                 DOLIUM_BLAST, SAGITTA_SHOCK, UMBRA_SWAP -> shockwave(pose, out, camera, a, life, fade);
            case STATIC_MARK, PESTIS_MARK -> {
                if (target == null) return;
                SigilRenderer.at(pose, camera, target.getPosition(partial).add(0, target.getBbHeight() + .25, 0));
                SigilRenderer.spin(pose, life, 4F);
                SigilRenderer.drawMesh(out, pose, SigilGeometry.RUNE_RING, target.getBbWidth() * .4F + .12F, .02F,
                        a.kind == Kind.STATIC_MARK ? SPARK_RGB : BLOOD_RGB, .9F * fade, 0);
                pose.popPose();
            }
            case IUSTITIA_DEMACIAE -> {
                if (target == null) return;
                // A espada desce em {SWORD_FALL_TICKS} ticks e fica cravada ate o visual apagar.
                float fall = Mth.clamp(life / SWORD_FALL_TICKS, 0, 1);
                double tip = target.getY() - .4 + (1 - fall * fall) * 14;
                SigilRenderer.at(pose, camera, new Vec3(target.getX(), tip, target.getZ()));
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-target.getYRot()));
                sword(out, pose.last().pose(), GOLD_RGB, fade);
                pose.popPose();
                if (fall >= 1) {
                    SigilRenderer.at(pose, camera, target.getPosition(partial).add(0, .03, 0));
                    SigilRenderer.layered(out, pose, SigilGeometry.SEAL_MAJOR, 1.6F, GOLD_RGB, 0xFFF4CC, fade);
                    pose.popPose();
                }
            }
            case PROCELLA_CORVORUM -> {
                if (target == null) return;
                SigilRenderer.at(pose, camera, target.getPosition(partial).add(0, .03, 0));
                SigilRenderer.spin(pose, -life, 2.5F);
                SigilRenderer.layered(out, pose, SigilGeometry.SEAL_THORN, a.extra, VOID_RGB, 0xB070D0, fade * .8F);
                pose.popPose();
            }
            case SPHAERA_CHARGE -> {
                if (target == null) return;
                SigilRenderer.at(pose, camera, sphereCenter(target, a.extra, partial));
                Matrix4f m = pose.last().pose();
                float breath = 1 + .04F * Mth.sin(life * .4F);
                sphere(out, m, a.extra * breath, SPIRIT_RGB, .28F * fade, 12, 24);
                sphere(out, m, a.extra * .7F * breath, 0xE8FBFF, .55F * fade, 10, 20);
                pose.popPose();
            }
            case INCUS_SHADOW -> {
                SigilRenderer.at(pose, camera, a.pos.add(0, .04, 0));
                SigilGeometry.band(out, pose.last().pose(), .7F, .9F, 0, 0xFF2A2A, (.4F + .4F * Mth.sin(life * .8F)) * fade, 32);
                pose.popPose();
            }
            case PYROBOLUS_TARGET -> {
                SigilRenderer.at(pose, camera, a.pos.add(0, .04, 0));
                SigilGeometry.band(out, pose.last().pose(), a.extra * .9F, a.extra, 0, FIRE_RGB, .7F * fade, 64);
                SigilGeometry.band(out, pose.last().pose(), 1.6F, 2F, 0, 0xFF2A10, (.5F + .4F * Mth.sin(life * .6F)) * fade, 32);
                SigilRenderer.spin(pose, life, 1.5F);
                SigilRenderer.drawMesh(out, pose, SigilGeometry.CIRCLE, a.extra * .6F, .05F, FIRE_RGB, .5F * fade, .002F);
                pose.popPose();
            }
            case MORS_EX_PROFUNDIS -> {
                float yaw = (a.extra - (int) a.extra) * 360f;
                float radius = (int) a.extra;
                boolean detonated = a.age >= MorsExProfundisSpell.DELAY;
                SigilRenderer.at(pose, camera, a.pos.add(0, .04, 0));
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
                float alpha = (detonated ? 1F : .35F + .3F * Mth.sin(life * .9F)) * fade;
                cross(out, pose.last().pose(), radius, .55F, TEAL_RGB, alpha);
                pose.popPose();
            }
            case PESTIS_AREA -> {
                SigilRenderer.at(pose, camera, a.pos.add(0, .04, 0));
                SigilRenderer.spin(pose, life, .8F);
                SigilRenderer.layered(out, pose, SigilGeometry.SEAL_THORN, a.extra, BLOOD_RGB, 0xFF5060, fade * .8F);
                pose.popPose();
            }
            case SIGNUM_MORTIS -> {
                if (target == null) return;
                SigilRenderer.at(pose, camera, target.getPosition(partial).add(0, .03, 0));
                SigilRenderer.spin(pose, -life, 3F);
                SigilRenderer.layered(out, pose, SigilGeometry.SEAL_DEATH, target.getBbWidth() + .9F, SHADOW_RGB, 0xFF3040,
                        fade * (.7F + .3F * Mth.sin(life * .5F)));
                pose.popPose();
            }
            case POSSESSIO_CORPORIS -> {
                if (target == null) return;
                SigilRenderer.at(pose, camera, target.getPosition(partial).add(0, target.getBbHeight() + .35, 0));
                SigilRenderer.spin(pose, life, 2F);
                SigilRenderer.drawMesh(out, pose, SigilGeometry.RUNE_RING, target.getBbWidth() * .5F + .15F, .02F, VOID_RGB, .9F * fade, 0);
                pose.popPose();
            }
            case LUX_DETONATE -> {
                if (target == null) return;
                float t = Mth.clamp(life / 10f, 0, 1);
                SigilRenderer.at(pose, camera, target.getPosition(partial).add(0, target.getBbHeight() * .55, 0));
                sphere(out, pose.last().pose(), .4F + 1.2F * t, LIGHT_RGB, .5F * (1 - t), 8, 16);
                pose.popPose();
            }
            default -> {
            }
        }
    }

    /** Anel de choque abrindo do centro ate o raio nos primeiros ticks, na cor de cada magia. */
    private static void shockwave(PoseStack pose, VertexConsumer out, Vec3 camera, Active a, float life, float fade) {
        int open = switch (a.kind) {
            case SPHAERA_BLAST, PYROBOLUS_BLAST -> 14;
            case SAGITTA_PULSE -> 10;
            default -> 7;
        };
        float t = Mth.clamp(life / open, 0, 1);
        float radius = Math.max(.8F, a.extra) * (1 - (1 - t) * (1 - t));
        int color = switch (a.kind) {
            case CAMPUS_STATICUS, SAGITTA_SHOCK -> SPARK_RGB;
            case SAGITTA_PULSE, SPHAERA_BLAST -> SPIRIT_RGB;
            case SECTIO_UMBRAE, UMBRA_SWAP -> SHADOW_RGB;
            case AESTUS_BURST -> BLOOD_RGB;
            default -> FIRE_RGB;
        };
        float alpha = (1 - t * .7F) * fade;
        SigilRenderer.at(pose, camera, a.pos.add(0, .05, 0));
        SigilGeometry.band(out, pose.last().pose(), radius * .75F, radius, 0, color, alpha, 64);
        if (a.kind == Kind.SPHAERA_BLAST && life < 20) {
            pose.translate(0, 1, 0);
            float flash = 1 - life / 20f;
            sphere(out, pose.last().pose(), a.extra * (.4F + .6F * t), 0xE8FBFF, .35F * flash * fade, 12, 24);
        }
        pose.popPose();
    }

    private static float dome(LivingEntity target) {
        return Math.max(target.getBbWidth(), target.getBbHeight()) * .62F + .35F;
    }

    private static Vec3 sphereCenter(LivingEntity caster, float radius, float partial) {
        return caster.getPosition(partial).add(0, caster.getBbHeight() + .5 + radius, 0);
    }

    // --- Geometria ----------------------------------------------------------------------------

    /** Esfera de quads, em volta da origem. */
    static void sphere(VertexConsumer out, Matrix4f m, float radius, int color, float alpha, int stacks, int slices) {
        if (radius <= .01F || alpha <= .004F) return;
        int c = SigilGeometry.argb(color, alpha);
        for (int i = 0; i < stacks; i++) {
            float t0 = Mth.PI * i / stacks, t1 = Mth.PI * (i + 1) / stacks;
            float y0 = Mth.cos(t0) * radius, y1 = Mth.cos(t1) * radius;
            float r0 = Mth.sin(t0) * radius, r1 = Mth.sin(t1) * radius;
            for (int j = 0; j < slices; j++) {
                float p0 = Mth.TWO_PI * j / slices, p1 = Mth.TWO_PI * (j + 1) / slices;
                float c0 = Mth.cos(p0), s0 = Mth.sin(p0), c1 = Mth.cos(p1), s1 = Mth.sin(p1);
                out.addVertex(m, r0 * c0, y0, r0 * s0).setColor(c);
                out.addVertex(m, r1 * c0, y1, r1 * s0).setColor(c);
                out.addVertex(m, r1 * c1, y1, r1 * s1).setColor(c);
                out.addVertex(m, r0 * c1, y0, r0 * s1).setColor(c);
            }
        }
    }

    /** Feixe da origem ate {@code delta}: dois quads cruzados ao longo do eixo, visivel de qualquer lado. */
    static void beam(VertexConsumer out, Matrix4f m, Vec3 delta, float width, int color, float alpha) {
        double length = delta.length();
        if (length < .05 || width <= .001F || alpha <= .004F) return;
        Vec3 axis = delta.scale(1 / length);
        Vec3 side = axis.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 normal = side.cross(axis).normalize();
        int c = SigilGeometry.argb(color, alpha);
        for (Vec3 offset : new Vec3[]{side.scale(width), normal.scale(width)}) {
            float ox = (float) offset.x, oy = (float) offset.y, oz = (float) offset.z;
            float dx = (float) delta.x, dy = (float) delta.y, dz = (float) delta.z;
            out.addVertex(m, ox, oy, oz).setColor(c);
            out.addVertex(m, dx + ox, dy + oy, dz + oz).setColor(c);
            out.addVertex(m, dx - ox, dy - oy, dz - oz).setColor(c);
            out.addVertex(m, -ox, -oy, -oz).setColor(c);
        }
    }

    /**
     * A espada do Garen, de ponta para baixo, com a ponta na origem: lamina, guarda, cabo e pomo, em
     * dois planos cruzados para ser vista de qualquer angulo.
     */
    private static void sword(VertexConsumer out, Matrix4f m, int color, float fade) {
        int blade = SigilGeometry.argb(color, .55F * fade);
        int edge = SigilGeometry.argb(0xFFFBE6, .8F * fade);
        int guard = SigilGeometry.argb(0xE0A030, .8F * fade);
        crossed(out, m, -.35F, .6F, .35F, 6.5F, blade);
        crossed(out, m, -.08F, .3F, .08F, 6.5F, edge);
        crossed(out, m, -.1F, 0F, .1F, .6F, blade);
        crossed(out, m, -1.3F, 6.5F, 1.3F, 6.85F, guard);
        crossed(out, m, -.12F, 6.85F, .12F, 8.2F, guard);
        crossed(out, m, -.25F, 8.2F, .25F, 8.55F, guard);
    }

    /** Retangulo nos planos XY e ZY ao mesmo tempo. */
    private static void crossed(VertexConsumer out, Matrix4f m, float x0, float y0, float x1, float y1, int c) {
        out.addVertex(m, x0, y0, 0).setColor(c);
        out.addVertex(m, x1, y0, 0).setColor(c);
        out.addVertex(m, x1, y1, 0).setColor(c);
        out.addVertex(m, x0, y1, 0).setColor(c);
        out.addVertex(m, 0, y0, x0).setColor(c);
        out.addVertex(m, 0, y0, x1).setColor(c);
        out.addVertex(m, 0, y1, x1).setColor(c);
        out.addVertex(m, 0, y1, x0).setColor(c);
    }

    /** O X do Pyke: duas barras no chao, a 45 graus da frente. */
    private static void cross(VertexConsumer out, Matrix4f m, float radius, float halfWidth, int color, float alpha) {
        int c = SigilGeometry.argb(color, alpha);
        for (int arm = 0; arm < 2; arm++) {
            float angle = arm == 0 ? Mth.PI / 4 : -Mth.PI / 4;
            float ax = -Mth.sin(angle), az = Mth.cos(angle);
            float nx = -az * halfWidth, nz = ax * halfWidth;
            float ex = ax * radius, ez = az * radius;
            out.addVertex(m, -ex + nx, 0, -ez + nz).setColor(c);
            out.addVertex(m, ex + nx, 0, ez + nz).setColor(c);
            out.addVertex(m, ex - nx, 0, ez - nz).setColor(c);
            out.addVertex(m, -ex - nx, 0, -ez - nz).setColor(c);
        }
    }
}
