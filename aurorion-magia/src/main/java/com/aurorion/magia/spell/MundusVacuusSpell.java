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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Mundus Vacuus — Mundo Vazio. Herdeira do antigo Sequestro de Impulso: em vez de roubar o
 * movimento de alguem, rouba <b>o mundo inteiro</b> de quem olha.
 *
 * <p>O alvo continua exatamente onde estava, e todos continuam em volta dele — mas o cliente dele
 * para de desenhar qualquer vivo. Ele fica sozinho num mundo vazio: leva dano de ninguem, ouve
 * passos de ninguem, conversa com ninguem. Quem esta ali continua ali; e ele que deixou de ver.
 *
 * <p><b>Duas conjuracoes</b>, como o Vox Interdicta: a primeira poe o veu, a segunda no mesmo alvo o
 * tira. Nao e recast do Iron's — e so olhar de novo para quem esta sob o efeito.
 *
 * <p>Custo no servidor: um efeito de status e um pacote de visual. Tudo o que some, some no cliente
 * do proprio afetado ({@code MagiaClientEvents}), que ja recebe o efeito pela sincronizacao do
 * vanilla. Nenhum pacote por tick, nenhuma entidade escondida de verdade: o servidor nunca mente
 * sobre quem esta onde.
 */
public final class MundusVacuusSpell extends AurorionSpell {
    private static final int RANGE = 20;

    public MundusVacuusSpell() {
        super("mundus_vacuus", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.EPIC, 5, 35, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 45;
        this.manaCostPerLevel = 10;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.PORTAL_TRIGGER);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.05f, 0.03f, 0.12f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.alternar"),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    /** So jogador: mob nao tem tela para esvaziar. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, true, target -> target instanceof Player);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel
                && target(serverLevel, entity, playerMagicData) instanceof ServerPlayer target) {
            if (target.hasEffect(MagiaEffects.SOLITARY)) {
                restore(target);
            } else {
                empty(entity, target, duration(spellLevel));
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void empty(LivingEntity caster, ServerPlayer target, int duration) {
        target.addEffect(new MobEffectInstance(MagiaEffects.SOLITARY, duration, 0, false, false, true), caster);
        target.displayClientMessage(Component.translatable("aurorion_magia.mundo_vazio"), true);
        sound(target, SoundEvents.PORTAL_TRIGGER, 0.7f, 0.5f);
        sound(target, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5f, 0.4f);
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.MUNDUS_VACUUS, duration);
    }

    /** Segunda conjuracao no mesmo alvo: o mundo volta. */
    private static void restore(ServerPlayer target) {
        target.removeEffect(MagiaEffects.SOLITARY);
        target.displayClientMessage(Component.translatable("aurorion_magia.mundo_volta"), true);
        sound(target, SoundEvents.BEACON_ACTIVATE, 0.6f, 1.4f);
    }

    /** 10 s no nivel 1, +5 s por nivel (30 s no 5). */
    private static int duration(int spellLevel) {
        return 200 + 100 * (spellLevel - 1);
    }
}
