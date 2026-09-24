package com.aurorion.magia.spell;

import com.aurorion.magia.compat.VoiceMute;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Vox Interdicta — Voz Interdita. Nao machuca: toma a voz.
 *
 * <p>Pelo tempo do efeito, o microfone do alvo no Simple Voice Chat nao chega a ninguem, o chat de
 * texto e recusado e nenhuma magia sai (toda conjuracao e verbal). Ele ainda anda, bate e foge.
 * "Conte para eles o que viu." — e a pessoa abre a boca, e nada.
 *
 * <p><b>Duas conjuracoes</b>: a primeira tira a voz, a segunda no mesmo alvo devolve. Interrogatorio
 * e isso — tirar a palavra e devolve-la quando convier, sem esperar o tempo correr.
 *
 * <p>So em jogador: mob nao tem voz para tomar.
 */
public final class VoxInterdictaSpell extends AurorionSpell {
    private static final int RANGE = 16;

    public VoxInterdictaSpell() {
        super("vox_interdicta", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.RARE, 5, 30, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 40;
        this.manaCostPerLevel = 8;
        this.castTime = 0;
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.25f, 0.12f, 0.32f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.silencio", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.alternar"));
    }

    /** Aliado incluido: devolver a voz tambem e conjurar nele. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, true, target -> target instanceof Player);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel
                && target(serverLevel, entity, playerMagicData) instanceof ServerPlayer target) {
            if (target.hasEffect(MagiaEffects.SILENCED)) {
                restore(target);
            } else {
                silence(entity, target, duration(spellLevel));
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** Segunda conjuracao no mesmo alvo: pode falar. O microfone volta pelo fim do efeito. */
    private static void restore(ServerPlayer target) {
        target.removeEffect(MagiaEffects.SILENCED);
        target.displayClientMessage(Component.translatable("aurorion_magia.voz_devolvida"), true);
        sound(target, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
    }

    private static void silence(LivingEntity caster, ServerPlayer target, int duration) {
        target.addEffect(new MobEffectInstance(MagiaEffects.SILENCED, duration, 0, false, false, true), caster);
        VoiceMute.mute(target.getUUID(), duration * 50L);
        if (MagicData.getPlayerMagicData(target).isCasting()) Utils.serverSideCancelCast(target);
        target.displayClientMessage(Component.translatable("aurorion_magia.sem_voz"), true);
        sound(target, SoundEvents.SCULK_CLICKING, 1.0f, 0.5f);
        sound(target, SoundEvents.WARDEN_HEARTBEAT, 0.8f, 1.4f);
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.VOX_INTERDICTA, duration);
    }

    /** 5 s no nivel 1, +1,25 s por nivel (10 s no 5). */
    private static int duration(int spellLevel) {
        return 100 + 25 * (spellLevel - 1);
    }
}
