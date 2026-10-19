package com.aurorion.magia.client;

import com.aurorion.magia.client.ClientSpellVisuals.Active;
import com.aurorion.magia.network.SpellVisualPayload.Kind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Os visuais das duas magias novas e das passivas dos representantes. Mesma tecnica do resto do modulo:
 * geometria aditiva do {@link SigilRenderer} e particulas geradas aqui, a partir de um visual de ~30
 * bytes que o servidor manda uma vez.
 */
final class RepresentativeVisuals {
    private static final ParticleOptions MOON = ClientSpellVisuals.dust(0.82f, 0.88f, 1.0f, 0.9f);
    private static final ParticleOptions QUANTUM = ClientSpellVisuals.dust(0.55f, 0.95f, 1.0f, 0.7f);
    private static final ParticleOptions ABYSS = ClientSpellVisuals.dust(0.4f, 0.08f, 0.7f, 1.0f);

    private RepresentativeVisuals() {
    }

    static boolean handles(Kind kind) {
        return switch (kind) {
            case FULGOR_BLAST, NETHER_GRASP, NULL_ZONE, QUANTUM_BLINK, LUNAR_AURA, VOID_RIFT, VOID_MARK -> true;
            default -> false;
        };
    }

    static void glow(PoseStack pose, VertexConsumer out, Vec3 camera, ClientLevel level, Active a, float partial) {
        float life = a.age + partial, fade = a.fade(partial);
        LivingEntity target = a.target(level);
        switch (a.kind) {
            case FULGOR_BLAST -> {
                if (a.age > 8) return;
                float grow = Mth.clamp(life / 4, 0, 1);
                SigilRenderer.at(pose, camera, a.pos);
                KitVisuals.sphere(out, pose.last().pose(), 0.5F + 2.5F * grow, 0xFFFFFF, .7F * (1 - life / 9), 10, 20);
                KitVisuals.sphere(out, pose.last().pose(), 0.3F + 1.2F * grow, 0xFFFDE8, .9F * (1 - life / 9), 8, 16);
                pose.popPose();
            }
            case NETHER_GRASP -> {
                if (target == null || !(a.caster(level) instanceof LivingEntity caster)) return;
                Vec3 from = ClientSpellVisuals.castingHand(caster, partial);
                Vec3 to = target.getPosition(partial).add(0, target.getBbHeight() * .55, 0);
                SigilRenderer.at(pose, camera, from);
                Vec3 delta = to.subtract(from);
                float throb = .8F + .2F * Mth.sin(life * .9F);
                KitVisuals.beam(out, pose.last().pose(), delta, .22F * throb, 0x3A0A66, .35F * fade);
                KitVisuals.beam(out, pose.last().pose(), delta, .09F, 0xB070FF, .85F * fade);
                pose.popPose();
                SigilRenderer.at(pose, camera, target.getPosition(partial).add(0, .03, 0));
                SigilRenderer.spin(pose, life, -3F);
                SigilRenderer.layered(out, pose, SigilGeometry.SEAL_THORN, target.getBbWidth() + .9F,
                        0x6A1FC2, 0xE3C8FF, fade);
                pose.popPose();
            }
            case LUNAR_AURA -> {
                if (target == null) return;
                SigilRenderer.at(pose, camera, target.getPosition(partial).add(0, .03, 0));
                SigilRenderer.spin(pose, life, .6F);
                SigilRenderer.layered(out, pose, SigilGeometry.SEAL_MAJOR, 1.6F, 0xAFC4FF, 0xFFFFFF, .55F * fade);
                SigilRenderer.drawMesh(out, pose, SigilGeometry.CIRCLE, Math.max(2, a.extra), .04F, 0xC9D6FF, .35F * fade, 0);
                pose.popPose();
            }
            case VOID_RIFT -> {
                SigilRenderer.at(pose, camera, a.pos.add(0, 1.0, 0));
                float pressure = Mth.clamp(a.extra, 0, 1);
                float wobble = .9F + .1F * Mth.sin(life * (.3F + pressure));
                // Luz aditiva: preto nao aparece aqui. A fenda e uma casca violeta e um nucleo claro.
                KitVisuals.sphere(out, pose.last().pose(), .3F * wobble + .2F * pressure, 0xD8C4FF, .5F * fade, 8, 16);
                KitVisuals.sphere(out, pose.last().pose(), .75F * wobble + .45F * pressure, 0x7A3CFF,
                        (.25F + .45F * pressure) * fade, 10, 20);
                pose.popPose();
            }
            case VOID_MARK -> {
                if (target == null) return;
                SigilRenderer.at(pose, camera, target.getPosition(partial).add(0, target.getBbHeight() + .35, 0));
                SigilRenderer.spin(pose, life, 4F);
                SigilRenderer.layered(out, pose, SigilGeometry.RUNE_RING, .45F, 0x7A3CFF, 0xE8DDFF, fade);
                pose.popPose();
            }
            case QUANTUM_BLINK -> {
                if (a.age > 10) return;
                SigilRenderer.at(pose, camera, a.pos.add(0, .9, 0));
                KitVisuals.sphere(out, pose.last().pose(), .4F + .5F * Mth.clamp(life / 6, 0, 1), 0x7FF2FF,
                        .45F * (1 - life / 11), 8, 16);
                pose.popPose();
            }
            default -> {
            }
        }
    }

