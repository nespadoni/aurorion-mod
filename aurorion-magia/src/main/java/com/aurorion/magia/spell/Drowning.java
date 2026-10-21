package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * O pulmao cheio d'agua do Submersio.
 *
 * <p>A magia nao inventa uma segunda mecanica de ar: ela <b>gasta o ar do vanilla</b>. Com isso tudo
 * o que ja existe em volta continua valendo de graca — a barra de bolhas some na tela do alvo, o
 * encantamento Respiracao segura mais tempo, a poçao de respirar embaixo d'agua salva, e sair do
 * efeito enche o ar de volta como sair da agua. Escrever um contador proprio teria dado o mesmo
 * afogamento sem nenhuma dessas tres coisas.
 *
 * <p>Quando o ar zera, o dano sai com o tipo {@code aurorion_magia:submersio}, que <b>nomeia quem
 * conjurou</b> na mensagem de morte. Por isso o conjurador fica guardado no {@code persistentData} do
 * alvo, ao lado do efeito que tambem e salvo: relogar afogando continua afogando, e continua sendo
 * culpa da mesma pessoa.
 */
public final class Drowning {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, AurorionMagia.id("submersio"));

    private static final String KEY_OWNER = AurorionMagia.MOD_ID + ":afogador";

    private Drowning() {
    }

    public static void drown(LivingEntity target, LivingEntity caster, int duration) {
        target.getPersistentData().putUUID(KEY_OWNER, caster.getUUID());
        target.addEffect(new MobEffectInstance(MagiaEffects.DROWNING, duration, 0, false, false, true), caster);
        // O pulmao ja comeca comprometido: a primeira bolhada sai na hora, e nao daqui a dez ticks.
        target.setAirSupply(Math.min(target.getAirSupply(), target.getMaxAirSupply() / 2));
        AurorionSpell.sound(target, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.9f, 0.6f);
    }

    /**
     * Um pulso do efeito (meio segundo). Primeiro o ar do vanilla baixa; depois que ele zera, o golpe.
     *
     * <p>O afogamento so continua enquanto quem conjurou esta no servidor: sem ele, o dano sairia sem
     * causador, e dano sem causador passa por cima da protecao de PvP das areas. Dono fora (saiu do
     * jogo, morreu) acaba com o efeito. Vitima parada num lugar sem PvP nao perde ar nem leva golpe
     * enquanto estiver ali — o efeito so espera.
     *
     * @return {@code false} quando o efeito deve sair
     */
    public static boolean pulse(LivingEntity entity, int airPerPulse, float damage) {
        if (!(entity.level() instanceof ServerLevel level)) return true;
        Entity owner = owner(level, entity);
        if (owner == null) return false;
        if (!Hits.pvpAllowedFrom(owner, entity)) return true;
        int air = entity.getAirSupply();
        if (air > -20) {
            entity.setAirSupply(air - airPerPulse);
            return true;
        }
        entity.setAirSupply(-20);
        entity.hurt(FriendlyFire.source(source(level, owner), entity), damage);
        AurorionSpell.sound(entity, SoundEvents.PLAYER_HURT_DROWN, 0.7f, 0.9f);
        return true;
    }

    /** Bolhas escapando: so cosmetico, e so no cliente de quem esta por perto. */
    public static Vec3 mouth(LivingEntity entity) {
        return entity.position().add(0, entity.getEyeHeight() - 0.1, 0);
    }

    public static void release(LivingEntity entity) {
        entity.getPersistentData().remove(KEY_OWNER);
    }

    private static DamageSource source(ServerLevel level, Entity owner) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE), owner);
    }

    @Nullable
    private static Entity owner(ServerLevel level, LivingEntity entity) {
        return entity.getPersistentData().hasUUID(KEY_OWNER)
                ? Owners.find(level, entity.getPersistentData().getUUID(KEY_OWNER))
                : null;
    }
}
