package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Mortem Dico — Sentenca Final. <b>Magia proibida.</b> "Eu declaro a morte."
 *
 * <p>Mata o alvo na hora, como {@code /kill}: ignora armadura, resistencia, encantamento, escudo,
 * invulnerabilidade de criativo e totem. O dano sai com o tipo proprio
 * {@code aurorion_magia:mortem_dico} (nas tags {@code bypasses_*} do vanilla), que registra quem
 * conjurou na mensagem de morte. Se algum mod ainda segurar a entidade viva, cai no {@code kill()}
 * de verdade.
 *
 * <h2>A sentenca coletiva</h2>
 *
 * <p>Do <b>nivel 2</b> em diante a magia tem a segunda forma: conjurada <b>agachada</b>
 * ({@link AreaCast}), a sentenca deixa de ser de um e passa a ser de todos. Um pentagrama do tamanho
 * do raio se abre no chao e <b>tudo o que estiver dentro dele morre ao mesmo tempo</b> — aliado,
 * inimigo, bicho, sem escolher lado e sem totem. E a ultimate do mod: no nivel 3 sao
 * {@value #RADIUS_L3} blocos de raio, ate {@value #MAX_TARGETS} sentenciados, 400 de mana e um minuto
 * e meio de recarga.
 *
 * <p>Chefes ({@code imune_deslocamento}) ficam de fora <b>so da area</b>: mirado, um a um, o chefe
 * morre igual. Uma luta de chefe nao acaba porque alguem agachou.
 *
 * <p>Morte comum para todos os efeitos: conta vida no {@code aurorion-vidas}, dropa inventario pelas
 * regras normais.
 */
public final class MortemDicoSpell extends AurorionSpell {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, AurorionMagia.id("mortem_dico"));
    private static final int RANGE = 32;
    private static final int MAX_TARGETS = 32;
    private static final int RADIUS_L3 = 14;

    public MortemDicoSpell() {
        super("mortem_dico", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.LEGENDARY, 3, 90, CastType.INSTANT, true);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.baseManaCost = 200;
        this.manaCostPerLevel = 100;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.WITHER_SHOOT);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.23f, 1.0f, 0.42f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        int radius = radius(spellLevel);
        if (radius <= 0) {
            return List.of(Component.translatable("ui.aurorion_magia.morte_instantanea"),
                    Component.translatable("ui.aurorion_magia.alcance", RANGE),
                    Component.translatable("ui.aurorion_magia.proibida"));
        }
        return List.of(Component.translatable("ui.aurorion_magia.morte_instantanea"),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.agachado_sentenca", radius),
                Component.translatable("ui.aurorion_magia.proibida"));
    }

    /** Aliado incluido: a sentenca nao escolhe lado. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (!wide(entity, spellLevel)) {
            return aim(level, entity, playerMagicData, RANGE, true, target -> !target.isSpectator());
        }
        if (!(level instanceof ServerLevel serverLevel)) return true;
        if (!area(serverLevel, entity, spellLevel).isEmpty()) return true;
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_magia.ninguem_em_volta"), true);
        }
        return false;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            if (wide(entity, spellLevel)) {
                judgement(serverLevel, entity, spellLevel);
            } else {
                LivingEntity target = target(serverLevel, entity, playerMagicData);
                if (target != null) sentence(serverLevel, entity, target);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** A sentenca coletiva: o pentagrama abre e todos dentro dele caem juntos. */
    private void judgement(ServerLevel level, LivingEntity caster, int spellLevel) {
        Vec3 center = caster.position();
        // O selo primeiro: ele precisa chegar ao cliente antes de os corpos sumirem.
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.MORTEM_AREA, 80, center, radius(spellLevel));
        sound(level, center, SoundEvents.WITHER_SPAWN, 1.6f, 0.6f);
        sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2f, 1.4f);
        for (LivingEntity victim : area(level, caster, spellLevel)) {
            sentence(level, caster, victim);
        }
    }

    private List<LivingEntity> area(ServerLevel level, LivingEntity caster, int spellLevel) {
        return AreaCast.victims(level, caster, caster.position(), radius(spellLevel), MAX_TARGETS, target -> true);
    }

    private static void sentence(ServerLevel level, LivingEntity caster, LivingEntity target) {
        Vec3 feet = target.position();
        float height = target.getBbHeight();

        DamageSource death = new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE), caster);
        target.hurt(death, Float.MAX_VALUE);
        if (target.isAlive()) target.kill();

        sound(level, feet, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f);
        sound(level, feet, SoundEvents.SOUL_ESCAPE.value(), 2.0f, 0.5f);
        // Preso ao ponto, nao ao alvo: o corpo some, o selo fica.
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.MORTEM_DICO, 40, feet, height);
    }

    /** A area so existe do nivel 2 em diante, e so agachado. */
    private static boolean wide(LivingEntity caster, int spellLevel) {
        return radius(spellLevel) > 0 && AreaCast.wide(caster);
    }

    /** Sem area no nivel 1; 8 blocos no 2; {@value #RADIUS_L3} no 3. */
    private static int radius(int spellLevel) {
        return switch (spellLevel) {
            case 1 -> 0;
            case 2 -> 8;
            default -> RADIUS_L3;
        };
    }
}
