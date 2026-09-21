package com.aurorion.areas.server;

import com.aurorion.areas.AurorionAreas;
import com.aurorion.areas.geometry.AreaShape;
import com.aurorion.areas.geometry.Bounds;
import com.aurorion.areas.geometry.Point2;
import com.aurorion.areas.region.AreaRegion;
import com.aurorion.areas.rules.Decision;
import com.aurorion.core.house.HouseGate;
import com.aurorion.core.level.SafeSpot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A parede invisivel das areas de casa: quem nao e da casa nao atravessa o limite dela.
 *
 * <p><b>Nao e uma prisao nem um congelamento.</b> O jogador continua andando, pulando e voltando
 * para tras a vontade — o que ele nao consegue e terminar um tick <em>dentro</em> de uma casa que
 * nao e a dele. O empurrao devolve a ultima posicao permitida, que e a de um tick atras, entao o
 * deslocamento e sempre menor que um passo: bate e para, como uma parede.
 *
 * <h2>Quem passa</h2>
 *
 * <p>Criativo passa por qualquer casa, <b>com ou sem OP</b>; sobrevivencia nao passa, <b>com ou sem
 * OP</b>. Essa e a diferenca deliberada para o {@link com.aurorion.areas.api.AreaApi#bypass} das
 * demais regras, que exige criativo <em>e</em> permissao 2 e obedece uma config: aqui o que importa
 * e o modo de jogo, porque a barreira e ficcao do mundo (de quem esta jogando) e nao uma ferramenta
 * de moderacao (de quem esta administrando). Um administrador jogando em sobrevivencia participa da
 * ficcao como qualquer pessoa; para atravessar, ele troca para criativo.
 *
 * <h2>Custo</h2>
 *
 * <p>Fora de uma area de casa, isto e uma leitura de campo por tick e nada mais: a area dona ja veio
 * resolvida junto com as regras do jogador ({@code ResolvedRules#houseArea}), sem percorrer a arvore
 * nem testar poligono de novo. A casa do jogador so e consultada quando ele esta de fato dentro de
 * uma area de casa, e a consulta e um {@code HashMap#get} (SDD §2).
 */
public final class HouseBarrier {
    /**
     * Regra que abre a passagem sem mudar a dona da area — o "passe de visitante".
     *
     * <p>Nao e um cadastro novo: e a mesma maquina de excecoes e regras que as outras politicas ja
     * usam, entao {@code /area excecao <area> <jogador> casa true} libera uma pessoa e
     * {@code /area regra <area> casa permitir} abre a porta para todo mundo (util num evento). O
     * reset de personagem ja limpa essas excecoes junto com as demais, de graca.
     */
    public static final String PASS_RULE = "casa";
    /** Um aviso a cada dois segundos: menos que isso vira zumbido enquanto a pessoa encosta na parede. */
    private static final int NOTICE_TICKS = 40;
    /** Expulsao no maximo uma vez por segundo, para um ponto ruim nao virar teleporte por tick. */
    private static final int EJECT_TICKS = 20;
    /** Folga ao sair da forma, para nao parar exatamente em cima da linha e reentrar no tick seguinte. */
    private static final double MARGIN = 1.5;
    private static final Component HOUSELESS = Component.literal(" Você ainda não tem casa.")
            .withStyle(ChatFormatting.DARK_GRAY);
    private static boolean warnedMissingGate;

    private HouseBarrier() {
    }

    /**
     * Criativo e espectador atravessam; sobrevivencia e aventura nao.
     *
     * <p>Fake players e NPCs marcados ficam de fora pelo mesmo motivo de sempre: nao sao pessoas
     * jogando, e um NPC preso na porta da casa errada seria so um bug com aparencia de regra.
     */
    public static boolean bypass(ServerPlayer player) {
        return player.isSpectator() || player.isCreative()
                || player instanceof net.neoforged.neoforge.common.util.FakePlayer
                || player.getTags().contains("aurorion_areas_npc");
    }

    /**
     * @return a casa que barra este jogador nesta posicao, ou {@code null} se ele pode estar aqui.
     */
    @Nullable
    static AreaRegion blocking(ServerPlayer player, @Nullable AreaRegion houseArea) {
        if (houseArea == null) return null;
        if (!HouseGate.installed()) {
            // Sem mod de casas, "todo mundo sem casa" barraria todo mundo de toda casa. Melhor nao
            // barrar ninguem e dizer por que — no log, uma vez, nao no chat de quem esta jogando.
            if (!warnedMissingGate) {
                warnedMissingGate = true;
                AurorionAreas.LOGGER.warn("Areas: a area '{}' pertence a casa {}, mas nenhum mod de casas esta"
                        + " instalado. A barreira de casa esta inativa ate o aurorion_ethereal voltar ao pack.",
                        houseArea.id(), houseArea.house());
            }
            return null;
        }
        ResourceLocation mine = HouseGate.of(player.server, player.getUUID());
        if (houseArea.house().equals(mine)) return null;
        return houseArea.decision(PASS_RULE, player.getUUID()) == Decision.ALLOW ? null : houseArea;
    }

    /** Roda uma vez por tick para cada jogador sujeito a barreira. */
    static void tick(ServerPlayer player, AreaRuntime.State state, long now) {
        AreaRegion blocked = blocking(player, state.rules.houseArea());
        if (blocked == null) {
            remember(player, state);
            return;
        }
        if (!state.hasSafeSpot) {
            // Entrou sem ter passado pela porta: deslogou dentro, nasceu dentro, ou foi teleportado.
            if (now < state.ejectAt) return;
            state.ejectAt = now + EJECT_TICKS;
            eject(player, blocked);
            notice(player, state, blocked, now);
            return;
        }
        push(player, state.safeX, state.safeY, state.safeZ);
        notice(player, state, blocked, now);
    }

    /**
     * Devolve o jogador a uma posicao e para o movimento dele ali.
     *
     * <p><b>Desmontar vem primeiro, e nao e detalhe:</b> {@code absMoveTo} nao tira ninguem de cima
     * de um veiculo, e o {@code rideTick} do proprio jogador o gruda de volta na posicao da montaria
     * no tick seguinte — ou seja, a cavalo ou de barco o empurrao seria <em>desfeito</em> e a parede
     * simplesmente nao existiria. A montaria fica onde estava; ela nao e quem a barreira barra.
     */
    private static void push(ServerPlayer player, double x, double y, double z) {
        if (player.isPassenger()) player.stopRiding();
        player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        // O empurrao nao pode cobrar a queda: quem foi barrado no ar cairia de onde nunca esteve.
        player.fallDistance = 0;
    }

    /** Guarda o ultimo lugar em que o jogador tinha direito de estar — o destino do empurrao. */
    private static void remember(ServerPlayer player, AreaRuntime.State state) {
        state.safeX = player.getX();
        state.safeY = player.getY();
        state.safeZ = player.getZ();
        state.hasSafeSpot = true;
    }

    /**
     * Tira o jogador de dentro pelo lado mais proximo da forma em que ele esta.
     *
     * <p>Usa a forma que o contem, e nao a caixa da area inteira: numa area de varias partes, a
     * caixa da uniao pode estar centenas de blocos longe, e a saida seria uma viagem em vez de um
     * passo para fora. Circulo sai pelo raio; poligono sai pela face mais proxima da caixa dele, que
     * e sempre um ponto fora do poligono.
     */
    private static void eject(ServerPlayer player, AreaRegion area) {
        double x = player.getX(), y = player.getY(), z = player.getZ();
        AreaShape shape = containing(area, x, y, z);
        double targetX = x, targetZ = z;
        if (shape != null && shape.isCircle()) {
            Point2 center = shape.points().getFirst();
            double dx = x - center.x(), dz = z - center.z();
            double length = Math.sqrt(dx * dx + dz * dz);
            // No centro exato nao ha direcao; qualquer uma serve, e o leste e tao boa quanto outra.
            if (length < 1e-4) { dx = 1; dz = 0; length = 1; }
            double out = shape.radius() + MARGIN;
            targetX = center.x() + dx / length * out;
            targetZ = center.z() + dz / length * out;
        } else {
            Bounds box = shape == null ? area.volume().bounds() : shape.bounds();
            double west = x - box.minX(), east = box.maxX() - x, north = z - box.minZ(), south = box.maxZ() - z;
            double nearest = Math.min(Math.min(west, east), Math.min(north, south));
            if (nearest == west) targetX = box.minX() - MARGIN;
            else if (nearest == east) targetX = box.maxX() + MARGIN;
            else if (nearest == north) targetZ = box.minZ() - MARGIN;
            else targetZ = box.maxZ() + MARGIN;
        }

        ServerLevel level = player.serverLevel();
        // Caminho raro (uma vez por segundo, so para quem apareceu dentro), entao o custo de gerar
        // um chunk aqui e aceitavel — ver o aviso do SafeSpot.
        BlockPos ground = SafeSpot.scanDown(level, Mth.floor(targetX), Mth.floor(targetZ), Mth.ceil(y) + 8);
        if (ground == null) ground = SafeSpot.aroundColumn(level, BlockPos.containing(targetX, y, targetZ), 3, Mth.ceil(y) + 8);
        double landingY = ground == null ? y : ground.getY();
        double landingX = ground == null ? targetX : ground.getX() + .5;
        double landingZ = ground == null ? targetZ : ground.getZ() + .5;

        push(player, landingX, landingY, landingZ);
    }

    @Nullable
    private static AreaShape containing(AreaRegion area, double x, double y, double z) {
        for (AreaShape part : area.volume().parts()) {
            if (part.contains(x, y, z)) return part;
        }
        return null;
    }

    /**
     * Barra de acao, nunca chat: a pessoa vai encostar na parede varias vezes, e um historico de
     * chat cheio de "voce nao pode entrar" e pior que a propria parede (mesma razao do aviso de
     * limpeza no {@code aurorion-essentials}).
     */
    private static void notice(ServerPlayer player, AreaRuntime.State state, AreaRegion area, long now) {
        if (now < state.barrierNoticeAt) return;
        state.barrierNoticeAt = now + NOTICE_TICKS;
        var message = Component.literal("A barreira da ").withStyle(ChatFormatting.GRAY)
                .append(HouseGate.nameOf(area.house()))
                .append(Component.literal(" não deixa você passar.").withStyle(ChatFormatting.GRAY));
        // Quem nao tem casa nenhuma bate em todas as paredes; dizer isso na mesma linha evita que a
        // pessoa fique procurando o que fez de errado.
        if (HouseGate.of(player.server, player.getUUID()) == null) message.append(HOUSELESS);
        player.displayClientMessage(message, true);
        player.playNotifySound(SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, .35F, .6F);
    }
}
