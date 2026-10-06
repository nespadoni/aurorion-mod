package com.aurorion.magia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.jetbrains.annotations.Nullable;

/**
 * O Capricho no cliente: quem virou bicho e desenhado como o guaxinim do Alex's Mobs.
 *
 * <p>Um guaxinim so, criado uma vez por mundo e nunca adicionado a ele — so serve de molde para o
 * renderizador do Alex's Mobs. A cada quadro ele recebe a posicao, a direcao do corpo e da cabeca de
 * quem esta transformado e e desenhado no lugar dele. O tipo vem do registro pelo nome, entao o Alex's
 * Mobs nao e dependencia de compilacao; sem ele no pack, a forma e a raposa do vanilla.
 */
final class PolymorphRender {
    private static final ResourceLocation RACCOON = ResourceLocation.fromNamespaceAndPath("alexsmobs", "raccoon");

    @Nullable
    private static Entity beast;
    @Nullable
    private static Level beastLevel;

    private PolymorphRender() {
    }

    /** {@code true} se desenhou o bicho — e o corpo original nao deve ser desenhado. */
    static boolean render(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!KitVisuals.polymorphed(entity)) return false;
        if (!(beast(entity.level()) instanceof LivingEntity form)) return true;

        float partial = event.getPartialTick();
        float body = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
        float head = Mth.rotLerp(partial, entity.yHeadRotO, entity.yHeadRot);
        form.setPos(entity.getX(), entity.getY(), entity.getZ());
        form.xo = entity.xo;
        form.yo = entity.yo;
        form.zo = entity.zo;
        form.yBodyRot = form.yBodyRotO = body;
        form.yHeadRot = form.yHeadRotO = head;
        form.setYRot(body);
        form.yRotO = body;
        form.setXRot(entity.getXRot());
        form.xRotO = entity.xRotO;
        form.tickCount = entity.tickCount;
        form.setOnGround(entity.onGround());
        form.walkAnimation.setSpeed(entity.walkAnimation.speed());

        Minecraft.getInstance().getEntityRenderDispatcher().render(form, 0, 0, 0, body, partial,
                event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        return true;
    }

    @Nullable
    private static Entity beast(Level level) {
        if (beast == null || beastLevel != level) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(RACCOON).orElse(EntityType.FOX);
            beast = type.create(level);
            beastLevel = level;
        }
        return beast;
    }

    static void clear() {
        beast = null;
        beastLevel = null;
    }
}
