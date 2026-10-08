package com.aurorion.magia.spell;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Impetus Venti — <b>Impeto do Vento</b>. Um avanco imparavel para a frente, montado no vento, que
 * termina rachando o chao e jogando para o alto todo mundo em volta.
 *
 * <p>Inspirada na "Forca Imparavel": quem conjura dispara na direcao em que olha, sem que nada o
 * empurre no caminho; ao chegar, todos num raio de {@value Impetus#RADIUS} blocos sobem uns seis blocos
 * e ficam cerca de 1,5 s no ar, sem conseguir conjurar. <b>No nivel 1 o impacto nao fere</b> — e so o
 * lancamento; do nivel 2 em diante ele causa dano, que cresce por nivel.
 *
 * <p>A mecanica esta em {@link Impetus}; aqui ficam a distancia, o dano e o texto do livro.
 */
public final class ImpetusVentiSpell extends AurorionSpell {
    public ImpetusVentiSpell() {
        super("impetus_venti", SchoolRegistry.EVOCATION_RESOURCE, SpellRarity.UNCOMMON, 5, 18, CastType.INSTANT);
        this.baseSpellPower = 2;
        this.spellPowerPerLevel = 2;
        this.baseManaCost = 40;
        this.manaCostPerLevel = 8;
        this.castTime = 0;
    }

    /** Os sons saem do {@link Impetus}: a partida junto do impulso, o impacto no ponto de chegada. */
    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.85f, 1.0f, 0.94f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        float damage = damage(spellLevel, caster);
        return List.of(
                Component.translatable("ui.aurorion_magia.investida", (int) distance(spellLevel)),
                Component.translatable("ui.aurorion_magia.raio", (int) Impetus.RADIUS),
                Component.translatable("ui.aurorion_magia.no_ar", Utils.timeFromTicks(30, 1)),
                damage > 0
                        ? Component.translatable("ui.aurorion_magia.dano_impacto", Utils.stringTruncation(damage, 1))
                        : Component.translatable("ui.aurorion_magia.sem_dano"),
                Component.translatable("ui.aurorion_magia.imparavel"));
    }

    /** Sem mira: a investida sai sempre para onde se olha, mesmo sem ninguem na frente. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return !Impetus.isDashing(entity);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            Impetus.start(serverLevel, entity, distance(spellLevel), damage(spellLevel, entity));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** 8 blocos no nivel 1, +1,5 por nivel (14 no 5). */
    private static double distance(int spellLevel) {
        return 8 + 1.5 * (spellLevel - 1);
    }

    /** Nivel 1 so lanca; do 2 em diante fere, com o poder de magia de quem conjura. */
    private float damage(int spellLevel, @Nullable LivingEntity caster) {
        return spellLevel <= 1 ? 0 : damagePower(spellLevel - 1, caster);
    }
}
