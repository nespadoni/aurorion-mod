package com.aurorion.magia.spell;

import com.aurorion.magia.entity.SpellZoneEntity;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Ventus Custos — <b>Vento Guardiao</b>. A magia defensiva da escola do vento.
 *
 * <p>No instante da conjuracao, todos dentro do circulo sao arremessados para fora dele. Depois disso
 * fica a <b>barreira</b>: enquanto ela durar, quem tentar entrar e empurrado de volta, cada vez mais
 * forte quanto mais fundo conseguir furar. Quem ergueu a barreira passa por ela a vontade — e quem
 * fica dentro fica sozinho.
 *
 * <p>Ela <b>nao cura e nao protege de dano</b>: flecha, magia e bola de fogo atravessam. O que ela
 * compra e distancia, que e a moeda de quem esta em um contra tres. Curar junto teria feito dela a
 * unica magia defensiva que alguem levaria.
 *
 * <p>A barreira e um {@link SpellZoneEntity}: nasce onde voce estava, nao anda com voce. Recuar para
 * dentro dela e uma decisao; arrasta-la junto seria imunidade ambulante.
 */
public final class VentusCustosSpell extends AurorionSpell {
    /** O 7x7 circular do pedido: raio de 3,5 blocos, medido do centro. */
    private static final float RADIUS = 3.5f;
    private static final float HEIGHT = 3.5f;

    public VentusCustosSpell() {
        super("ventus_custos", SchoolRegistry.EVOCATION_RESOURCE, SpellRarity.UNCOMMON, 5, 22, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 35;
        this.manaCostPerLevel = 7;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.BREEZE_WIND_CHARGE_BURST.value());
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.78f, 0.95f, 0.88f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.raio", (int) Math.ceil(RADIUS)),
                Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.barreira_empurra"),
                Component.translatable("ui.aurorion_magia.barreira_sem_dano"));
    }

    /** Defensiva nao mira ninguem: erguer a barreira num corredor vazio e uma jogada valida. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return true;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 at = entity.position();
            SpellZoneEntity.create(serverLevel, entity, SpellZoneEntity.Shape.WARD, at,
                    RADIUS, HEIGHT, duration(spellLevel), 1, Vec3.ZERO);
            sound(serverLevel, at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4f, 0.8f);
            sound(serverLevel, at, SoundEvents.BEACON_ACTIVATE, 0.6f, 1.6f);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** 6 s no nivel 1, +1,5 s por nivel (12 s no 5). */
    private static int duration(int spellLevel) {
        return 120 + 30 * (spellLevel - 1);
    }
}
