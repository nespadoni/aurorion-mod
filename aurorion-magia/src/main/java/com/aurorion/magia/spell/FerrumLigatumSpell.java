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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Ferrum Ligatum — Ferro Vinculado. A armadura e o que esta na mao secundaria ficam presos no corpo:
 * nao sai, nao troca, nao cai com Q. "Agora precisa continuar lutando com ela."
 *
 * <p>So em jogador (mob nao troca de armadura). A regra esta em {@link IronBinding}.
 */
public final class FerrumLigatumSpell extends AurorionSpell {
    private static final int RANGE = 12;

    public FerrumLigatumSpell() {
        super("ferrum_ligatum", SchoolRegistry.BLOOD_RESOURCE, SpellRarity.RARE, 3, 40, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 40;
        this.manaCostPerLevel = 10;
        this.castTime = 0;
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.55f, 0.57f, 0.62f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, false, target -> target instanceof Player);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel
                && target(serverLevel, entity, playerMagicData) instanceof ServerPlayer target) {
            int duration = duration(spellLevel);
            IronBinding.bind(target);
            target.addEffect(new MobEffectInstance(MagiaEffects.IRON_BOUND, duration, 0, false, false, true), entity);
            target.displayClientMessage(Component.translatable("aurorion_magia.ferro_vinculado"), true);
            sound(target, SoundEvents.CHAIN_PLACE, 1.2f, 0.5f);
            sound(target, SoundEvents.ANVIL_USE, 0.5f, 0.6f);
            MagiaNetwork.sendVisual(entity, target, SpellVisualPayload.Kind.FERRUM_LIGATUM, duration);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** 10 s no nivel 1, +5 s por nivel. */
    private static int duration(int spellLevel) {
        return 200 + 100 * (spellLevel - 1);
    }
}
