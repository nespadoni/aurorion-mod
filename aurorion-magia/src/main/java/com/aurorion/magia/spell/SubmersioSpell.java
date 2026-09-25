package com.aurorion.magia.spell;

import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Submersio — <b>Afogamento</b>. A agua enche o pulmao de alguem que esta em terra firme.
 *
 * <p>O alvo continua de pe no meio da praça, seco, e comeca a se afogar: a barra de bolhas dele
 * esvazia, o corpo pesa, e quando o ar acaba vem o afogamento do vanilla, golpe a golpe, ate o tempo
 * da magia correr. E a magia de interrogatorio da escola da agua — tem relogio, tem panico e tem
 * saida, porque quem conjura pode simplesmente parar.
 *
 * <p>Nao e uma segunda mecanica de ar (ver {@link Drowning}): ela gasta o ar do proprio jogo, entao
 * Respiracao no elmo segura mais tempo, poçao de respirar embaixo d'agua salva, leite tira o efeito e
 * sair dele enche o ar de volta como sair de um mergulho.
 *
 * <p><b>Duas conjuracoes:</b> a segunda no mesmo alvo devolve o ar. Tirar o folego e devolve-lo
 * quando convier e a mesma logica da Voz Interdita — a magia serve para conduzir uma conversa, nao so
 * para matar.
 */
public final class SubmersioSpell extends AurorionSpell {
    private static final int RANGE = 18;

    public SubmersioSpell() {
        super("submersio", SchoolRegistry.ICE_RESOURCE, SpellRarity.UNCOMMON, 5, 25, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 35;
        this.manaCostPerLevel = 8;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.PLAYER_SPLASH_HIGH_SPEED);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.25f, 0.6f, 0.85f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.afogamento"),
                Component.translatable("ui.aurorion_magia.alternar"),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    /** Aliado incluido: devolver o ar tambem e conjurar nele. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, true, target -> true);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) {
                if (target.hasEffect(MagiaEffects.DROWNING)) {
                    breathe(target);
                } else {
                    Drowning.drown(target, entity, duration(spellLevel));
                    if (target instanceof ServerPlayer player) {
                        player.displayClientMessage(Component.translatable("aurorion_magia.afogando"), true);
                    }
                    MagiaNetwork.sendVisual(entity, target, SpellVisualPayload.Kind.SUBMERSIO, duration(spellLevel));
                }
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** Segunda conjuracao no mesmo alvo: o ar volta. O resto do estado sai com o efeito. */
    private static void breathe(LivingEntity target) {
        target.removeEffect(MagiaEffects.DROWNING);
        target.setAirSupply(target.getMaxAirSupply());
        if (target instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_magia.ar_devolvido"), true);
        }
        sound(target, SoundEvents.PLAYER_BREATH, 1.0f, 1.0f);
    }

    /** 6 s no nivel 1, +2 s por nivel (14 s no 5). */
    private static int duration(int spellLevel) {
        return 120 + 40 * (spellLevel - 1);
    }
}
