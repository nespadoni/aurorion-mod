package com.aurorion.magia.spell;

import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Transpositio — voce e o alvo trocam de lugar. Instantaneo.
 *
 * <p>A regra que faz a magia pedir cabeca: <b>a velocidade e a queda tambem trocam</b>. Voce parado,
 * ele caindo de um penhasco: depois da troca, ele esta onde voce estava — e quem esta caindo e voce,
 * com a distancia de queda que ele ja acumulava.
 *
 * <p>Aceita aliado de proposito (tirar alguem da lava, trazer um colega para dentro da muralha). Cada
 * um mantem para onde olhava.
 */
public final class TranspositioSpell extends AurorionSpell {
    public TranspositioSpell() {
        super("transpositio", SchoolRegistry.ENDER_RESOURCE, SpellRarity.RARE, 3, 20, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 50;
        this.manaCostPerLevel = 10;
        this.castTime = 0;
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.6f, 0.2f, 0.85f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.alcance", range(spellLevel)));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, range(spellLevel), true, target -> !Displacement.isImmune(target));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null && target.level() == entity.level()) swap(entity, target);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void swap(LivingEntity caster, LivingEntity target) {
        Vec3 casterPos = caster.position();
        Vec3 targetPos = target.position();
        Vec3 casterMotion = motionOf(caster);
        Vec3 targetMotion = motionOf(target);
        float casterFall = caster.fallDistance;
        float targetFall = target.fallDistance;

        caster.stopRiding();
        target.stopRiding();
        moveTo(caster, targetPos);
        moveTo(target, casterPos);
        launch(caster, targetMotion);
        launch(target, casterMotion);
        caster.fallDistance = targetFall;
        target.fallDistance = casterFall;

        sound(caster.level(), casterPos, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.8f);
        sound(caster.level(), targetPos, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.1f);
        sound(caster.level(), targetPos, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.7f, 0.6f);
        // pos = onde o conjurador estava (onde o alvo agora esta).
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.TRANSPOSITIO, 30, casterPos, 0);
    }

    /** Jogador pelo teleporte de conexao (senao o cliente discorda e volta); o resto direto. */
    private static void moveTo(LivingEntity entity, Vec3 pos) {
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(player.serverLevel(), pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
        } else {
            entity.teleportTo(pos.x, pos.y, pos.z);
        }
    }

    /** 24 blocos no nivel 1, +6 por nivel. */
    private static int range(int spellLevel) {
        return 24 + 6 * (spellLevel - 1);
    }
}
