package com.aurorion.magia.client;

import com.aurorion.magia.client.ClientSpellVisuals.Active;
import com.aurorion.magia.network.SpellVisualPayload.Kind;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

import static com.aurorion.magia.client.SigilGeometry.*;

/**
 * Selos, aneis e correntes das magias, desenhados no mundo.
 *
 * <p>A mesma tecnica da Cerimonia de Vinculacao do {@code aurorion-ethereal}: geometria luminosa em
 * lote, sem entidade, sem textura, em dois passes —
 *
 * <ul>
 *   <li><b>tinta</b> ({@code INK}): translucida, cobre o que esta atras. Serve para o escuro: a
 *       mancha do Lux Vorata, o colar do Vox Interdicta, o nucleo das correntes;</li>
 *   <li><b>luz</b> ({@code GLOW}): aditiva, soma no que ja esta na tela. Os selos e os aneis.</li>
 * </ul>
 *
 * <p>Custo: um batch por passe por quadro, e so quando ha visual ativo. Os selos sao listas fixas de
 * segmentos (ver {@link SigilGeometry}); desenhar um selo e emitir algumas centenas de quads.
 */
public final class SigilRenderer {
    private static final RenderType INK = Shard.ink();
    private static final RenderType GLOW = Shard.glow();
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final double MAX_DISTANCE_SQR = 64 * 64;

    private SigilRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        List<Active> actives = ClientSpellVisuals.active();
        if (actives.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        VertexConsumer ink = buffers.getBuffer(INK);
        for (Active active : actives) {
            if (visible(active, level, camera, partial)) ink(pose, ink, camera, level, active, partial);
        }
        buffers.endBatch(INK);

        VertexConsumer glow = buffers.getBuffer(GLOW);
        for (Active active : actives) {
            if (visible(active, level, camera, partial)) glow(pose, glow, camera, level, active, partial);
        }
        buffers.endBatch(GLOW);
    }

    private static boolean visible(Active active, ClientLevel level, Vec3 camera, float partial) {
        LivingEntity target = active.target(level);
        Vec3 at = target != null ? target.getPosition(partial) : active.pos;
        return at.distanceToSqr(camera) < MAX_DISTANCE_SQR;
    }

    // --- Tinta -------------------------------------------------------------------------------

    private static void ink(PoseStack pose, VertexConsumer out, Vec3 camera, ClientLevel level, Active a, float partial) {
        LivingEntity target = a.target(level);
        float fade = a.fade(partial);
        switch (a.kind) {
            case CRUCIATUS_BEAM -> {
                if (target == null) return;
                at(pose, camera, target.getPosition(partial).add(0, .02, 0));
                solidDisc(out, pose.last().pose(), target.getBbWidth() + .6F, 0, 0x1A0005, .35F * fade, 0, 32);
                pose.popPose();
            }
            case VINCULUM -> {
                if (target == null) return;
                float tension = Math.max(a.tension(target.getPosition(partial)), a.flash);
                if (tension <= .01F) return;
                at(pose, camera, a.pos);
                chain(out, pose.last().pose(), new Vec3(0, .2, 0), waist(target, partial).subtract(a.pos),
                        .32F, .035F, 0x2A0508, .75F * tension);
                pose.popPose();
            }
            case VOX_INTERDICTA -> {
                if (target == null) return;
                at(pose, camera, neck(target, partial));
                float radius = target.getBbWidth() * .45F + .06F;
                band(out, pose.last().pose(), radius - .07F, radius, 0, 0x14061C, .85F * fade, 32);
                band(out, pose.last().pose(), radius - .07F, radius, -.05F, 0x14061C, .6F * fade, 32);
                pose.popPose();
            }
            case LUX_VORATA -> {
                at(pose, camera, a.pos.add(0, .03, 0));
                float grow = Mth.clamp((a.age + partial) / 12f, 0, 1);
                solidDisc(out, pose.last().pose(), a.extra * grow, 0, 0x020005, .6F * fade, .15F * fade, 48);
                pose.popPose();
            }
            case FERRUM_LIGATUM -> {
                if (target == null) return;
                for (int band = 0; band < 3; band++) {
                    at(pose, camera, target.getPosition(partial).add(0, target.getBbHeight() * (.3 + band * .25), 0));
                    spin(pose, (band & 1) == 0 ? a.age + partial : -(a.age + partial), 2.5F);
                    drawMesh(out, pose, SEAL_CHAIN, target.getBbWidth() * .6F + .12F, .05F, 0x1C1C22, .55F * fade, 0);
                    pose.popPose();
                }
            }
            case DOLOR_UNIVERSUS -> {
                if (target == null) return;
                at(pose, camera, target.getPosition(partial).add(0, .02, 0));
                solidDisc(out, pose.last().pose(), a.extra * .95F, 0, 0x1A0005, .3F * fade, .05F * fade, 64);
                pose.popPose();
            }
            case MORTEM_AREA -> {
                at(pose, camera, a.pos.add(0, .02, 0));
                solidDisc(out, pose.last().pose(), a.extra * .98F, 0, 0x02100A, .55F * fade, .1F * fade, 64);
                pose.popPose();
            }
            case MUNDUS_VACUUS -> {
                if (target == null) return;
                // O veu: uma casca escura rente ao corpo, que engole o que esta atras dele.
                float radius = target.getBbWidth() * .8F + .3F;
                for (int band = 0; band < 3; band++) {
                    at(pose, camera, target.getPosition(partial).add(0, target.getBbHeight() * (.2 + band * .3), 0));
                    band(out, pose.last().pose(), radius * .55F, radius, 0, 0x05030C, .5F * fade, 32);
                    pose.popPose();
                }
            }
            default -> {
            }
        }
    }

