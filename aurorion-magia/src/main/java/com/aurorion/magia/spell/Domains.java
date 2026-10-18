package com.aurorion.magia.spell;

import com.aurorion.magia.compat.AreasMagic;
import com.aurorion.magia.entity.SpellZoneEntity;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import com.aurorion.magia.AurorionMagia;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Um dominio por conjurador. A entidade e o relogio; nao ha varredura global de jogadores. */
public final class Domains {
    private static final ResourceKey<DamageType> SHRINE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            AurorionMagia.id("santuario_malevolente"));
    private static final String KEY = "aurorion_magia:dominio";
    /** Pulso do Vazio Infinito (ticks), o mesmo de {@code SpellZoneEntity.domain}. */
    private static final int VOID_PULSE = 4;
    /**
     * Duracao do Vazio Infinito no alvo: tres pulsos. Renovado so quando sobra um pulso ou menos
     * (padrao {@code DreadAura.renew}), o efeito segue continuo sem um pacote de efeito a cada pulso.
     */
    private static final int VOID_TICKS = VOID_PULSE * 3;
    private Domains() { }

    public static void start(ServerLevel level, LivingEntity caster, SpellZoneEntity.Shape shape,
                             float radius, int duration, float damage) {
        stop(caster);
        SpellZoneEntity zone = SpellZoneEntity.create(level, caster, shape, caster.position(), radius,
                radius, duration, damage, Vec3.ZERO);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Zone", zone.getUUID());
        tag.putString("Dim", level.dimension().location().toString());
        caster.getPersistentData().put(KEY, tag);
        // Amplificador identifica a expansao no cliente, sem pacote adicional de estado.
        caster.addEffect(new MobEffectInstance(MagiaEffects.DOMAIN_EXPANSION, duration, shape.ordinal(), false, false, false));
        // A incapacitacao ja vale no instante em que a expansao abre.
        pulse(zone, caster, damage);
        AurorionSpell.sound(caster, SoundEvents.SCULK_SHRIEKER_SHRIEK, 3, shape == SpellZoneEntity.Shape.INFINITE_VOID ? .45F : .8F);
        AurorionSpell.sound(caster, SoundEvents.LIGHTNING_BOLT_THUNDER, 3, .6F);
    }

    @Nullable
    public static SpellZoneEntity zone(LivingEntity caster) {
        CompoundTag tag = caster.getPersistentData().getCompound(KEY);
        if (!tag.hasUUID("Zone") || !(caster.level() instanceof ServerLevel level)) return null;
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dim"));
        ServerLevel origin = dim == null ? null : level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        return origin != null && origin.getEntity(tag.getUUID("Zone")) instanceof SpellZoneEntity zone ? zone : null;
    }

    /**
     * O mesmo que {@code zone(caster) == zone}, sem resolver dimensao nem buscar entidade: a zona que
     * pergunta esta viva no proprio nivel, entao basta o UUID gravado no conjurador ser o dela.
     */
    public static boolean owns(LivingEntity caster, SpellZoneEntity zone) {
        if (!(caster.level() instanceof ServerLevel)) return false;
        CompoundTag tag = caster.getPersistentData().getCompound(KEY);
        return tag.hasUUID("Zone") && zone.getUUID().equals(tag.getUUID("Zone"));
    }

    public static void stop(LivingEntity caster) {
        stop(caster, true);
    }

    /** O evento ja esta removendo o marcador; nao altera o mapa de efeitos durante sua iteracao. */
    public static void onEffectEnded(LivingEntity caster) {
        stop(caster, false);
    }

    private static void stop(LivingEntity caster, boolean removeMarker) {
        if (!caster.getPersistentData().contains(KEY)) return;
        SpellZoneEntity zone = zone(caster);
        caster.getPersistentData().remove(KEY);
        if (removeMarker) caster.removeEffect(MagiaEffects.DOMAIN_EXPANSION);
        if (zone != null) zone.discard();
    }

    public static void stop(LivingEntity caster, SpellZoneEntity.Shape shape) {
        SpellZoneEntity zone = zone(caster);
        if (zone != null && zone.shape() == shape) stop(caster);
    }

    public static void pulse(SpellZoneEntity zone, LivingEntity caster, float damage) {
        if (!(zone.level() instanceof ServerLevel level)) return;
        boolean infinite = zone.shape() == SpellZoneEntity.Shape.INFINITE_VOID;
        Vec3 center = zone.position();
        float radius = zone.radius();
        // Resolvido uma vez por pulso, e nao por alvo: o tipo de dano nao muda entre um corte e outro.
        Holder<DamageType> shrineDamage = infinite ? null
                : level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SHRINE_DAMAGE);
        // Entidades em chunks ja carregados, sem ler blocos ou forcar chunks num raio de 200.
        // Sem teto de alvos: todo alvo elegivel dentro da esfera recebe o corte.
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(radius), t -> Hits.hittable(caster, t)
                        && Hits.enemy(caster, t) && t.distanceToSqr(center) <= radius * radius)) {
            // Um abate pode devolver o conjurador do Reino das Almas no meio deste mesmo pulso.
            if (zone.isRemoved() || !caster.isAlive() || caster.level() != level) return;
            if (!AreasMagic.allowedAt(level, target.position(), caster.getUUID())) continue;
            if (infinite) {
                if (Displacement.isImmune(target)) continue;
                target.stopRiding();
                if (target instanceof ServerPlayer player && MagicData.getPlayerMagicData(player).isCasting()) {
                    Utils.serverSideCancelCast(player);
                }
                renewVoid(target, caster);
                target.stopUsingItem();
                ControlSpells.syncVoice(target, null);
            } else {
                boolean cleave = (zone.tickCount / 10) % 2 == 0;
                // Destrinchar se adapta ao vigor do alvo, mas tem teto para chefes de muita vida.
                float cut = cleave ? Math.min(damage * 1.75F, damage + target.getMaxHealth() * .035F) : damage;
                FriendlyFire.applyDamage(target, cut, new DamageSource(shrineDamage, zone, caster));
                MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.DOMAIN_SLASH, 9,
                        target.position(), cleave ? 1 : 0);
                if (target instanceof ServerPlayer) AurorionSpell.sound(target, SoundEvents.PLAYER_ATTACK_SWEEP, .9F,
                        cleave ? .6F : 1.2F);
            }
        }
        if (zone.tickCount % 40 == 0) AurorionSpell.sound(level, center,
                infinite ? SoundEvents.BEACON_AMBIENT : SoundEvents.SOUL_ESCAPE.value(), 2, .5F);
    }

    /**
     * Mantem o Vazio Infinito no alvo. Com mais de um pulso de sobra nao reaplica: o efeito ainda cobre
     * o proximo pulso, e reaplicar so mandaria outro pacote de efeito igual.
     */
    private static void renewVoid(LivingEntity target, LivingEntity caster) {
        MobEffectInstance current = target.getEffect(MagiaEffects.INFINITE_VOID);
        if (current != null && (current.isInfiniteDuration() || current.getDuration() > VOID_PULSE)) return;
        target.addEffect(new MobEffectInstance(MagiaEffects.INFINITE_VOID, VOID_TICKS, 0, false, false, true), caster);
    }
}
