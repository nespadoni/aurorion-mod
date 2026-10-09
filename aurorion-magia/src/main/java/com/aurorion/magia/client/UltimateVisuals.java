package com.aurorion.magia.client;

import com.aurorion.magia.client.ClientSpellVisuals.Active;
import com.aurorion.magia.network.SpellVisualPayload.Kind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Camadas de energia, arcos eletricos e runas; nenhum raio real ou entidade de relampago. */
final class UltimateVisuals {
    private UltimateVisuals() { }

    static void spiritBall(VertexConsumer out, PoseStack pose, float radius, float life, float fade) {
        Matrix4f m = pose.last().pose();
        float pulse = 1 + .025F * Mth.sin(life * .35F);
        KitVisuals.sphere(out, m, radius * 1.08F * pulse, 0x337BFF, .12F * fade, 12, 24);
        KitVisuals.sphere(out, m, radius * pulse, 0x6ADFFF, .33F * fade, 12, 24);
        KitVisuals.sphere(out, m, radius * .76F, 0xD1F8FF, .58F * fade, 10, 20);
        KitVisuals.sphere(out, m, radius * .46F, 0xFFFFFF, .8F * fade, 8, 16);
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(35 + i * 55));
            pose.mulPose(Axis.XP.rotationDegrees(i * 38));
            SigilRenderer.spin(pose, life, i % 2 == 0 ? 1.2F : -1.7F);
            SigilRenderer.drawMesh(out, pose, SigilGeometry.RUNE_RING, radius * (1.12F + i * .07F),
                    .035F, i % 2 == 0 ? 0xE8FBFF : 0x45BFFF, .7F * fade, 0);
            pose.popPose();
        }
        // Os segmentos mudam a cada dois ticks, sem gerar centenas de particulas.
        for (int i = 0; i < 7; i++) {
            float angle = i * Mth.TWO_PI / 7 + life * .045F;
            Vec3 start = new Vec3(Mth.cos(angle), Mth.sin(angle * 1.7F) * .7, Mth.sin(angle)).normalize();
            Vec3 end = new Vec3(Mth.cos(angle + .9F), Mth.sin(angle * 1.7F + 1) * .7,
                    Mth.sin(angle + .9F)).normalize();
            lightning(out, m, start.scale(radius * 1.08), end.scale(radius * 1.08), radius * .075F,
                    life, i, 0xA4EDFF, .8F * fade);
        }
    }

    static void luxBeam(PoseStack pose, VertexConsumer out, Vec3 camera, Vec3 from, Active a, float partial) {
        float life = a.age + partial;
        float fade = a.fade(partial);
        float t = Mth.clamp(life / a.ttl, 0, 1);
        float width = a.extra * (2.6F - 1.7F * t);
        Vec3 delta = a.pos.subtract(from);
        SigilRenderer.at(pose, camera, from);
        Matrix4f m = pose.last().pose();
        KitVisuals.beam(out, m, delta, width * 1.6F, 0xFFBC4A, .14F * fade);
        KitVisuals.beam(out, m, delta, width, 0xFFE19C, .4F * fade);
        KitVisuals.beam(out, m, delta, width * .52F, 0xFFF9DC, .85F * fade);
        KitVisuals.beam(out, m, delta, width * .18F, 0xFFFFFF, fade);
        Vec3 axis = delta.normalize();
        Vec3 side = axis.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < .01) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(axis).normalize();
        for (int strand = 0; strand < 3; strand++) {
            Vec3 previous = Vec3.ZERO;
            for (int i = 1; i <= 24; i++) {
                float angle = i * .8F - life * .3F + strand * Mth.TWO_PI / 3;
                Vec3 point = delta.scale(i / 24.0).add(side.scale(Mth.cos(angle) * width))
                        .add(up.scale(Mth.sin(angle) * width));
                SigilGeometry.segment(out, m, previous, point, .035F, 0xFFD360, .85F * fade);
                previous = point;
            }
        }
        KitVisuals.sphere(out, m, width * 1.1F, 0xFFFFFF, .4F * fade, 8, 16);
        pose.popPose();
        SigilRenderer.at(pose, camera, a.pos);
        KitVisuals.sphere(out, pose.last().pose(), 1 + 2 * t, 0xFFF4AF, .45F * fade, 10, 20);
        for (int i = 0; i < 10; i++) {
            float angle = i * Mth.TWO_PI / 10 + life * .04F;
            Vec3 end = new Vec3(Mth.cos(angle) * 4, Mth.sin(angle * 2) * 2, Mth.sin(angle) * 4);
            lightning(out, pose.last().pose(), Vec3.ZERO, end, .25F, life, i, 0xFFDC80, fade);
        }
        pose.popPose();
    }

    static void glow(PoseStack pose, VertexConsumer out, Vec3 camera, ClientLevel level, Active a, float partial) {
        LivingEntity target = a.target(level);
        if (target == null) return;
        float life = a.age + partial, fade = a.fade(partial);
        switch (a.kind) {
            case LUX_CHARGE -> {
                float charge = Mth.clamp(life / Math.max(1, a.ttl), 0, 1);
                Vec3 hand = ClientSpellVisuals.castingHand(target, partial);
                SigilRenderer.at(pose, camera, hand);
                KitVisuals.sphere(out, pose.last().pose(), .2F + .7F * charge, 0xFFF6C8, .6F * fade, 8, 16);
                Vec3 direction = target.getLookAngle();
                KitVisuals.beam(out, pose.last().pose(), direction.scale(8 + charge * 8), .018F,
                        0xFFC764, .55F * fade);
                pose.mulPose(Axis.YP.rotationDegrees(-target.getYRot()));
                pose.mulPose(Axis.XP.rotationDegrees(target.getXRot() + 90));
                SigilRenderer.spin(pose, life, 4);
                SigilRenderer.layered(out, pose, SigilGeometry.SEAL_MAJOR, .5F + charge * .9F,
                        0xFFD36A, 0xFFFFFF, fade);
                pose.popPose();
            }
            case DOMAIN_SLASH -> {
                SigilRenderer.at(pose, camera, target.getPosition(partial).add(0, target.getBbHeight() * .55, 0));
                pose.mulPose(Axis.YP.rotationDegrees((target.getId() * 47) % 360));
                pose.mulPose(Axis.ZP.rotationDegrees(a.extra > .5F ? 35 : -35));
                float stretch = .5F + Mth.clamp(life / 4, 0, 1) * 2.5F;
                for (int i = -1; i <= 1; i++) {
                    Vec3 start = new Vec3(-stretch, i * .4 - .25, -.1);
                    Vec3 end = new Vec3(stretch, i * .4 + .25, .1);
                    SigilGeometry.segment(out, pose.last().pose(), start, end, .13F, 0xC00B24, .3F * fade);
                    SigilGeometry.segment(out, pose.last().pose(), start, end, .035F, 0xFFF0E5, fade);
                }
                pose.popPose();
            }
            case SOUL_REALM_AURA, SOUL_REAP -> {
                SigilRenderer.at(pose, camera, target.getPosition(partial).add(0, .04, 0));
                SigilRenderer.spin(pose, life, 1.7F);
                SigilRenderer.layered(out, pose, SigilGeometry.SEAL_DEATH, a.kind == Kind.SOUL_REAP ? 1.4F : 2.2F,
                        0x26BFA5, 0xCCFFF1, fade * .7F);
                SigilGeometry.wall(out, pose.last().pose(), 1.25F, 2.8F, 0x39DFBF, .15F * fade, 0, 32);
                pose.popPose();
            }
            default -> { }
        }
    }

    static void particles(ClientLevel level, Active a, LivingEntity target, int stride, long time) {
        if (target == null || time % stride != 0) return;
        switch (a.kind) {
            case LUX_CHARGE -> {
                Vec3 hand = ClientSpellVisuals.castingHand(target);
                Vec3 origin = hand.add(ClientSpellVisuals.offset(level.random, 2.5));
                Vec3 flow = hand.subtract(origin).scale(.15);
                level.addParticle(ParticleTypes.END_ROD, origin.x, origin.y, origin.z, flow.x, flow.y, flow.z);
            }
            case DOMAIN_SLASH -> {
                if (a.age > 3) return;
                Vec3 center = ClientSpellVisuals.chest(target);
                ClientSpellVisuals.burst(level, center, ClientSpellVisuals.CRIMSON, 6 / stride + 1, .16);
                level.addParticle(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 0, 0, 0);
            }
            case SOUL_REALM_AURA, SOUL_REAP -> {
                Vec3 center = target.position();
                double angle = time * .12, radius = 1.2;
                level.addParticle(ParticleTypes.SOUL, center.x + Math.cos(angle) * radius, center.y + .3,
                        center.z + Math.sin(angle) * radius, 0, .025, 0);
            }
            default -> { }
        }
    }

    static void lightning(VertexConsumer out, Matrix4f m, Vec3 start, Vec3 end, float jitter,
                          float life, int seed, int color, float alpha) {
        Vec3 previous = start;
        float time = (int) (life / 2);
        for (int i = 1; i <= 8; i++) {
            float wave = i == 8 ? 0 : jitter;
            Vec3 point = start.lerp(end, i / 8.0).add(Mth.sin(i * 17 + seed * 7 + time) * wave,
                    Mth.cos(i * 11 + seed * 3 + time) * wave, Mth.sin(i * 23 + seed + time) * wave);
            SigilGeometry.segment(out, m, previous, point, .055F, color, alpha * .22F);
            SigilGeometry.segment(out, m, previous, point, .015F, 0xF3FFFF, alpha);
            previous = point;
        }
    }

    /** Impulso curto, ponderado pela distancia; respeita as opcoes de tremor e distorcao no chamador. */
    static float impact(Vec3 camera) {
        float weight = 0;
        for (Active a : ClientSpellVisuals.active()) {
            if (a.ended || a.age > 20 || (a.kind != Kind.LUX_FINALIS && a.kind != Kind.SPHAERA_BLAST)) continue;
            double range = a.kind == Kind.SPHAERA_BLAST ? Math.max(24, a.extra * 2) : 24;
            double distance = camera.distanceTo(a.pos);
            float strength = (float) Math.max(0, 1 - distance / range) * (1 - a.age / 20F);
            weight = Math.max(weight, strength);
        }
        return weight;
    }
}