    // --- Luz ---------------------------------------------------------------------------------

    private static void glow(PoseStack pose, VertexConsumer out, Vec3 camera, ClientLevel level, Active a, float partial) {
        LivingEntity target = a.target(level);
        float fade = a.fade(partial);
        float life = a.age + partial;
        switch (a.kind) {
            case CRUCIATUS_BEAM -> {
                if (target == null) return;
                at(pose, camera, target.getPosition(partial).add(0, .03, 0));
                spin(pose, life, 1.5F);
                layered(out, pose, SEAL_THORN, target.getBbWidth() + .7F, 0xB0102A, 0xFF4050, fade * pulse(life, .9F));
                pose.popPose();
            }
            case IMPERIUM_AURA -> {
                if (target == null) return;
                at(pose, camera, target.getPosition(partial).add(0, target.getBbHeight() + .28, 0));
                spin(pose, life, 2F);
                drawMesh(out, pose, RUNE_RING, target.getBbWidth() * .45F + .15F, .018F, 0x8FF5C8, .9F * fade, 0);
                drawMesh(out, pose, RUNE_RING, target.getBbWidth() * .45F + .15F, .05F, 0x8FF5C8, .18F * fade, 0);
                pose.popPose();
                if (life < 20) {
                    float t = life / 20f;
                    at(pose, camera, target.getPosition(partial).add(0, .03, 0));
                    layered(out, pose, SEAL_MAJOR, .4F + 1.1F * t, 0x8FF5C8, 0xE0EAF2, 1 - t);
                    pose.popPose();
                }
            }
            case VINCULUM -> {
                if (target == null) return;
                float tension = Math.max(a.tension(target.getPosition(partial)), a.flash);
                at(pose, camera, a.pos.add(0, .03, 0));
                spin(pose, life, .6F);
                layered(out, pose, SEAL_CHAIN, .85F, 0xA01830, 0xFF6A5A, fade * (.7F + .3F * tension));
                pose.popPose();
                at(pose, camera, a.pos.add(0, .05, 0));
                drawMesh(out, pose, CIRCLE, a.extra, .035F, 0xA01830, (.12F + .45F * tension) * fade, 0);
                if (tension > .01F) {
                    chain(out, pose.last().pose(), new Vec3(0, .15, 0), waist(target, partial).subtract(a.pos.add(0, .05, 0)),
                            .32F, .06F, 0xFF4A4A, .55F * tension * fade);
                }
                pose.popPose();
            }
            case TRANSPOSITIO -> {
                float grow = .6F + .4F * Mth.clamp(life / 6f, 0, 1);
                if (target != null) transpositio(pose, out, camera, target.getPosition(partial), grow, fade, life);
                if (a.caster(level) instanceof LivingEntity caster) {
                    transpositio(pose, out, camera, caster.getPosition(partial), grow, fade, life);
                }
            }
            case MANUS_GRIP -> {
                if (target == null) return;
                float radius = target.getBbWidth() * .8F + .35F;
                at(pose, camera, waist(target, partial));
                pose.pushPose();
                spin(pose, life, 4F);
                drawMesh(out, pose, RUNE_RING, radius, .02F, 0xB9A7FF, .85F * fade, 0);
                pose.popPose();
                pose.mulPose(ROTATION.rotationX(18 * Mth.DEG_TO_RAD));
                spin(pose, -life, 3F);
                drawMesh(out, pose, CIRCLE, radius * 1.15F, .015F, 0xFFFFFF, .5F * fade, 0);
                pose.popPose();
            }
            case MANUS_VACUA -> {
                float t = Mth.clamp(life / 20f, 0, 1);
                at(pose, camera, a.pos);
                if (a.caster(level) instanceof Entity caster) faceTowards(pose, caster.position().subtract(a.pos));
                layered(out, pose, SEAL_MAJOR, .25F + .6F * t, 0xE8ECF5, 0xFFFFFF, 1 - t);
                pose.popPose();
            }
            case GENUA_FLECTE -> {
                if (target == null) return;
                Vec3 feet = target.getPosition(partial);
                at(pose, camera, feet.add(0, .03, 0));
                spin(pose, life, .4F);
                layered(out, pose, SEAL_MAJOR, 1.3F * Mth.clamp(life / 6f, 0, 1), 0xB7A4D6, 0xF2E8FF, fade);
                pose.popPose();
                if (life < 16) {
                    at(pose, camera, feet);
                    for (int k = 0; k < 3; k++) {
                        float t = Mth.clamp((life - k * 3) / 10f, 0, 1);
                        if (t <= 0 || t >= 1) continue;
                        pose.pushPose();
                        pose.translate(0, 2.4F * (1 - t), 0);
                        drawMesh(out, pose, RUNE_RING, 1.4F - .9F * t, .03F, 0xF2E8FF, .8F * (1 - t), 0);
                        pose.popPose();
                    }
                    pose.popPose();
                }
            }
            case VOX_INTERDICTA -> {
                if (target == null) return;
                at(pose, camera, neck(target, partial).add(0, .01, 0));
                spin(pose, life, 3F);
                drawMesh(out, pose, RUNE_RING, target.getBbWidth() * .45F + .09F, .012F, 0x7A2E8C, .8F * fade, 0);
                pose.popPose();
            }
            case SIGILLUM, SIGILLUM_DENY, SIGILLUM_BREAK -> sigil(pose, out, camera, a, life, fade);
            case DEIECTIO -> {
                if (target == null) return;
                Vec3 base = target.getPosition(partial);
                if (!a.landed) {
                    float drop = Mth.clamp(life / 8f, 0, 1);
                    at(pose, camera, base.add(0, target.getBbHeight() + .2 + 1.4 * (1 - drop), 0));
                    spin(pose, life, 6F);
                    layered(out, pose, SEAL_THORN, .9F, 0x7A5CB0, 0xCFC2FF, fade);
                    pose.popPose();
                } else {
                    float t = Mth.clamp((life - a.landedAge) / 14f, 0, 1);
                    if (t >= 1) return;
                    float radius = .5F + 2.5F * t;
                    at(pose, camera, base.add(0, .05, 0));
                    band(out, pose.last().pose(), radius * .7F, radius, 0, 0xCFC2FF, .7F * (1 - t), 48);
                    pose.popPose();
                }
            }
            case LUX_VORATA -> {
                float closing = Mth.clamp(life / 20f, 0, 1);
                float radius = a.extra * (1.25F - .25F * closing);
                at(pose, camera, a.pos.add(0, .05, 0));
                drawMesh(out, pose, CIRCLE, radius, .06F, 0x6A2E9A, .5F * fade, 0);
                spin(pose, life, -.8F);
                drawMesh(out, pose, SEAL_THORN, a.extra * .35F, .03F, 0x4A1E6A, .45F * fade, 0);
                pose.popPose();
            }
            case FERRUM_LIGATUM -> {
                if (target == null) return;
                for (int band = 0; band < 3; band++) {
                    at(pose, camera, target.getPosition(partial).add(0, target.getBbHeight() * (.3 + band * .25), 0));
                    spin(pose, (band & 1) == 0 ? life : -life, 2.5F);
                    drawMesh(out, pose, SEAL_CHAIN, target.getBbWidth() * .6F + .12F, .02F, 0xA8AEB8, .75F * fade, 0);
                    pose.popPose();
                }
            }
            case TEMPUS_SISTERE -> {
                float radius = a.extra;
                // Relogio parado: de proposito sem girar — o tempo nao anda dentro do selo.
                at(pose, camera, a.pos.add(0, .04, 0));
                layered(out, pose, SEAL_CLOCK, radius * Mth.clamp(life / 10f, .2F, 1), 0xBFE8FF, 0xFFFFFF, fade);
                disc(out, pose.last().pose(), radius, .01F, 0xBFE8FF, .07F * fade, 48);
                if (life < 14) {
                    float t = life / 14f;
                    band(out, pose.last().pose(), radius * t * .85F, radius * t, .05F, 0xFFFFFF, .7F * (1 - t), 64);
                }
                pose.popPose();
            }
            case MORTEM_DICO -> {
                float t = Mth.clamp(life / 40f, 0, 1);
                at(pose, camera, a.pos.add(0, .03, 0));
                spin(pose, life, -.7F);
                layered(out, pose, SEAL_DEATH, 1.3F * (.5F + .5F * Mth.clamp(life / 5f, 0, 1)), 0x3BFF6B, 0xC8FFD8, fade);
                Matrix4f matrix = pose.last().pose();
                float height = 3F * (1 - t);
                for (int i = 0; i < 10; i++) {
                    ray(out, matrix, i * Mth.TWO_PI / 10, 1.25F, .08F, height,
                            (i & 1) == 0 ? 0x3BFF6B : 0xC8FFD8, .35F * fade);
                }
                pose.popPose();
                // O raio: uma linha verde da mao ao peito, so nos primeiros instantes.
                if (life < 8 && a.caster(level) instanceof LivingEntity caster) {
                    Vec3 chest = a.pos.add(0, a.extra * .6, 0);
                    at(pose, camera, chest);
                    Vec3 hand = ClientSpellVisuals.castingHand(caster, partial).subtract(chest);
                    float strike = 1 - life / 8f;
                    segment(out, pose.last().pose(), hand, Vec3.ZERO, .09F, 0x3BFF6B, .45F * strike);
                    segment(out, pose.last().pose(), hand, Vec3.ZERO, .03F, 0xE8FFEE, strike);
                    pose.popPose();
                }
            }
            case ASPECTUS_CAPTUS -> {
                if (target == null || !(a.caster(level) instanceof LivingEntity caster)) return;
                Vec3 eyes = target.getEyePosition(partial);
                Vec3 captor = caster.getEyePosition(partial);
                // O olho, de pe sobre a cabeca, encarando quem prende.
                at(pose, camera, eyes.add(0, .75, 0));
                faceTowards(pose, captor.subtract(eyes));
                float open = Mth.clamp(life / 5f, 0, 1);
                pose.scale(1, 1, open);
                layered(out, pose, EYE, .42F, 0x8B3FC4, 0xE6D0FF, fade);
                pose.popPose();
                // O fio do olhar, fino, dos olhos do cativo aos do captor.
                at(pose, camera, eyes);
                segment(out, pose.last().pose(), Vec3.ZERO, captor.subtract(eyes), .012F, 0xB070E0, .35F * fade);
                pose.popPose();
            }
            case DOLOR_UNIVERSUS -> {
                if (target == null) return;
                at(pose, camera, target.getPosition(partial).add(0, .04, 0));
                spin(pose, life, .8F);
                layered(out, pose, SEAL_THORN, a.extra, 0xB0102A, 0xFF4050, fade * pulse(life, .85F));
                drawMesh(out, pose, CIRCLE, a.extra * 1.04F, .05F, 0xFF4050, .35F * fade, 0);
                pose.popPose();
            }
            case DEIECTIO_AREA -> {
                // A onda abrindo do centro ate a borda, e o selo de espinhos que fica marcado no chao.
                float open = Mth.clamp(life / 10f, 0, 1);
                at(pose, camera, a.pos.add(0, .04, 0));
                spin(pose, life, -1.2F);
                layered(out, pose, SEAL_THORN, a.extra * open, 0x7A5CB0, 0xCFC2FF, fade);
                if (life < 16) {
                    float t = life / 16f;
                    band(out, pose.last().pose(), a.extra * t * .82F, a.extra * t, .05F, 0xE6DEFF, .8F * (1 - t), 72);
                }
                pose.popPose();
            }
            case ASPECTUS_AREA -> {
                // O olho enorme de pe sobre quem conjura, e o circulo do alcance no chao.
                float open = Mth.clamp(life / 8f, 0, 1);
                at(pose, camera, a.pos.add(0, 2.6, 0));
                pose.mulPose(ROTATION.rotationY(life * .3F * Mth.DEG_TO_RAD));
                pose.mulPose(ROTATION.rotationX(Mth.HALF_PI));
                pose.scale(1, 1, open);
                layered(out, pose, EYE, 1.6F, 0x8B3FC4, 0xE6D0FF, fade);
                pose.popPose();
                at(pose, camera, a.pos.add(0, .05, 0));
                drawMesh(out, pose, CIRCLE, a.extra * open, .05F, 0x8B3FC4, .3F * fade, 0);
                spin(pose, life, .5F);
                drawMesh(out, pose, RUNE_RING, a.extra * .35F * open, .03F, 0xE6D0FF, .45F * fade, .002F);
                pose.popPose();
            }
            case MORTEM_AREA -> {
                // O pentagrama do tamanho do raio: a sentenca de todos ao mesmo tempo.
                float open = Mth.clamp(life / 8f, 0, 1);
                float t = Mth.clamp(life / 80f, 0, 1);
                at(pose, camera, a.pos.add(0, .04, 0));
                spin(pose, life, -.4F);
                layered(out, pose, SEAL_DEATH, a.extra * open, 0x3BFF6B, 0xC8FFD8, fade);
                Matrix4f matrix = pose.last().pose();
                float height = 6F * (1 - t);
                int rays = 16;
                for (int i = 0; i < rays; i++) {
                    ray(out, matrix, i * Mth.TWO_PI / rays, a.extra * .9F * open, .18F, height,
                            (i & 1) == 0 ? 0x3BFF6B : 0xC8FFD8, .3F * fade);
                }
                if (life < 20) {
                    float wave = life / 20f;
                    band(out, matrix, a.extra * wave * .85F, a.extra * wave, .06F, 0xC8FFD8, .7F * (1 - wave), 72);
                }
                pose.popPose();
            }
            case MUNDUS_VACUUS -> {
                if (target == null) return;
                // Nao ha selo brilhante: a magia e a ausencia. So um fio de runa fechando o corpo.
                float radius = target.getBbWidth() * .8F + .32F;
                at(pose, camera, target.getPosition(partial).add(0, target.getBbHeight() * .55, 0));
                spin(pose, life, -1.6F);
                drawMesh(out, pose, RUNE_RING, radius, .012F, 0x6A4FA0, .5F * fade, 0);
                pose.popPose();
                at(pose, camera, target.getPosition(partial).add(0, .03, 0));
                drawMesh(out, pose, CIRCLE, radius * 1.4F, .02F, 0x2A1840, .35F * fade, 0);
                pose.popPose();
            }
            default -> {
            }
        }
    }

