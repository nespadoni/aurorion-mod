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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Lux Vorata — Devorar Luz. A magia que apaga o lugar.
 *
 * <p>A primeira versao so apagava vela e fogueira, e na pratica nao acontecia nada: o lugar
 * continuava iluminado por tocha, lanterna e lampada de mod, e ninguem sentia diferenca. Agora a
 * magia devora a luz em tres camadas, e a do meio e a que importa.
 *
 * <ol>
 *   <li><b>Apaga o que tem como apagar.</b> Qualquer bloco no raio que esteja aceso ({@code lit} e
 *       emitindo luz) se apaga: velas, bolos com vela, fogueiras, lampadas e lanternas de mod. Nada e
 *       quebrado nem sai do lugar — acende de novo com isqueiro ou redstone. Fica de fora o que
 *       estiver na tag {@code aurorion_magia:inapagavel} (fornalha e lampada de redstone, por
 *       padrao: apagar o forno do cozinheiro nao e magia de combate). A tag
 *       {@code aurorion_magia:apagavel} continua servindo para forcar blocos que nao usam
 *       {@code lit}.</li>
 *   <li><b>Cega quem esta dentro.</b> Todos no raio, menos quem conjurou e os aliados dele, recebem a
 *       <b>Escuridao</b> do vanilla pelo tempo da zona, mais um instante de Cegueira no baque. E a
 *       escuridao do Warden: a tela fecha em pulsos e o mundo some, com ou sem tocha na mao. E isto
 *       que faltava — a magia agora <i>faz</i> alguma coisa com quem esta ali.</li>
 *   <li><b>Deixa a zona escura.</b> Fogo aceso no chao se apaga, quem estiver pegando fogo apaga, e
 *       fica a neblina negra desenhada pelo cliente por alguns segundos.</li>
 * </ol>
 *
 * <p>Custo: uma varredura unica da esfera no momento da conjuracao (ate ~5 mil posicoes no nivel 5,
 * so leitura de estado em chunk ja carregado) e uma busca de entidades, com cooldown de 30 s. Nada
 * roda depois: a Escuridao e efeito de status, que o vanilla tica sozinho. Quem <b>entra</b> na zona
 * depois da conjuracao pega so a neblina, nao a Escuridao — a zona nao fica vigiando ninguem.
 */
public final class LuxVorataSpell extends AurorionSpell {
    /** Forca o apagamento de blocos que nao usam {@code lit} (lanterna magica de outro mod). */
    public static final TagKey<Block> EXTINGUISHABLE =
            TagKey.create(Registries.BLOCK, AurorionMagia.id("apagavel"));
    /** Nunca apaga: fornalha, lampada de redstone e o que a staff colocar aqui por datapack. */
    public static final TagKey<Block> PROTECTED =
            TagKey.create(Registries.BLOCK, AurorionMagia.id("inapagavel"));

    private static final int BLINDNESS_TICKS = 30;
    private static final int MAX_TARGETS = 32;

    public LuxVorataSpell() {
        super("lux_vorata", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.RARE, 5, 30, CastType.LONG);
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
                Component.translatable("ui.aurorion_magia.escuridao", Utils.timeFromTicks(darkness(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.poupa_aliados"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) devour(serverLevel, entity, spellLevel);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void devour(ServerLevel level, LivingEntity caster, int spellLevel) {
        int radius = radius(spellLevel);
        int duration = darkness(spellLevel);
        BlockPos center = caster.blockPosition();
        Player player = caster instanceof Player p ? p : null;
        int radiusSqr = radius * radius;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (pos.distSqr(center) > radiusSqr) continue;
            BlockState state = level.getBlockState(pos);
            if (!consumable(level, pos, state)) continue;
            // Protecao de spawn e afins: quem nao pode mexer ali, nao apaga ali.
            if (player != null && !level.mayInteract(player, pos)) continue;
            BlockPos at = pos.immutable();
            if (state.is(BlockTags.FIRE)) {
                level.removeBlock(at, false);
            } else {
                level.setBlock(at, state.setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL);
            }
        }

        // A escuridao em quem esta dentro. Aliado de time passa: a magia e do grupo que a lancou.
        for (LivingEntity victim : AreaCast.victims(level, caster, caster.position(), radius, MAX_TARGETS,
                target -> !caster.isAlliedTo(target))) {
            victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, duration, 0, false, false, true), caster);
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS, 0, false, false, true), caster);
            victim.clearFire();
        }

        Vec3 at = caster.position();
        sound(level, at, SoundEvents.FIRE_EXTINGUISH, 1.5f, 0.5f);
        sound(level, at, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 0.4f);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.LUX_VORATA, duration, at, radius);
    }

    /**
     * O que a magia devora: fogo no chao, o que a tag manda apagar, e qualquer bloco aceso que ainda
     * esteja emitindo luz. Nada que esteja na tag {@code inapagavel}.
     */
    private static boolean consumable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(PROTECTED)) return false;
        if (state.is(BlockTags.FIRE)) return true;
        if (!state.hasProperty(BlockStateProperties.LIT) || !state.getValue(BlockStateProperties.LIT)) return false;
        return state.is(EXTINGUISHABLE) || state.getLightEmission(level, pos) > 0;
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
