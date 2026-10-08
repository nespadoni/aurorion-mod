package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.registry.MagiaSpells;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Campus Staticus — Campo Estatico. A ultimate do Blitzcrank, nas duas metades.
 *
 * <ul>
 *   <li><b>Ativo</b>: uma descarga em {@value #RADIUS} blocos arranca os "escudos" dos inimigos (os
 *       coracoes de absorcao, de maca dourada a magia de escudo), fere e silencia por um instante — sem
 *       magia, sem chat, sem voz.</li>
 *   <li><b>Passivo</b>: por {@value #FIELD_SECONDS} segundos depois de conjurar, cada golpe corpo a
 *       corpo de quem conjurou marca o alvo. Um segundo depois a marca estoura em choque.</li>
 * </ul>
 *
 * <p>Custo: uma busca no raio ao conjurar. A marca e reacao ao golpe e o estouro e o fim do efeito,
 * entao nada roda por tick.
 */
public final class CampusStaticusSpell extends AurorionSpell {
    private static final int RADIUS = 6;
    private static final int MAX_TARGETS = 16;
    private static final int SILENCE_TICKS = 60;
    private static final int FIELD_SECONDS = 20;
    private static final int MARK_TICKS = 40;
    private static final String ZAP_KEY = AurorionMagia.MOD_ID + ":campo_estatico";
    private static final String MARK_KEY = AurorionMagia.MOD_ID + ":marca_estatica";

    public CampusStaticusSpell() {
        super("campus_staticus", SchoolRegistry.LIGHTNING_RESOURCE, SpellRarity.EPIC, 3, 40, CastType.INSTANT);
        this.baseSpellPower = 6;
        this.spellPowerPerLevel = 2;
        this.baseManaCost = 60;
        this.manaCostPerLevel = 15;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.BEACON_DEACTIVATE);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.4f, 0.75f, 1.0f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(damagePower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.raio", RADIUS),
                Component.translatable("ui.aurorion_magia.silencio", Utils.timeFromTicks(SILENCE_TICKS, 1)),
                Component.translatable("ui.aurorion_magia.campo_golpes", FIELD_SECONDS,
                        Utils.stringTruncation(zap(spellLevel, caster), 1)));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) discharge(serverLevel, entity, spellLevel);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void discharge(ServerLevel level, LivingEntity caster, int spellLevel) {
        caster.addEffect(new MobEffectInstance(MagiaEffects.STATIC_FIELD, FIELD_SECONDS * 20, spellLevel - 1, false, false, true));
        caster.getPersistentData().putFloat(ZAP_KEY, zap(spellLevel, caster));

        float damage = damagePower(spellLevel, caster);
        for (LivingEntity victim : Hits.around(level, caster, caster.position(), RADIUS, MAX_TARGETS, t -> Hits.enemy(caster, t))) {
            // Escudo e silencio so em quem a descarga de fato alcancou.
            if (!FriendlyFire.applyDamage(victim, damage, getDamageSource(caster)) || !victim.isAlive()) continue;
            victim.removeEffect(MobEffects.ABSORPTION);
            victim.setAbsorptionAmount(0);
            silence(victim, caster);
            MagiaNetwork.sendVisual(caster, victim, SpellVisualPayload.Kind.STATIC_MARK, 8);
        }

        sound(caster, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4f, 1.3f);
        sound(caster, SoundEvents.BEACON_POWER_SELECT, 1.0f, 2.0f);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.CAMPUS_STATICUS, 14, caster.position(), RADIUS);
    }

    private static void silence(LivingEntity victim, LivingEntity caster) {
        victim.addEffect(new MobEffectInstance(MagiaEffects.SILENCED, SILENCE_TICKS, 0, false, false, true), caster);
        ControlSpells.syncVoice(victim, null);
        if (victim instanceof ServerPlayer player && MagicData.getPlayerMagicData(player).isCasting()) {
            Utils.serverSideCancelCast(player);
        }
    }

    /**
     * Golpe corpo a corpo de quem esta com o campo ligado: marca o alvo. Chamado pelo evento de dano.
     * Alvo ja marcado nao renova — o choque sai no tempo da primeira marca.
     */
    public static void onMelee(LivingEntity attacker, LivingEntity victim) {
        if (!attacker.hasEffect(MagiaEffects.STATIC_FIELD) || victim.hasEffect(MagiaEffects.STATIC_MARK)
                || !victim.isAlive()) return;
        CompoundTag mark = new CompoundTag();
        mark.putUUID("By", attacker.getUUID());
        mark.putFloat("Damage", attacker.getPersistentData().contains(ZAP_KEY) ? attacker.getPersistentData().getFloat(ZAP_KEY) : 4);
        victim.getPersistentData().put(MARK_KEY, mark);
        victim.addEffect(new MobEffectInstance(MagiaEffects.STATIC_MARK, MARK_TICKS, 0, false, false, true), attacker);
        MagiaNetwork.sendVisual(attacker, victim, SpellVisualPayload.Kind.STATIC_MARK, MARK_TICKS);
    }

    /** A marca venceu: o choque. */
    public static void pop(LivingEntity victim) {
        CompoundTag data = victim.getPersistentData();
        if (!data.contains(MARK_KEY, Tag.TAG_COMPOUND) || !(victim.level() instanceof ServerLevel level)) return;
        CompoundTag mark = data.getCompound(MARK_KEY);
        data.remove(MARK_KEY);
        if (!mark.hasUUID("By") || !(level.getEntity(mark.getUUID("By")) instanceof LivingEntity attacker)) return;
        FriendlyFire.applyDamage(victim, mark.getFloat("Damage"), MagiaSpells.CAMPUS_STATICUS.get().getDamageSource(attacker));
        sound(victim, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 1.8f);
        MagiaNetwork.sendVisual(attacker, victim, SpellVisualPayload.Kind.STATIC_MARK, 6);
    }

    /** A marca saiu sem estourar (leite): o dono e o dano guardados saem junto. */
    public static void clearMark(LivingEntity victim) {
        victim.getPersistentData().remove(MARK_KEY);
    }

    /** O choque da marca: 60% do dano da descarga. */
    private float zap(int spellLevel, @Nullable LivingEntity caster) {
        return damagePower(spellLevel, caster) * 0.6f;
    }
}