    private static void transpositio(PoseStack pose, VertexConsumer out, Vec3 camera, Vec3 feet, float grow,
                                     float fade, float life) {
        at(pose, camera, feet.add(0, .03, 0));
        layered(out, pose, SEAL_MAJOR, 1.1F * grow, 0x9B4DFF, 0xE0B0FF, fade);
        Matrix4f matrix = pose.last().pose();
        float height = 1.8F * Mth.clamp(1 - life / 30f, 0, 1);
        for (int i = 0; i < 8; i++) {
            ray(out, matrix, i * Mth.TWO_PI / 8, 1.05F * grow, .1F, height, (i & 1) == 0 ? 0x9B4DFF : 0xE0B0FF, .35F * fade);
        }
        pose.popPose();
    }

    /** O selo desenhado na face do bloco, respirando; o clarao de recusa e a quebra. */
    private static void sigil(PoseStack pose, VertexConsumer out, Vec3 camera, Active a, float life, float fade) {
        Direction face = Direction.from3DDataValue(Mth.clamp((int) a.extra, 0, 5));
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        at(pose, camera, a.pos.add(normal.scale(.015)));
        pose.mulPose(face.getRotation());
        float radius = .42F;
        float alpha = fade * (.75F + .15F * Mth.sin(life * .12F));
        if (a.kind == Kind.SIGILLUM_DENY) {
            alpha = fade;
            radius *= 1.1F;
        } else if (a.kind == Kind.SIGILLUM_BREAK) {
            float t = Mth.clamp(life / 16f, 0, 1);
            radius *= 1 + .8F * t;
            alpha = 1 - t;
        }
        alpha = Math.min(1, alpha + a.flash);
        spin(pose, life, .5F);
        layered(out, pose, SEAL_MAJOR, radius, 0x8A3DFF, 0xD8B8FF, alpha);
        disc(out, pose.last().pose(), radius * 1.1F, -.002F, 0x8A3DFF, .12F * alpha, 24);
        pose.popPose();
    }

