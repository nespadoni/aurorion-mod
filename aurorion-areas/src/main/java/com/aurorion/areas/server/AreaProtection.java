package com.aurorion.areas.server;

import com.aurorion.areas.config.AreasConfig;
import com.aurorion.areas.data.AreaData;
import com.aurorion.areas.rules.Protection;
import com.aurorion.areas.rules.ProtectionRule;
import com.aurorion.core.house.HouseGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.PistonEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Protecao de blocos das areas, consultada na posicao do <b>bloco</b> (nao na do jogador).
 *
 * <p>Os listeners ficam no {@link AreaEvents}; aqui mora a politica. Toda consulta comeca por
 * {@link #guarded}: config + um {@code Set#contains} por dimensao. Num mundo sem area de casa e sem
 * regra de protecao negada, nada abaixo disso roda — importa porque o fluido pergunta isto a cada
 * passo de agua e lava do servidor.
 *
 * <h2>Quem passa</h2>
 *
 * <p>So a staff com permissao 2 (qualquer modo de jogo), e nunca fake player. Diferente da barreira
 * de casa: aqui e ferramenta de moderacao — a staff precisa consertar um grief em sobrevivencia — e
 * fake player sem bypass e o que impede uma maquina (deployer, quarry, turtle) posta do lado de fora
 * de quebrar a parede de dentro. O fake player responde pela UUID dele, que nao e membro de casa.
 */
public final class AreaProtection {
    /** Alcance do raio do balde. Folgado acima dos 4,5 do vanilla: errar para o lado de negar e barato. */
    private static final double BUCKET_REACH = 6.0;
    /** Profundidade maxima de "dono do dono" (flecha de esqueleto montado, lobo de alguem...). */
    private static final int OWNER_DEPTH = 4;

    private AreaProtection() {
    }

    public static boolean bypass(ServerPlayer player) {
        return !(player instanceof FakePlayer) && player.hasPermissions(2);
    }

    /** Ha qualquer protecao de bloco nesta dimensao? Falso = nada a fazer. */
    public static boolean guarded(ServerLevel level) {
        return AreasConfig.ENABLED.get() && AreaData.get(level.getServer()).guarded(level.dimension().location());
    }

    /** Este jogador pode fazer {@code rule} neste bloco? */
    public static boolean mayAct(ServerLevel level, ServerPlayer player, BlockPos pos, ProtectionRule rule) {
        if (!guarded(level) || bypass(player)) return true;
        return !protection(level, pos, rule, player.getUUID()).denied();
    }

    /**
     * Clique direito num bloco protegido por {@code interagir}: inventarios (qualquer block entity) e
     * os blocos da tag {@link AreaTags#PROTECTED_INTERACTIONS}. Bloco comum (terra, mesa de trabalho)
     * nao passa por aqui — colocar bloco em cima dele e assunto do {@code construir}.
     */
    public static boolean deniesUse(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!guarded(level) || bypass(player)) return false;
        var state = level.getBlockState(pos);
        if (!state.hasBlockEntity() && !state.is(AreaTags.PROTECTED_INTERACTIONS)) return false;
        return protection(level, pos, ProtectionRule.INTERACT, player.getUUID()).denied();
    }

    /**
     * Balde cheio ou vazio. O balde nao passa pelo {@code EntityPlaceEvent} (ele usa {@code use}, nao
     * {@code useOn}), entao o raio e refeito aqui: nega se o bloco mirado <em>ou</em> o vizinho da face
     * mirada estiverem protegidos — um e onde o fluido sai, o outro e de onde ele e recolhido.
     */
    public static boolean deniesBucket(ServerLevel level, ServerPlayer player) {
        if (!guarded(level) || bypass(player)) return false;
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(BUCKET_REACH));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = hit.getBlockPos();
        return protection(level, pos, ProtectionRule.BUILD, player.getUUID()).denied()
                || protection(level, pos.relative(hit.getDirection()), ProtectionRule.BUILD, player.getUUID()).denied();
    }

    /** Moldura, quadro, suporte de armadura: contam como bloco para {@code construir}/{@code interagir}. */
    public static boolean decoration(Entity entity) {
        return entity instanceof HangingEntity || entity instanceof ArmorStand;
    }

    /**
     * Tira da explosao os blocos (e as decoracoes) protegidos por {@code explosao}.
     *
     * <p>Quem responde pela explosao e o jogador por tras dela (dono da TNT, de quem acendeu o creeper,
     * da bola de fogo). Assim a TNT de um membro dentro da propria casa continua funcionando, e a TNT de
     * fora — ou um creeper, que nao tem dono — nao derruba parede de casa nenhuma.
     */
    public static void filterExplosion(ServerLevel level, Explosion explosion, List<BlockPos> blocks, List<Entity> entities) {
        Entity source = explosion.getIndirectSourceEntity();
        if (source == null) source = explosion.getDirectSourceEntity();
        UUID actor = playerBehind(source);
        if (actor != null) {
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(actor);
            if (online != null && bypass(online)) return;
        }
        // Uma explosao de TNT mexe em centenas de blocos: a casa do responsavel e buscada uma vez so.
        UUID who = actor;
        boolean houses = HouseGate.installed();
        ResourceLocation house = houses && who != null ? HouseGate.of(level.getServer(), who) : null;
        blocks.removeIf(pos -> protection(level, pos, ProtectionRule.EXPLOSION, who, house, houses).denied());
        entities.removeIf(entity -> decoration(entity)
                && protection(level, entity.blockPosition(), ProtectionRule.EXPLOSION, who, house, houses).denied());
    }

    /**
     * Fluido (ou o fogo que a lava acende) saindo de {@code from} para {@code to} atravessa para dentro
     * de uma protecao de {@code fluido}? Agua escorrendo dentro da propria area nao conta.
     */
    public static boolean fluidCrosses(ServerLevel level, BlockPos from, BlockPos to) {
        if (!level.getServer().isSameThread() || !guarded(level)) return false;
        return Protection.crosses(protection(level, from, ProtectionRule.FLUID, null),
                protection(level, to, ProtectionRule.FLUID, null));
    }

    /**
     * Pistao mexendo em bloco de uma protecao de {@code pistao} da qual ele nao faz parte: empurrando
     * para dentro, puxando para fora (pistao grudento) ou quebrando o que esta no caminho.
     */
    public static boolean pistonCrosses(ServerLevel level, PistonEvent.Pre event) {
        if (!guarded(level)) return false;
        BlockPos piston = event.getPos();
        boolean extending = event.getPistonMoveType().isExtend;
        Protection home = protection(level, piston, ProtectionRule.PISTON, null);
        Direction facing = event.getDirection();
        if (extending && Protection.crosses(home, protection(level, piston.relative(facing), ProtectionRule.PISTON, null))) {
            return true;
        }
        // Pistao comum recolhendo nao puxa nada; so o grudento traz bloco de volta.
        if (!extending && !level.getBlockState(piston).is(Blocks.STICKY_PISTON)) return false;
        PistonStructureResolver structure = event.getStructureHelper();
        if (structure == null || !structure.resolve()) return false;
        Direction push = structure.getPushDirection();
        for (BlockPos moved : structure.getToPush()) {
            if (Protection.crosses(home, protection(level, moved, ProtectionRule.PISTON, null))
                    || Protection.crosses(home, protection(level, moved.relative(push), ProtectionRule.PISTON, null))) {
                return true;
            }
        }
        for (BlockPos broken : structure.getToDestroy()) {
            if (Protection.crosses(home, protection(level, broken, ProtectionRule.PISTON, null))) return true;
        }
        return false;
    }

    /**
     * O jogador por tras de uma entidade: ele mesmo, o dono do bicho, quem atirou o projetil, quem
     * acendeu a TNT, quem invocou o raio do tridente. {@code null} quando nao ha jogador nenhum.
     *
     * <p>Devolve UUID, e nao a entidade, porque o dono de um lobo pode estar offline — e o lobo dele
     * continua sem poder morder alguem numa area sem PvP.
     */
    @Nullable
    public static UUID playerBehind(@Nullable Entity entity) {
        for (int depth = 0; entity != null && depth < OWNER_DEPTH; depth++) {
            if (entity instanceof Player player) return player.getUUID();
            if (entity instanceof OwnableEntity pet) {
                UUID owner = pet.getOwnerUUID();
                if (owner == null) return null;
                Entity online = pet.getOwner();
                if (online == null) return owner;
                entity = online;
            } else if (entity instanceof LightningBolt bolt) {
                entity = bolt.getCause();
            } else if (entity instanceof TraceableEntity traced) {
                entity = traced.getOwner();
            } else {
                return null;
            }
        }
        return null;
    }

    private static Protection protection(ServerLevel level, BlockPos pos, ProtectionRule rule, @Nullable UUID actor) {
        boolean houses = HouseGate.installed();
        ResourceLocation mine = houses && actor != null ? HouseGate.of(level.getServer(), actor) : null;
        return protection(level, pos, rule, actor, mine, houses);
    }

    private static Protection protection(ServerLevel level, BlockPos pos, ProtectionRule rule, @Nullable UUID actor,
                                         @Nullable ResourceLocation actorHouse, boolean houses) {
        // Centro do bloco: a forma testa altura com min <= y <= max, e o piso de um andar e a face.
        return AreaData.get(level.getServer()).protection(level.dimension().location(),
                pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, rule, actor, actorHouse, houses);
    }
}
