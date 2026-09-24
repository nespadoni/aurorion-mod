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
 * Aspectus Captus — Olhar Cativo. "Olhe para mim quando eu falar com voce."
 *
 * <p>Por alguns segundos, a camera e a cabeca do alvo ficam presas em quem conjurou: ele anda devagar
 * e fala normalmente, mas nao consegue desviar o rosto. Para quem esta preso, a tela fecha num tunel
 * escuro em volta de quem o prende.
 *
 * <p><b>Agachado, em area</b> ({@link AreaCast}): "olhem para mim, todos voces." Um olho enorme se
 * abre sobre quem conjura e <b>a praca inteira</b> vira o rosto para ele — ate {@value #MAX_TARGETS}
 * pessoas e criaturas num raio que chega a {@code 30} blocos. E a magia de discurso, de julgamento,
 * de entrada de vilao: ninguem consegue olhar para outro lado enquanto voce fala.
 *
 * <p>Custo: nenhum no servidor alem dos efeitos. A camera e virada pelo cliente de cada alvo, que ja
 * recebe o visual da magia (com o id de quem conjurou) no mesmo pacote que todos recebem.
 */
public final class AspectusCaptusSpell extends AurorionSpell {
    private static final int RANGE = 16;
    private static final int MAX_TARGETS = 24;

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
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.agachado_area", radius(spellLevel)));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (!AreaCast.wide(entity)) return aim(level, entity, playerMagicData, RANGE, false, target -> true);
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
            int duration = duration(spellLevel);
            if (AreaCast.wide(entity)) {
                for (LivingEntity victim : area(serverLevel, entity, spellLevel)) {
                    capture(entity, victim, duration);
                }
                Vec3 at = entity.position();
                sound(serverLevel, at, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 1.2f);
                MagiaNetwork.sendVisualAt(serverLevel, entity, SpellVisualPayload.Kind.ASPECTUS_AREA,
                        duration, at, radius(spellLevel));
            } else {
                LivingEntity target = target(serverLevel, entity, playerMagicData);
                if (target != null) capture(entity, target, duration);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void capture(LivingEntity caster, LivingEntity target, int duration) {
        Gaze.capture(target, caster, duration);
        sound(target, SoundEvents.WARDEN_NEARBY_CLOSEST, 0.8f, 1.3f);
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.ASPECTUS_CAPTUS, duration);
    }

    private List<LivingEntity> area(ServerLevel level, LivingEntity caster, int spellLevel) {
        return AreaCast.victims(level, caster, caster.position(), radius(spellLevel), MAX_TARGETS, target -> true);
    }

    /** 2 s no nivel 1, +0,5 s por nivel (4 s no 5). */
    private static int duration(int spellLevel) {
        return 40 + 10 * (spellLevel - 1);
    }

    /** Raio do olhar coletivo: 14 blocos no nivel 1, +4 por nivel (30 no 5). */
    private static int radius(int spellLevel) {
        return 14 + 4 * (spellLevel - 1);
    }
}
