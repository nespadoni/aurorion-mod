package com.aurorion.magia.spell;

import com.aurorion.magia.passive.VoidPocket;
import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.spoils.Spoils;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Spoliatio — Espoliacao. Instantanea: tudo o que a pessoa carrega — inventario, armadura, acessorios,
 * o telefone — vai parar em mochilas no inventario de quem conjurou (ver {@link Spoils}).
 *
 * <ul>
 *   <li><b>Em pe</b>: o alvo na mira, a ate {@value #RANGE} blocos.</li>
 *   <li><b>Agachado</b> ({@link AreaCast}): todos os jogadores num raio de {@value #AREA_RADIUS} blocos,
 *       ate {@value #MAX_TARGETS} — o ataque ao acampamento inteiro. Aliados de quem conjura ficam de
 *       fora. Sem ninguem para espoliar, nao sai, nao gasta mana nem recarga.</li>
 * </ul>
 *
 * <p>Feita para o sequestro: quem e levado fica sem arma, sem item e sem como chamar ajuda. Por isso e
 * <b>proibida</b> — so a staff concede, uma a uma.
 *
 * <p>So jogador e alvo (criatura nao tem inventario a tomar), e so onde PvP vale ({@link Hits#hittable}).
 * Quem esta no Bolso do Vazio ou no Reino das Almas fica de fora: os pertences dele estao guardados
 * noutro lugar, e tomar o que esta no corpo ali seria tomar o que o bolso emprestou.
 */
public final class SpoliatioSpell extends AurorionSpell {
    private static final int RANGE = 6;
    private static final int AREA_RADIUS = 48;
    /** Teto de vitimas por conjuracao em area: cada uma rende ate algumas mochilas. */
    private static final int MAX_TARGETS = 40;

    public SpoliatioSpell() {
        super("spoliatio", SchoolRegistry.ENDER_RESOURCE, SpellRarity.EPIC, 1, 180, CastType.INSTANT, true);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.baseManaCost = 100;
        this.manaCostPerLevel = 0;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.BUNDLE_DROP_CONTENTS);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.45f, 0.2f, 0.75f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.toma_tudo"),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.agachado_area", AREA_RADIUS),
                Component.translatable("ui.aurorion_magia.proibida"));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (!(entity instanceof ServerPlayer caster)) return false;
        if (!AreaCast.wide(caster)) {
            return aim(level, caster, playerMagicData, RANGE, false,
                    target -> target instanceof ServerPlayer victim && takeable(caster, victim));
        }
        if (!(level instanceof ServerLevel serverLevel) || !area(serverLevel, caster).isEmpty()) return true;
        caster.displayClientMessage(Component.translatable("aurorion_magia.ninguem_em_volta"), true);
        return false;
    }

    private static boolean takeable(LivingEntity caster, ServerPlayer victim) {
        return Hits.hittable(caster, victim)
                && !VoidPocket.isInside(victim) && !VoidPocket.isInside(caster)
                && !victim.hasEffect(MagiaEffects.SOUL_REALM) && !caster.hasEffect(MagiaEffects.SOUL_REALM);
    }

    /** Jogadores no raio que podem ser espoliados, nunca do lado de quem conjura. */
    private static List<LivingEntity> area(ServerLevel level, ServerPlayer caster) {
        return AreaCast.victims(level, caster, caster.position(), AREA_RADIUS, MAX_TARGETS,
                target -> target instanceof ServerPlayer victim && !FriendlyFire.spares(caster, victim)
                        && takeable(caster, victim));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer caster) {
            if (AreaCast.wide(caster)) {
                int people = 0, stacks = 0;
                for (LivingEntity victim : area(serverLevel, caster)) {
                    stacks += take(serverLevel, caster, (ServerPlayer) victim, false);
                    people++;
                }
                sound(serverLevel, caster.position(), SoundEvents.WARDEN_SONIC_BOOM, 1f, .5f);
                serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, caster.getX(), caster.getY() + 1, caster.getZ(),
                        120, 2, 1, 2, .3);
                caster.displayClientMessage(Component.translatable("aurorion_magia.espolios_area", stacks, people), true);
            } else if (target(serverLevel, caster, playerMagicData) instanceof ServerPlayer victim && takeable(caster, victim)) {
                int taken = take(serverLevel, caster, victim, true);
                caster.displayClientMessage(taken == 0
                        ? Component.translatable("aurorion_magia.espolios_nada")
                        : Component.translatable("aurorion_magia.espolios_tomados", taken, victim.getDisplayName()), true);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /**
     * Uma vitima: confisco, aviso e o efeito. {@code full} so no alvo unico: em area, com ate
     * {@value #MAX_TARGETS} vitimas, o rastro de cada uma viraria centenas de pacotes de uma vez.
     */
    private static int take(ServerLevel level, ServerPlayer caster, ServerPlayer victim, boolean full) {
        int taken = Spoils.confiscate(caster, victim);
        victim.displayClientMessage(Component.translatable("aurorion_magia.espoliado"), true);
        if (full) {
            effects(level, caster, victim);
        } else {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, victim.getX(), victim.getY() + 1, victim.getZ(),
                    40, .35, .6, .35, .05);
            sound(victim, SoundEvents.ENDERMAN_TELEPORT, .9f, .6f);
        }
        return taken;
    }

    /** Um sopro roxo saindo da vitima e entrando em quem conjurou. Uma rajada, sem nada por tick. */
    private static void effects(ServerLevel level, LivingEntity caster, LivingEntity victim) {
        Vec3 from = victim.position().add(0, victim.getBbHeight() * .55, 0);
        Vec3 to = caster.position().add(0, caster.getBbHeight() * .55, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 60, .35, .6, .35, .05);
        level.sendParticles(ParticleTypes.WITCH, from.x, from.y, from.z, 18, .3, .5, .3, .02);
        for (int i = 1; i <= 10; i++) {
            Vec3 at = from.lerp(to, i / 11.0);
            level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 4, .08, .08, .08, .15);
        }
        sound(victim, SoundEvents.ENDERMAN_TELEPORT, .9f, .6f);
        sound(victim, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1f, .7f);
        sound(caster, SoundEvents.BUNDLE_INSERT, 1f, .8f);
    }
}
