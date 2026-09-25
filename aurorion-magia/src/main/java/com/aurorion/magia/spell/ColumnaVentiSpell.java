package com.aurorion.magia.spell;

import com.aurorion.magia.entity.SpellZoneEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Columna Venti — <b>Coluna de Vento</b>. A magia de exploracao da escola do vento.
 *
 * <p>Uma corrente ascendente de 3x3 e {@value #HEIGHT} blocos de altura fica em pe no chao por alguns
 * segundos. Quem entrar nela sobe, e ganha queda lenta pelo tempo que sobrar da coluna: dar a volta
 * numa muralha, subir um penhasco, tirar o grupo de um buraco, descer de uma torre sem morrer.
 *
 * <p><b>Ela nao escolhe lado.</b> Quem conjurou, os aliados e quem esta perseguindo sobem igual — e
 * ai esta a graça: usada na hora errada, ela da ao inimigo a mesma altura que deu a voce. A unica
 * coisa que ela nunca faz e machucar.
 *
 * <p>Nasce onde voce esta olhando, ate {@value #RANGE} blocos, em cima do primeiro chao firme — e por
 * isso ela tambem serve para levantar outra pessoa, e nao so voce.
 */
public final class ColumnaVentiSpell extends AurorionSpell {
    private static final int RANGE = 12;
    /** 3x3 do pedido: raio de 1,5 a partir do centro do bloco. */
    private static final float RADIUS = 1.5f;
    private static final float HEIGHT = 6f;

    public ColumnaVentiSpell() {
        super("columna_venti", SchoolRegistry.EVOCATION_RESOURCE, SpellRarity.COMMON, 5, 14, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 20;
        this.manaCostPerLevel = 4;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.BREEZE_IDLE_AIR);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.85f, 1.0f, 0.92f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.coluna_altura", (int) HEIGHT),
                Component.translatable("ui.aurorion_magia.queda_lenta"),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return true;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 at = ground(serverLevel, entity);
            SpellZoneEntity.create(serverLevel, entity, SpellZoneEntity.Shape.COLUMN, at,
                    RADIUS, HEIGHT, duration(spellLevel), power(spellLevel), Vec3.ZERO);
            sound(serverLevel, at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.0f, 1.3f);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /**
     * Onde a coluna nasce: o bloco que a mira acertou, ou o chao sob o ponto olhado quando a mira
     * pegou so o ar. Nunca dentro de parede — {@code ClipContext.Block.COLLIDER} para no primeiro
     * solido, entao a coluna encosta nele em vez de atravessa-lo.
     */
    private static Vec3 ground(ServerLevel level, LivingEntity caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 far = eye.add(caster.getLookAngle().scale(RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        Vec3 at = hit.getType() == HitResult.Type.BLOCK
                ? Vec3.atBottomCenterOf(hit.getBlockPos().above())
                : far;

        BlockPos pos = BlockPos.containing(at);
        for (int step = 0; step < 8 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); step++) {
            pos = pos.below();
        }
        return new Vec3(at.x, pos.getY(), at.z);
    }

    /** 5 s no nivel 1, +1 s por nivel (9 s no 5). */
    private static int duration(int spellLevel) {
        return 100 + 20 * (spellLevel - 1);
    }

    /** O impulso cresce de leve com o dominio: 6 blocos no 1, um pouco mais no 5. */
    private static float power(int spellLevel) {
        return 1 + 0.06f * (spellLevel - 1);
    }
}
