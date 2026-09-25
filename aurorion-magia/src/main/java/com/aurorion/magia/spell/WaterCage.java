package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A esfera d'agua do Carcer Aquae: o alvo boia dentro dela, parado no ponto em que foi pego.
 *
 * <p>O ponto fica no {@code persistentData} do preso, como a ancora do {@link Binding} — deslogar
 * preso e voltar continua preso, no mesmo lugar. O tick do efeito puxa o corpo de volta para esse
 * ponto todo tick, o que tambem desfaz o que o cliente dele tentou andar.
 *
 * <p><b>Resgate.</b> Qualquer golpe de fora estoura a bolha. E a diferenca entre esta magia e uma
 * prisao: capturar alguem no meio da praça sem ninguem poder tira-lo de la vira impasse; com o
 * resgate, vira cena. Quem estoura nao machuca o preso — o dano do golpe e absorvido pela agua.
 */
public final class WaterCage {
    private static final String KEY = AurorionMagia.MOD_ID + ":carcer";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_Z = "Z";
    private static final String KEY_DIM = "Dim";

    /** Quanto o corpo boia acima do chao em que estava. */
    private static final double FLOAT_HEIGHT = 0.6;

    private WaterCage() {
    }

    public static void cage(LivingEntity target, LivingEntity caster, int duration) {
        Vec3 at = target.position().add(0, FLOAT_HEIGHT, 0);
        CompoundTag tag = new CompoundTag();
        tag.putDouble(KEY_X, at.x);
        tag.putDouble(KEY_Y, at.y);
        tag.putDouble(KEY_Z, at.z);
        tag.putString(KEY_DIM, target.level().dimension().location().toString());
        target.getPersistentData().put(KEY, tag);

        target.addEffect(new MobEffectInstance(MagiaEffects.CAGED, duration, 0, false, false, true), caster);
        target.stopRiding();
        if (target instanceof ServerPlayer player) {
            player.stopUsingItem();
            player.displayClientMessage(Component.translatable("aurorion_magia.encarcerado"), true);
        }
        AurorionSpell.launch(target, Vec3.ZERO);
        AurorionSpell.sound(target, SoundEvents.PLAYER_SPLASH, 1.1f, 0.7f);
        AurorionSpell.sound(target, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.6f);
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.CARCER_AQUAE, duration);
    }

    /** Tick do efeito: o corpo volta para o ponto da bolha e a queda nunca acumula. */
    public static void hold(LivingEntity entity) {
        CompoundTag tag = tagOf(entity);
        if (tag == null || !(entity.level() instanceof ServerLevel level)
                || !tag.getString(KEY_DIM).equals(level.dimension().location().toString())) return;

        Vec3 at = new Vec3(tag.getDouble(KEY_X), tag.getDouble(KEY_Y), tag.getDouble(KEY_Z));
        entity.resetFallDistance();
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity.position().distanceToSqr(at) < 0.0004) {
            // Ja esta no lugar: nao manda pacote de posicao por tick so para repetir o obvio.
            entity.hasImpulse = false;
            return;
        }
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(at.x, at.y, at.z, player.getYRot(), player.getXRot());
        } else {
            entity.setPos(at.x, at.y, at.z);
        }
    }

    public static boolean isCaged(Entity entity) {
        return entity instanceof LivingEntity living && living.hasEffect(MagiaEffects.CAGED);
    }

    /**
     * O golpe que arrebenta a bolha: um corpo que acertou outro corpo.
     *
     * <p>Precisa vir de <b>fora</b> ({@code getDirectEntity()} existe e nao e o proprio preso) — o
     * afogamento, a queda e o veneno que o preso ja carregava nao contam, senao qualquer sangramento
     * anterior soltaria a captura sozinho no primeiro tick. O Submersio tambem fica de fora de
     * proposito: afogar alguem dentro da bolha e uma combinacao valida das duas magias da agua, e nao
     * um jeito torto de se libertar.
     */
    public static boolean breaksOn(LivingEntity caged, DamageSource source) {
        Entity direct = source.getDirectEntity();
        return direct != null && direct != caged && !source.is(Drowning.DAMAGE_TYPE);
    }

    /** Um golpe de fora: a agua arrebenta e solta quem estava dentro. */
    public static void burst(LivingEntity entity) {
        entity.removeEffect(MagiaEffects.CAGED);
        AurorionSpell.sound(entity, SoundEvents.GLASS_BREAK, 1.0f, 0.8f);
        AurorionSpell.sound(entity, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.0f);
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_magia.carcer_rompido"), true);
        }
    }

    public static void release(LivingEntity entity) {
        entity.getPersistentData().remove(KEY);
    }

    @Nullable
    private static CompoundTag tagOf(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(KEY, Tag.TAG_COMPOUND) ? data.getCompound(KEY) : null;
    }
}
