package com.aurorion.mundos.portal;

import com.aurorion.mundos.AurorionMundos;
import com.aurorion.mundos.config.MundosConfig;
import com.aurorion.mundos.mixin.NetherPortalExitInvoker;
import com.aurorion.mundos.world.WorldCatalog;
import net.minecraft.BlockUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.DimensionTransition;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Onde um jogador sai, do outro lado do portal.
 *
 * <p>E a reimplementacao de {@code NetherPortalBlock#getExitPortal} com duas diferencas — e as duas
 * existem por causa dos 90 jogadores, nao por gosto.
 *
 * <h2>1. Raio de busca 16, e nao 128</h2>
 *
 * <p>O vanilla procura um portal existente num raio de <b>128</b> blocos sempre que o destino nao e o
 * Nether ({@code PortalForcer#OVERWORLD_PORTAL_RADIUS}). Isso vira, dentro de
 * {@code PoiManager#ensureLoadedAndValid}, uma varredura de {@code (2*128/16+1)^2 = 289} chunks e
 * cerca de 6.900 secoes de POI — <b>por travessia</b>.
 *
 * <p>Esse raio existe porque o Nether comprime coordenadas 8:1 e o ponto de chegada cai longe do
 * esperado. Entre dois mundos de {@code coordinate_scale 1.0},
 * {@code DimensionType#getTeleportationScale} devolve 1 e o destino cai <b>na mesma coordenada</b> —
 * 128 nao compra nada. Com 16 sao 9 chunks e 216 secoes: cerca de 32x menos chunk tocado.
 *
 * <h2>2. A trava de geracao</h2>
 *
 * <p>Nao achando portal do outro lado, o vanilla <b>cava um</b>. Cavar significa
 * {@code PortalForcer#createPortal} varrendo um espiral de 16 blocos e perguntando a altura do
 * terreno em cada coluna — e perguntar a altura de um chunk que nao existe <b>gera esse chunk</b>, na
 * thread do servidor.
 *
 * <p>Por isso cavar e negado por padrao ({@code allowRuntimeGeneration}). O desenho deste mod supoe
 * terreno pre-gerado fora e importado; a travessia sem portal de chegada e um erro de operacao — um
 * portal que a staff esqueceu de construir — e a resposta certa a um erro de operacao e uma mensagem,
 * nao um pico de worldgen no horario de pico.
 */
public final class PortalRouter {
    /**
     * Ultimo aviso de "sem destino" por jogador. Sem isso, quem fica parado dentro de um portal sem
     * saida recebe a mensagem a cada tick.
     *
     * <p>So cresce com quem de fato esbarrou num portal sem destino, e e zerado ao parar o servidor.
     */
    private static final Map<UUID, Long> LAST_DENIAL = new HashMap<>();

    private PortalRouter() {
    }

    /**
     * @return o destino, ou {@code null} quando nao ha para onde ir. {@code null} e um valor que o
     *         vanilla ja trata (e o que ele mesmo devolve quando nao consegue cavar um portal): a
     *         entidade simplesmente nao viaja.
     */
    @Nullable
    public static DimensionTransition destination(ServerLevel origin, Entity entity, BlockPos portalPos, DimensionLink link) {
        ServerLevel target = origin.getServer().getLevel(link.to());
        if (target == null) {
            AurorionMundos.LOGGER.error("Ligacao '{}' aponta para uma dimensao que nao existe: {}",
                    link.id(), link.to().location());
            return null;
        }

        WorldBorder border = target.getWorldBorder();
        double scale = DimensionType.getTeleportationScale(origin.dimensionType(), target.dimensionType());
        BlockPos exit = border.clampToBounds(entity.getX() * scale, entity.getY(), entity.getZ() * scale);

        int radius = link.searchRadius().orElseGet(MundosConfig.DEFAULT_SEARCH_RADIUS::get);
        BlockPos existing = findPortal(target, exit, radius, border);

        BlockUtil.FoundRectangle rectangle;
        DimensionTransition.PostDimensionTransition post;

        if (existing != null) {
            BlockState state = target.getBlockState(existing);
            rectangle = BlockUtil.getLargestRectangleAround(
                    existing,
                    state.getValue(BlockStateProperties.HORIZONTAL_AXIS),
                    21,
                    Direction.Axis.Y,
                    21,
                    candidate -> target.getBlockState(candidate) == state);
            post = DimensionTransition.PLAY_PORTAL_SOUND.then(traveller -> traveller.placePortalTicket(existing));
        } else {
            if (!WorldCatalog.allowsRuntimeGeneration(link.to())) {
                denied(entity, link, target);
                return null;
            }

            Direction.Axis axis = origin.getBlockState(portalPos)
                    .getOptionalValue(NetherPortalBlock.AXIS)
                    .orElse(Direction.Axis.X);

            Optional<BlockUtil.FoundRectangle> created = target.getPortalForcer().createPortal(exit, axis);
            if (created.isEmpty()) {
                AurorionMundos.LOGGER.error("Nao foi possivel cavar um portal em {} — destino provavelmente fora da barreira.",
                        link.to().location());
                return null;
            }

            rectangle = created.get();
            post = DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET);
        }

        return NetherPortalExitInvoker.aurorion_mundos$fromExit(entity, portalPos, rectangle, target, post);
    }

    /**
     * O portal existente mais proximo do ponto de chegada, dentro de {@code radius}.
     *
     * <p>Mesma consulta do {@code PortalForcer#findClosestPortalPosition} — inclusive a ordenacao por
     * distancia e depois por altura, que e o que faz a chegada ser estavel em vez de sortear entre
     * dois portais empatados. O que muda e so o raio deixar de ser constante.
     */
    @Nullable
    private static BlockPos findPortal(ServerLevel level, BlockPos exit, int radius, WorldBorder border) {
        PoiManager poi = level.getPoiManager();
        poi.ensureLoadedAndValid(level, exit, radius);

        return poi.getInSquare(type -> type.is(PoiTypes.NETHER_PORTAL), exit, radius, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .filter(border::isWithinBounds)
                // Um POI pode sobreviver ao bloco que o criou (portal quebrado sem o chunk carregado).
                // Sem esta checagem, o getValue(HORIZONTAL_AXIS) logo abaixo estouraria.
                .filter(pos -> level.getBlockState(pos).hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
                .min(Comparator.<BlockPos>comparingDouble(pos -> pos.distSqr(exit)).thenComparingInt(Vec3i::getY))
                .orElse(null);
    }

    private static void denied(Entity entity, DimensionLink link, ServerLevel target) {
        if (MundosConfig.LOG_DENIALS.get()) {
            AurorionMundos.LOGGER.info("Travessia negada por '{}': nenhum portal em {} num raio de {} blocos, e a dimensao nao permite geracao em runtime.",
                    link.id(), target.dimension().location(), link.searchRadius().orElseGet(MundosConfig.DEFAULT_SEARCH_RADIUS::get));
        }

        if (!(entity instanceof ServerPlayer player)) return;

        long now = System.currentTimeMillis();
        long cooldown = MundosConfig.DENY_MESSAGE_COOLDOWN_SECONDS.get() * 1000L;
        Long last = LAST_DENIAL.get(player.getUUID());
        if (last != null && now - last < cooldown) return;

        LAST_DENIAL.put(player.getUUID(), now);
        player.displayClientMessage(
                Component.translatable("aurorion_mundos.portal.sem_destino").withStyle(ChatFormatting.RED), true);
    }

    public static void forget(UUID player) {
        LAST_DENIAL.remove(player);
    }

    public static void clear() {
        LAST_DENIAL.clear();
    }
}
