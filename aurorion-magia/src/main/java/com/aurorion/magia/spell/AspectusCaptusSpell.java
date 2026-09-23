package com.aurorion.magia.spell;

import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
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
 * Aspectus Captus — Olhar Cativo. "Olhe para mim quando eu falar com voce."
 *
 * <p>Por 2 a 4 segundos, a camera e a cabeca do alvo ficam presas em quem conjurou: ele anda devagar
 * e fala normalmente, mas nao consegue desviar o rosto. Para quem esta preso, a tela fecha num tunel
 * escuro em volta de quem o prende.
 *
 * <p>Custo: nenhum no servidor alem do efeito. A camera e virada pelo cliente do alvo, que ja recebe o
 * visual da magia (com o id de quem conjurou) no mesmo pacote que todos recebem.
 */
public final class AspectusCaptusSpell extends AurorionSpell {
    private static final int RANGE = 16;

    public AspectusCaptusSpell() {
        super("aspectus_captus", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.UNCOMMON, 5, 20, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 25;
        this.manaCostPerLevel = 5;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.ENDER_EYE_DEATH);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.42f, 0.18f, 0.56f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, false, target -> true);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) {
                int duration = duration(spellLevel);
                Gaze.capture(target, entity, duration);
                sound(target, SoundEvents.WARDEN_NEARBY_CLOSEST, 0.8f, 1.3f);
                MagiaNetwork.sendVisual(entity, target, SpellVisualPayload.Kind.ASPECTUS_CAPTUS, duration);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** 2 s no nivel 1, +0,5 s por nivel (4 s no 5). */
    private static int duration(int spellLevel) {
        return 40 + 10 * (spellLevel - 1);
    }
}