    static void particles(ClientLevel level, Active a, @Nullable LivingEntity target, int stride, long time) {
        RandomSource random = level.random;
        switch (a.kind) {
            case FULGOR_BLAST -> {
                if (a.age != 1) return;
                level.addParticle(ParticleTypes.FLASH, a.pos.x, a.pos.y, a.pos.z, 0, 0, 0);
                ClientSpellVisuals.burst(level, a.pos, ParticleTypes.END_ROD, 30 / stride + 1, 0.35);
                ClientSpellVisuals.burst(level, a.pos, ParticleTypes.FIREWORK, 16 / stride + 1, 0.25);
            }
            case NETHER_GRASP -> {
                if (target == null || time % stride != 0 || !(a.caster(level) instanceof Entity caster)) return;
                Vec3 from = caster.position().add(0, caster.getBbHeight() * .7, 0);
                Vec3 to = ClientSpellVisuals.chest(target);
                for (int i = 0; i < 3; i++) {
                    Vec3 p = from.lerp(to, random.nextDouble());
                    level.addParticle(ABYSS, p.x, p.y, p.z, 0, 0, 0);
                }
                if (time % (4L * stride) == 0) level.addParticle(ParticleTypes.REVERSE_PORTAL, to.x, to.y, to.z, 0, 0.05, 0);
            }
            case QUANTUM_BLINK -> {
                if (a.age > 6 || time % stride != 0) return;
                // Os atomos: pontos que se desfazem (extra 0, a saida) ou se juntam (extra 1, a chegada).
                boolean arriving = a.extra > .5F;
                for (int i = 0; i < 10 / stride + 1; i++) {
                    Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * .8, random.nextGaussian()).normalize();
                    Vec3 center = a.pos.add(0, .9, 0);
                    Vec3 p = arriving ? center.add(dir.scale(1.4)) : center.add(dir.scale(.2));
                    Vec3 v = dir.scale(arriving ? -.12 : .12);
                    level.addParticle(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : QUANTUM, p.x, p.y, p.z, v.x, v.y, v.z);
                }
            }
            case LUNAR_AURA -> {
                if (target == null || time % (2L * stride) != 0) return;
                double angle = random.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(random.nextDouble()) * Math.max(2, a.extra);
                Vec3 c = target.position();
                level.addParticle(MOON, c.x + Math.cos(angle) * r, c.y + .2, c.z + Math.sin(angle) * r, 0, .03, 0);
                if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.END_ROD,
                        c.x + random.nextGaussian() * .4, c.y + target.getBbHeight() + .2, c.z + random.nextGaussian() * .4, 0, .01, 0);
            }
            case VOID_RIFT -> {
                if (time % (2L * stride) != 0) return;
                Vec3 c = a.pos.add(0, 1, 0);
                Vec3 p = c.add(ClientSpellVisuals.offset(random, 1.2));
                Vec3 v = c.subtract(p).scale(.08);
                level.addParticle(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, v.x, v.y, v.z);
                if (a.extra > .3F) level.addParticle(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 0, 0, 0);
            }
            case VOID_MARK -> {
                if (target == null || a.age > 20 || time % stride != 0) return;
                Vec3 c = ClientSpellVisuals.chest(target);
                level.addParticle(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 0, 0.04, 0);
            }
            default -> {
            }
        }
    }
}