    // --- Ajudantes ---------------------------------------------------------------------------

    /** Empilha uma pose com origem no ponto do mundo. Quem chama fecha com {@code popPose}. */
    private static void at(PoseStack pose, Vec3 camera, Vec3 world) {
        pose.pushPose();
        pose.translate(world.x - camera.x, world.y - camera.y, world.z - camera.z);
    }

    private static void spin(PoseStack pose, float life, float degreesPerTick) {
        pose.mulPose(ROTATION.rotationY(life * degreesPerTick * Mth.DEG_TO_RAD));
    }

    /** Vira o plano do selo (XZ) para ficar de frente para a direcao dada. */
    private static void faceTowards(PoseStack pose, Vec3 direction) {
        float yaw = (float) Mth.atan2(direction.x, direction.z);
        pose.mulPose(ROTATION.rotationY(yaw));
        pose.mulPose(ROTATION.rotationX(Mth.HALF_PI));
    }

    /** Uma malha de raio 1 escalada; a largura dos tracos e em blocos, independente do raio. */
    private static void drawMesh(VertexConsumer out, PoseStack pose, float[] mesh, float radius, float width,
                                 int color, float alpha, float height) {
        if (radius <= .001F || alpha <= .004F) return;
        pose.pushPose();
        pose.scale(radius, 1, radius);
        SigilGeometry.mesh(out, pose.last().pose(), mesh, width / radius, color, alpha, height);
        pose.popPose();
    }

