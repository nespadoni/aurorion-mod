package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A corrente do Vinculum Carnificis.
 *
 * <p>Quem esta preso anda, bate, conjura e se defende a vontade — dentro do raio. Ao cruzar a borda,
 * leva um puxao na direcao da ancora <b>e sangra</b>: a corrente nao e um muro macio, e ferro
 * fechado na carne. Cada puxao seguido aumenta a "tensao", e o proximo vem mais forte e dai mais
 * caro. Ficar dentro do raio alivia a tensao aos poucos.
 *
 * <p>O dano tem um intervalo proprio de {@value #HURT_INTERVAL} ticks, independente do tick da
 * corrente (5x por segundo): quem insiste na borda leva <b>um</b> golpe por segundo, nao cinco. Sai
 * com o tipo {@code aurorion_magia:vinculum_carnificis}, que ignora armadura e escudo e nomeia quem
 * prendeu na mensagem de morte — morrer na corrente e morrer pela mao de quem a lancou.
 *
 * <p>Teleporte nao salva: perola, chorus e teleportes de magia sao cancelados pelo
 * {@code MagiaServerEvents}; e se alguma coisa colocar o preso muito longe mesmo assim (um mod de
 * teleporte sem evento), a corrente o arranca de volta <b>para a propria ancora</b> — o unico ponto
 * que a magia sabe ser chao firme, porque foi de la que o alvo foi preso.
 *
 * <p>A ancora mora no {@code persistentData} do preso, ao lado do efeito que tambem e salvo:
 * deslogar preso e voltar continua preso, no mesmo lugar.
 */
public final class Binding {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, AurorionMagia.id("vinculum_carnificis"));

    private static final String KEY = AurorionMagia.MOD_ID + ":vinculo";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_Z = "Z";
    private static final String KEY_DIM = "Dim";
    private static final String KEY_RADIUS = "Radius";
    private static final String KEY_STRAIN = "Strain";
    private static final String KEY_OWNER = "Owner";
    private static final String KEY_HURT_AT = "HurtAt";

    /** Alem do raio mais isto, nao e fuga a pe: foi teleporte. Volta para a ancora na hora. */
    private static final double SNAP_BACK = 8;
    private static final int MAX_STRAIN = 8;
    /** Um golpe por segundo, no maximo, por mais que o preso force a borda. */
    private static final int HURT_INTERVAL = 20;
    /** 3 de dano no primeiro puxao, +1 por ponto de tensao: 11 (cinco coracoes e meio) no limite. */
    private static final float HURT_BASE = 3;
    private static final float HURT_PER_STRAIN = 1;

    private Binding() {
    }

    public static void bind(LivingEntity target, LivingEntity caster, float radius, int duration) {
        Vec3 anchor = target.position();
        CompoundTag tag = new CompoundTag();
        tag.putDouble(KEY_X, anchor.x);
        tag.putDouble(KEY_Y, anchor.y);
        tag.putDouble(KEY_Z, anchor.z);
        tag.putString(KEY_DIM, target.level().dimension().location().toString());
        tag.putFloat(KEY_RADIUS, radius);
        tag.putUUID(KEY_OWNER, caster.getUUID());
        target.getPersistentData().put(KEY, tag);

        target.addEffect(new MobEffectInstance(MagiaEffects.BOUND, duration, 0, false, false, true), caster);
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.VINCULUM, duration, anchor, radius);
    }

    /** Chamado pelo tick do efeito, 5 vezes por segundo, so em quem esta preso. */
    public static void enforce(LivingEntity entity) {
        CompoundTag tag = tagOf(entity);
        if (tag == null || !(entity.level() instanceof ServerLevel level)
                || !tag.getString(KEY_DIM).equals(level.dimension().location().toString())) return;

        Vec3 anchor = new Vec3(tag.getDouble(KEY_X), tag.getDouble(KEY_Y), tag.getDouble(KEY_Z));
        float radius = tag.getFloat(KEY_RADIUS);
        Vec3 offset = entity.position().subtract(anchor);
        double distance = offset.length();
        int strain = tag.getInt(KEY_STRAIN);

        if (distance <= radius) {
            if (strain > 0) tag.putInt(KEY_STRAIN, strain - 1);
            return;
        }

        strain = Math.min(MAX_STRAIN, strain + 1);
        tag.putInt(KEY_STRAIN, strain);
        Vec3 inward = offset.scale(-1 / distance);

        if (distance > radius + SNAP_BACK) {
            // Longe demais para ter sido a pe. Volta para a ancora, e nao para a borda: a ancora e o
            // unico ponto que a magia sabe ser chao firme (foi onde o alvo estava quando foi preso).
            // Cair de um ponto qualquer da borda e o que fazia gente despencar do nada.
            moveTo(level, entity, anchor);
        } else {
            // Quanto mais passou da borda e quanto mais vezes ja tentou, mais violento.
            double force = Math.min(2.6, 0.6 + 0.2 * strain + (distance - radius) * 0.3);
            AurorionSpell.launch(entity, inward.scale(force).add(0, 0.2 + 0.03 * strain, 0));
        }
        entity.resetFallDistance();
        bleed(level, entity, tag, strain);
        snap(entity, anchor, strain);
    }

    /** Perola ou teleporte negado: mesmo estalo do puxao, sem mover ninguem. */
    public static void refuseTeleport(LivingEntity entity) {
        CompoundTag tag = tagOf(entity);
        if (tag == null) return;
        Vec3 anchor = new Vec3(tag.getDouble(KEY_X), tag.getDouble(KEY_Y), tag.getDouble(KEY_Z));
        snap(entity, anchor, MAX_STRAIN);
    }

    public static boolean isBound(Entity entity) {
        return entity instanceof LivingEntity living && living.hasEffect(MagiaEffects.BOUND) && tagOf(living) != null;
    }

    public static void release(LivingEntity entity) {
        entity.getPersistentData().remove(KEY);
    }

    /** O ferro na carne. No maximo uma vez por segundo, por mais que o preso force a borda. */
    private static void bleed(ServerLevel level, LivingEntity entity, CompoundTag tag, int strain) {
        long now = level.getGameTime();
        if (now - tag.getLong(KEY_HURT_AT) < HURT_INTERVAL) return;
        tag.putLong(KEY_HURT_AT, now);

        Entity owner = tag.hasUUID(KEY_OWNER) ? level.getEntity(tag.getUUID(KEY_OWNER)) : null;
        DamageSource source = new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE), owner);
        entity.hurt(source, HURT_BASE + HURT_PER_STRAIN * strain);
        AurorionSpell.sound(entity, SoundEvents.PLAYER_HURT, 1.0f, 0.6f);
    }

    /** Jogador pelo teleporte de conexao; o resto direto. Teleportar jogador "na mao" desincroniza. */
    private static void moveTo(ServerLevel level, LivingEntity entity, Vec3 to) {
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(level, to.x, to.y, to.z, player.getYRot(), player.getXRot());
        } else {
            entity.teleportTo(to.x, to.y, to.z);
        }
        AurorionSpell.launch(entity, Vec3.ZERO);
    }

    private static void snap(LivingEntity entity, Vec3 anchor, int strain) {
        AurorionSpell.sound(entity, SoundEvents.CHAIN_BREAK, 1.0f, 0.55f + strain * 0.04f);
        AurorionSpell.sound(entity, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8f, 0.6f);
        MagiaNetwork.sendVisual(null, entity, SpellVisualPayload.Kind.VINCULUM_SNAP, 8, anchor, strain);
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_magia.corrente"), true);
        }
    }

    @Nullable
    private static CompoundTag tagOf(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(KEY, Tag.TAG_COMPOUND) ? data.getCompound(KEY) : null;
    }
}
