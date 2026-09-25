package com.aurorion.magia.spell;

import com.aurorion.magia.registry.MagiaEffects;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Carcer Aquae — <b>Carcere de Agua</b>. Uma esfera de agua se fecha em volta do alvo e o suspende.
 *
 * <p>Ele boia dentro dela, parado no ponto em que foi pego: nao anda, nao pula, nao foge, nao e
 * levado por ninguem. Continua respirando (afogar e a outra magia da escola) e continua vendo e
 * ouvindo tudo. E a magia de <b>captura</b> da agua: prender um so, inteiro, para conversar.
 *
 * <p><b>A bolha e quebravel de fora.</b> Qualquer golpe de qualquer pessoa a estoura, e quem estava
 * dentro sai solto — o dano do golpe some na agua. E o que separa esta magia de uma prisao: capturar
 * alguem no meio da praça sem ninguem poder tira-lo de la vira impasse; com resgate, vira cena. Quem
 * conjura de novo no mesmo alvo tambem a desfaz.
 *
 * <p>Chefes ficam de fora ({@code imune_deslocamento}), como em toda magia que tira alguem do chao.
 */
public final class CarcerAquaeSpell extends AurorionSpell {
    private static final int RANGE = 16;

    public CarcerAquaeSpell() {
        super("carcer_aquae", SchoolRegistry.ICE_RESOURCE, SpellRarity.RARE, 5, 35, CastType.LONG);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 50;
        this.manaCostPerLevel = 10;
        this.castTime = 20;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.2f, 0.55f, 0.8f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.carcer_resgate"),
                Component.translatable("ui.aurorion_magia.alternar"),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    /** Aliado incluido: tirar alguem de uma queda ou de uma lava tambem e prende-lo numa bolha. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, true, target -> !Displacement.isImmune(target));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) {
                if (target.hasEffect(MagiaEffects.CAGED)) {
                    WaterCage.burst(target);
                } else {
                    WaterCage.cage(target, entity, duration(spellLevel));
                }
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** 8 s no nivel 1, +3 s por nivel (20 s no 5). */
    private static int duration(int spellLevel) {
        return 160 + 60 * (spellLevel - 1);
    }
}
