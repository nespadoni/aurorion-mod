package com.aurorion.magia.client;

import com.aurorion.magia.entity.SpellZoneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Cenario de dominio desenhado no cliente, sem construir ou destruir o mapa do servidor. */
final class DomainVisuals {
    private static final Vec3[] STARS = stars();
    private DomainVisuals() { }

    static void ink(SpellZoneEntity zone, VertexConsumer out, PoseStack pose, float life, float fade) {
        if (zone.shape() == SpellZoneEntity.Shape.SOUL_ARENA) {
            SigilGeometry.wall(out, pose.last().pose(), 15.8F, 24, 0x031B19, .9F * fade, .4F * fade, 64);
        } else if (zone.shape() == SpellZoneEntity.Shape.INFINITE_VOID) {
            KitVisuals.sphere(out, pose.last().pose(), zone.radius(), 0x030311, .95F * fade, 16, 32);
            SigilGeometry.solidDisc(out, pose.last().pose(), zone.radius(), .04F, 0x040313,
                    .92F * fade, .8F * fade, 64);
            pose.pushPose();
            pose.translate(0, zone.radius() * .5, 0);
            pose.scale(zone.radius() / 24, zone.radius() / 24, zone.radius() / 24);
            KitVisuals.sphere(out, pose.last().pose(), 2.8F, 0x000000, fade, 12, 24);
            pose.popPose();
        } else {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-zone.getYRot()));
            shrine(out, pose.last().pose(), 0x16090C, fade * .9F, false);
            pose.popPose();
        }
    }

    static void glow(SpellZoneEntity zone, VertexConsumer out, PoseStack pose, float life, float fade) {
        if (zone.shape() == SpellZoneEntity.Shape.SOUL_ARENA) {
            SigilRenderer.layered(out, pose, SigilGeometry.SEAL_DEATH, 14, 0x088F79, 0x98FFE1, .6F * fade);
            for (int i = 0; i < 8; i++) {
                float angle = i * Mth.TWO_PI / 8;
                Vec3 base = new Vec3(Mth.cos(angle) * 15, .2, Mth.sin(angle) * 15);
                Vec3 peak = base.add(0, 11 + i % 3 * 3, 0);
                SigilGeometry.segment(out, pose.last().pose(), base, peak, .12F, 0x45FFC9, .8F * fade);
                SigilGeometry.segment(out, pose.last().pose(), peak, peak.scale(.8).add(0, 3, 0), .07F,
                        0xC4FFEF, .6F * fade);
            }
            pose.pushPose();
            pose.translate(0, 19, 0);
            SigilRenderer.spin(pose, life, -.5F);
            SigilRenderer.layered(out, pose, SigilGeometry.SEAL_CHAIN, 9, 0x19AD93, 0xBEFFE8, .7F * fade);
            pose.popPose();
            return;
        } else if (zone.shape() == SpellZoneEntity.Shape.INFINITE_VOID) cosmos(zone, out, pose, life, fade);
        else {
            Matrix4f m = pose.last().pose();
            // Somente o alcance marcado no chao: nenhuma parede impede a fuga do Santuario.
            SigilGeometry.band(out, m, zone.radius() - .4F, zone.radius(), .07F, 0xDD1739, .65F * fade, 128);
            SigilGeometry.band(out, m, zone.radius() * .48F, zone.radius() * .5F, .06F, 0x7D0A23, .12F * fade, 96);
            SigilRenderer.drawMesh(out, pose, SigilGeometry.SEAL_DEATH, 10, .14F, 0xF13A53, .7F * fade, .08F);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-zone.getYRot()));
            shrine(out, pose.last().pose(), 0xD2A996, fade * .7F, true);
            pose.popPose();
            // Cortes largos e intermitentes no ar, com quantidade fixa mesmo no raio maximo.
            for (int i = 0; i < 12; i++) {
                float angle = i * Mth.TWO_PI / 12 + (int) (life / 10) * .7F;
                float reach = Math.min(zone.radius(), 36);
                Vec3 from = new Vec3(Mth.cos(angle) * reach * .2, 1 + i % 4, Mth.sin(angle) * reach * .2);
                Vec3 to = new Vec3(Mth.cos(angle + .08F) * reach, 2 + i % 5, Mth.sin(angle + .08F) * reach);
                float flicker = Math.max(0, 1 - (life % 10) / 7);
                SigilGeometry.segment(out, m, from, to, .025F, 0xFFDEE1, fade * flicker * .8F);
            }
        }
    }

    private static void cosmos(SpellZoneEntity zone, VertexConsumer out, PoseStack pose, float life, float fade) {
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < STARS.length; i++) {
            Vec3 star = STARS[i];
            float radius = zone.radius() * .96F;
            float size = i % 9 == 0 ? .13F : .045F;
            float twinkle = .65F + .35F * Mth.sin(life * .05F + i * 2.3F);
            int color = i % 3 == 0 ? 0xAA9BFF : i % 3 == 1 ? 0x79C9FF : 0xFFFFFF;
            star(out, m, (float) star.x * radius, (float) star.y * radius, (float) star.z * radius,
                    size, SigilGeometry.argb(color, fade * twinkle));
        }
        // Buraco negro central: horizonte escuro no passe de tinta, disco de acrecao e lensing aqui.
        pose.pushPose();
        pose.translate(0, zone.radius() * .5, 0);
        pose.scale(zone.radius() / 24, zone.radius() / 24, zone.radius() / 24);
        pose.mulPose(Axis.ZP.rotationDegrees(24));
        SigilGeometry.band(out, pose.last().pose(), 2.85F, 3.15F, 0, 0xD6E7FF, .85F * fade, 96);
        SigilGeometry.band(out, pose.last().pose(), 3.3F, 7, 0, 0x5965DE, .28F * fade, 96);
        SigilRenderer.spin(pose, life, .2F);
        for (int arm = 0; arm < 4; arm++) {
            Vec3 previous = null;
            for (int i = 0; i < 48; i++) {
                float angle = arm * Mth.HALF_PI + i * .13F;
                float radius = 3.15F + i * .115F;
                Vec3 point = new Vec3(Mth.cos(angle) * radius, Mth.sin(i * .25F) * .09, Mth.sin(angle) * radius);
                if (previous != null) SigilGeometry.segment(out, pose.last().pose(), previous, point, .055F,
                        arm % 2 == 0 ? 0x87C3FF : 0xBE8DFF, .65F * fade);
                previous = point;
            }
        }
        pose.popPose();
        SigilRenderer.drawMesh(out, pose, SigilGeometry.RUNE_RING, zone.radius() * .94F, .045F,
                0x829CFF, .7F * fade, .06F);
        // Galaxias menores em tres planos inclinados, espalhadas acima da arena.
        for (int g = 0; g < 3; g++) {
            pose.pushPose();
            float angle = g * Mth.TWO_PI / 3;
            pose.translate(Mth.cos(angle) * zone.radius() * .6, (7 + g * 3) * zone.radius() / 24, Mth.sin(angle) * zone.radius() * .6);
            pose.scale(zone.radius() / 24, zone.radius() / 24, zone.radius() / 24);
            pose.mulPose(Axis.ZP.rotationDegrees(35 + g * 20));
            SigilRenderer.spin(pose, life, .15F);
            SigilGeometry.disc(out, pose.last().pose(), 2.5F, 0, 0x9E7ADF, .25F * fade, 32);
            SigilRenderer.drawMesh(out, pose, SigilGeometry.SPIRAL, 2.5F, .045F, 0xCDDFFF, .55F * fade, .01F);
            pose.popPose();
        }
    }

    /** Pagode espectral: degraus, pilares de osso, telhados e uma boca com dentes e chifres. */
    private static void shrine(VertexConsumer out, Matrix4f m, int color, float alpha, boolean bones) {
        if (!bones) {
            for (int i = 0; i < 3; i++) box(out, m, -6 + i, i * .6F, -4 + i * .5F,
                    6 - i, (i + 1) * .6F, 4 - i * .5F, color, alpha);
            box(out, m, -3.6F, 1.8F, -2.3F, 3.6F, 6.8F, 2.3F, color, alpha);
        }
        for (int x = -1; x <= 1; x += 2) for (int z = -1; z <= 1; z += 2) {
            Vec3 foot = new Vec3(x * 4, 1.8, z * 2.8), top = new Vec3(x * 4, 7, z * 2.8);
            SigilGeometry.segment(out, m, foot, top, bones ? .18F : .5F, color, alpha);
        }
        for (int tier = 0; tier < 3; tier++) {
            float width = 6 - tier * 1.3F, y = 6.8F + tier * 2.5F;
            for (int side = -1; side <= 1; side += 2) {
                Vec3 a = new Vec3(-width, y, side * width * .6), b = new Vec3(width, y, side * width * .6);
                Vec3 ridgeA = new Vec3(-width * .7, y + 1.8, 0), ridgeB = new Vec3(width * .7, y + 1.8, 0);
                if (!bones) quad(out, m, a, b, ridgeB, ridgeA, color, alpha);
                else {
                    SigilGeometry.segment(out, m, a, b, .08F, 0xA51A37, alpha);
                    SigilGeometry.segment(out, m, a, ridgeA, .07F, color, alpha);
                    SigilGeometry.segment(out, m, b, ridgeB, .07F, color, alpha);
                    SigilGeometry.segment(out, m, ridgeA, ridgeB, .1F, color, alpha);
                }
            }
        }
        if (!bones) return;
        // Boca cavernosa na fachada, dentes irregulares e duas orbitas vermelhas.
        for (int i = -4; i <= 4; i++) {
            double x = i * .6, length = 1 + Math.abs(i % 3) * .2;
            SigilGeometry.segment(out, m, new Vec3(x, 6.2, -2.45), new Vec3(x * .95, 6.2 - length, -2.7),
                    .14F, 0xF5DEB9, alpha);
            SigilGeometry.segment(out, m, new Vec3(x, 1.9, -2.5), new Vec3(x * .9, 2.7 + length * .25, -2.7),
                    .13F, 0xF5DEB9, alpha);
        }
        for (int side = -1; side <= 1; side += 2) {
            SigilGeometry.segment(out, m, new Vec3(side * 2.3, 6.8, -2.45), new Vec3(side * 1.2, 6.6, -2.45),
                    .18F, 0xFF163D, alpha);
            SigilGeometry.segment(out, m, new Vec3(side * 3, 7, -1), new Vec3(side * 4.4, 10.5, -1),
                    .17F, 0xE8D9BE, alpha);
        }
    }

    static void particles(SpellZoneEntity zone, int stride) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 center = zone.position();
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        boolean inside = eye.distanceToSqr(center) < zone.radius() * zone.radius();
        Vec3 at = inside ? eye : center.add(0, 3, 0);
        for (int i = 0; i < Math.max(1, 5 / stride); i++) {
            Vec3 p = at.add(ClientSpellVisuals.offset(mc.level.random, inside ? 7 : 10));
            mc.level.addParticle(zone.shape() == SpellZoneEntity.Shape.INFINITE_VOID ? ParticleTypes.ENCHANT
                    : zone.shape() == SpellZoneEntity.Shape.SOUL_ARENA ? ParticleTypes.SOUL
                    : ClientSpellVisuals.CRIMSON, p.x, p.y, p.z, 0, .015, 0);
        }
        if (zone.shape() == SpellZoneEntity.Shape.MALEVOLENT_SHRINE && zone.tickCount % (4 * stride) == 0) {
            double angle = mc.level.random.nextDouble() * Math.PI * 2;
            mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, center.x + Math.cos(angle) * 5,
                    center.y + 2, center.z + Math.sin(angle) * 5, 0, .04, 0);
        }
    }

    private static Vec3[] stars() {
        Vec3[] stars = new Vec3[160];
        for (int i = 0; i < stars.length; i++) {
            double y = 1 - (i + .5) * 2 / stars.length, r = Math.sqrt(1 - y * y), a = i * 2.399963;
            stars[i] = new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
        }
        return stars;
    }

    /** Tres pequenos quads cruzados: estrela visivel de qualquer lado, sem alocar vetores por frame. */
    private static void star(VertexConsumer out, Matrix4f m, float x, float y, float z, float size, int color) {
        float thin = size * .18F;
        out.addVertex(m,x-size,y-thin,z).setColor(color); out.addVertex(m,x+size,y-thin,z).setColor(color);
        out.addVertex(m,x+size,y+thin,z).setColor(color); out.addVertex(m,x-size,y+thin,z).setColor(color);
        out.addVertex(m,x-thin,y-size,z).setColor(color); out.addVertex(m,x+thin,y-size,z).setColor(color);
        out.addVertex(m,x+thin,y+size,z).setColor(color); out.addVertex(m,x-thin,y+size,z).setColor(color);
        out.addVertex(m,x,y-thin,z-size).setColor(color); out.addVertex(m,x,y-thin,z+size).setColor(color);
        out.addVertex(m,x,y+thin,z+size).setColor(color); out.addVertex(m,x,y+thin,z-size).setColor(color);
    }

    private static void quad(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, float alpha) {
        int tint = SigilGeometry.argb(color, alpha);
        for (Vec3 p : new Vec3[]{a, b, c, d}) out.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(tint);
    }

    private static void box(VertexConsumer out, Matrix4f m, float x1, float y1, float z1,
                            float x2, float y2, float z2, int color, float alpha) {
        Vec3 a = new Vec3(x1,y1,z1), b = new Vec3(x2,y1,z1), c = new Vec3(x2,y2,z1), d = new Vec3(x1,y2,z1);
        Vec3 e = new Vec3(x1,y1,z2), f = new Vec3(x2,y1,z2), g = new Vec3(x2,y2,z2), h = new Vec3(x1,y2,z2);
        quad(out,m,a,b,c,d,color,alpha); quad(out,m,f,e,h,g,color,alpha);
        quad(out,m,e,a,d,h,color,alpha); quad(out,m,b,f,g,c,color,alpha);
        quad(out,m,d,c,g,h,color,alpha); quad(out,m,e,f,b,a,color,alpha);
    }
}
