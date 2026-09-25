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
 * Turbo Ventorum — <b>Turbilhao</b>. A magia ofensiva da escola do vento.
 *
 * <p>Um funil de vento nasce a frente de quem conjurou e <b>anda em linha reta</b>, na direcao em que
 * voce estava olhando, acompanhando o relevo. Quem estiver no caminho e <b>puxado para o eixo</b> e
 * levantado do chao: o furacao nao empurra, ele recolhe. Enquanto a pessoa estiver dentro, leva um
 * golpe por segundo.
 *
 * <p>Ele e o oposto exato do {@link UndaMagnaSpell}: a onda abre espaço, o turbilhao junta gente. As
 * duas na mesma briga viram uma sequencia — puxar o grupo para o eixo e jogar todo mundo do penhasco.
 *
 * <p><b>Ele nao atravessa parede.</b> Ao bater num bloco solido na altura do peito, se desfaz ali. E o
 * que impede a magia de ser um aríete de invasao: numa muralha ela morre do lado de fora.
 */
public final class TurboVentorumSpell extends AurorionSpell {
    /** Distancia a frente em que o funil nasce: ele nunca começa em cima de quem conjurou. */
    private static final double SPAWN_AHEAD = 2.5;
    /** Blocos por tick. 0,3 = 6 blocos por segundo, um pouco mais rapido que correr. */
    private static final double SPEED = 0.3;

    public TurboVentorumSpell() {
        super("turbo_ventorum", SchoolRegistry.EVOCATION_RESOURCE, SpellRarity.EPIC, 5, 40, CastType.LONG);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 60;
        this.manaCostPerLevel = 12;
        this.castTime = 20;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.ENDER_DRAGON_FLAP);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.6f, 0.86f, 0.78f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.raio", radius(spellLevel)),
                Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.percurso", distance(spellLevel)),
                Component.translatable("ui.aurorion_magia.puxa_e_fere"));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return true;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 look = entity.getLookAngle().multiply(1, 0, 1);
            look = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
            Vec3 at = entity.position().add(look.scale(SPAWN_AHEAD));

            SpellZoneEntity.create(serverLevel, entity, SpellZoneEntity.Shape.STORM, at,
                    radius(spellLevel), height(spellLevel), duration(spellLevel), power(spellLevel),
                    look.scale(SPEED));
            sound(serverLevel, at, SoundEvents.ENDER_DRAGON_FLAP, 1.6f, 0.5f);
            sound(serverLevel, at, SoundEvents.WIND_CHARGE_THROW, 1.2f, 0.7f);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** Raio do funil: 2 blocos no nivel 1, +0,5 por nivel (4 no 5). */
    private static float radius(int spellLevel) {
        return 2 + 0.5f * (spellLevel - 1);
    }

    private static float height(int spellLevel) {
        return 4 + 0.5f * (spellLevel - 1);
    }

    /** 4 s no nivel 1, +1 s por nivel (8 s no 5). */
    private static int duration(int spellLevel) {
        return 80 + 20 * (spellLevel - 1);
    }

    /** Quanto ele percorre, para a descricao do pergaminho: velocidade x duracao. */
    private static int distance(int spellLevel) {
        return (int) Math.round(SPEED * duration(spellLevel));
    }

    /** Multiplicador do dano e do puxao: 2 por segundo no nivel 1, 3 no 5. */
    private static float power(int spellLevel) {
        return 1 + 0.125f * (spellLevel - 1);
    }
}