    /** Tres camadas como os selos da cerimonia: halo largo e fraco, traco medio, fio brilhante. */
    private static void layered(VertexConsumer out, PoseStack pose, float[] mesh, float radius, int main, int accent,
                                float alpha) {
        drawMesh(out, pose, mesh, radius, .045F * radius, main, alpha * .16F, 0);
        drawMesh(out, pose, mesh, radius, .02F * radius, accent, alpha * .8F, .002F);
        drawMesh(out, pose, mesh, radius, .007F * radius, main, alpha, .004F);
    }

    private static float pulse(float life, float base) {
        return base + (1 - base) * Mth.sin(life * .25F);
    }

    private static Vec3 waist(LivingEntity target, float partial) {
        return target.getPosition(partial).add(0, target.getBbHeight() * .5, 0);
    }

    private static Vec3 neck(LivingEntity target, float partial) {
        return target.getPosition(partial).add(0, target.getEyeHeight() - .2, 0);
    }


    private static final class Shard extends RenderStateShard {
        private Shard() {
            super("aurorion_magia_sigil", () -> {
            }, () -> {
            });
        }

        /** Tinta translucida: cobre o que esta atras. */
        static RenderType ink() {
            return RenderType.create("aurorion_magia:sigil_ink", DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, 32768, false, false, RenderType.CompositeState.builder()
                            .setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(PARTICLES_TARGET)
                            .createCompositeState(false));
        }

        /** Luz: soma no que ja esta na tela (SRC_ALPHA, ONE), entao clareia em vez de pintar. */
        static RenderType glow() {
            return RenderType.create("aurorion_magia:sigil_glow", DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, 32768, false, false, RenderType.CompositeState.builder()
                            .setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(PARTICLES_TARGET)
                            .createCompositeState(false));
        }
    }
}
