package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Lux Vorata — Devorar Luz. Nao cega ninguem: escurece o lugar.
 *
 * <ol>
 *   <li>Apaga toda fonte de luz <b>acendivel</b> no raio: velas, bolos com vela, fogueiras e o que
 *       mais estiver na tag {@code aurorion_magia:apagavel} (lanternas magicas de outros mods, por
 *       datapack). Nada e quebrado nem sai do lugar — acende de novo com isqueiro.</li>
 *   <li>Apaga quem esta pegando fogo no raio.</li>
 *   <li>Deixa uma zona de escuridao por alguns segundos: neblina negra para quem estiver dentro,
 *       desenhada pelo cliente.</li>
 * </ol>
 *
 * <p>Tocha comum e lanterna vanilla nao tem estado "apagada" — so daria para apaga-las quebrando, e
 * isso aqui nao e magia de griefing. A zona de escuridao e o que cobre esse caso.
 *
 * <p>Custo: uma varredura unica da esfera no momento da conjuracao (ate ~5 mil posicoes no nivel 5,
 * so leitura de estado em chunk ja carregado), com cooldown de 30 s. Nada roda depois.
 */
public final class LuxVorataSpell extends AurorionSpell {
    public static final TagKey<Block> EXTINGUISHABLE =
            TagKey.create(Registries.BLOCK, AurorionMagia.id("apagavel"));

    public LuxVorataSpell() {
        super("lux_vorata", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.UNCOMMON, 5, 30, CastType.LONG);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 40;
        this.manaCostPerLevel = 8;
        this.castTime = 20;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.BEACON_DEACTIVATE);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.18f, 0.08f, 0.25f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.raio", radius(spellLevel)),
                Component.translatable("ui.aurorion_magia.escuridao", Utils.timeFromTicks(darkness(spellLevel), 1)));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) devour(serverLevel, entity, spellLevel);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void devour(ServerLevel level, LivingEntity caster, int spellLevel) {
        int radius = radius(spellLevel);
        BlockPos center = caster.blockPosition();
        Player player = caster instanceof Player p ? p : null;
        int radiusSqr = radius * radius;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (pos.distSqr(center) > radiusSqr) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.is(EXTINGUISHABLE) || !state.hasProperty(BlockStateProperties.LIT)
                    || !state.getValue(BlockStateProperties.LIT)) continue;
            // Protecao de spawn e afins: quem nao pode mexer ali, nao apaga ali.
            if (player != null && !level.mayInteract(player, pos)) continue;
            level.setBlock(pos.immutable(), state.setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL);
        }

        for (LivingEntity burning : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center).inflate(radius), LivingEntity::isOnFire)) {
            burning.clearFire();
        }

        Vec3 at = caster.position();
        sound(level, at, SoundEvents.FIRE_EXTINGUISH, 1.5f, 0.5f);
        sound(level, at, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 0.4f);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.LUX_VORATA, darkness(spellLevel), at, radius);
    }

    /** 6 blocos no nivel 1, +1 por nivel. */
    private static int radius(int spellLevel) {
        return 5 + spellLevel;
    }

    /** Escuridao de 8 s no nivel 1, +2 s por nivel. */
    private static int darkness(int spellLevel) {
        return 160 + 40 * (spellLevel - 1);
    }
}
