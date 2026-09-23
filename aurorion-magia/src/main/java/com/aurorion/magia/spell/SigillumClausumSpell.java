package com.aurorion.magia.spell;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ICastData;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Sigillum Clausum — Lacre Profano. Olhe para uma porta, alcapao, portao, bau ou barril e sele.
 *
 * <ul>
 *   <li>Nivel 1: so voce abre, por 1 minuto.</li>
 *   <li>Nivel 2: voce e o seu time, por 3 minutos.</li>
 *   <li>Nivel 3: voce e o seu time, por 9 minutos.</li>
 * </ul>
 *
 * <p>Conjurar num lacre seu desfaz o lacre. Conjurar no lacre de outra pessoa, com nivel igual ou
 * maior, <b>quebra</b>; com nivel menor, o lacre resiste. "Ninguem toca em nada." — porta selada. A
 * regra dos lacres esta em {@link Seals}.
 */
public final class SigillumClausumSpell extends AurorionSpell {
    private static final int RANGE = 8;

    public SigillumClausumSpell() {
        super("sigillum_clausum", SchoolRegistry.ENDER_RESOURCE, SpellRarity.RARE, 3, 15, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 30;
        this.manaCostPerLevel = 10;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.RESPAWN_ANCHOR_CHARGE);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.55f, 0.25f, 0.9f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable(spellLevel >= 2 ? "ui.aurorion_magia.lacre_time" : "ui.aurorion_magia.lacre_dono"));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (!(level instanceof ServerLevel serverLevel)) return true;
        if (playerMagicData == null) return false;
        BlockHitResult hit = Utils.getTargetBlock(level, entity, ClipContext.Fluid.NONE, RANGE);
        if (hit.getType() == HitResult.Type.BLOCK && Seals.isSealable(serverLevel, hit.getBlockPos())) {
            playerMagicData.setAdditionalCastData(new BlockCastData(hit));
            return true;
        }
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_magia.nada_a_lacrar"), true);
        }
        return false;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel
                && playerMagicData != null && playerMagicData.getAdditionalCastData() instanceof BlockCastData cast) {
            BlockHitResult hit = cast.hit();
            Seals.Outcome outcome = Seals.cast(serverLevel, entity, hit.getBlockPos(), hit.getDirection(),
                    hit.getLocation(), spellLevel, duration(spellLevel));
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("aurorion_magia.lacre." + outcome.name().toLowerCase()), true);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** 1, 3 e 9 minutos. */
    private static int duration(int spellLevel) {
        return 1200 * (int) Math.pow(3, spellLevel - 1);
    }

    /** O bloco mirado, guardado entre a mira e o efeito. */
    private record BlockCastData(BlockHitResult hit) implements ICastData {
        @Override
        public void reset() {
        }
    }
}
