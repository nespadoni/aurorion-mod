package com.aurorion.ethereal.house;

import com.aurorion.core.house.HouseChangedEvent;
import com.aurorion.core.house.HouseGate;
import com.aurorion.core.integration.GameFacts;
import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.config.EtherealConfig;
import com.aurorion.ethereal.network.HouseChoiceResultPayload;
import com.aurorion.ethereal.network.OpenHouseSelectionPayload;
import com.aurorion.ethereal.ranking.BoardService;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras da vinculacao a uma casa. Tudo aqui roda no servidor: o cliente so desenha a tela e, no
 * caminho de escolha direta, aponta um id.
 *
 * <p>Nada neste caminho roda por tick. Vincular e um evento raro (uma vez por jogador, na vida do
 * personagem), entao o custo total da feature e um punhado de eventos de clique, um pacote de ida e
 * um de volta — SDD §7.1, custo "por evento" em vez de "por jogador".
 */
public final class HouseManager {
    public enum Result {
        OK,
        /** Nenhum datapack definiu casa nenhuma — sem isso o altar nao tem o que oferecer. */
        NO_HOUSES,
        /** Id que nao existe no catalogo: cliente adulterado, ou datapack recarregado no meio. */
        UNKNOWN_HOUSE,
        /** Nao havia tela aberta, ou o jogador se afastou/o altar sumiu antes de confirmar. */
        NOT_AT_ALTAR,
        /** Ja tem casa e o servidor nao permite trocar. */
        ALREADY_CHOSEN,
        /** A casa bateu a lotacao declarada no datapack. */
        FULL,
        /** Escolheu a casa que ja tinha: nada muda, e por isso nada e anunciado de novo. */
        UNCHANGED
    }

    private HouseManager() {
    }

    /**
     * Monta o catalogo do momento e manda a grade de casas para o jogador.
     *
     * <p>Com {@code ceremonyRequired} ligado (o padrao), esta tela e so de leitura: e como o jogador
     * que ja tem casa consulta a propria no altar. Quem vincula gente e a cerimonia.
     *
     * <p>O altar so mostra a casa <b>revelada</b>. Um cadastro ainda oculto trava a tela sem marcar
     * casa nenhuma: o jogador nao pode escolher por cima do que a staff decidiu, nem descobrir o que
     * foi decidido antes da cerimonia.
     */
    public static void openSelection(ServerPlayer player, BlockPos altar, boolean readOnly) {
        List<House> houses = HouseCatalog.all();
        if (houses.isEmpty()) {
            player.sendSystemMessage(Component.translatable("aurorion_ethereal.house.no_houses")
                    .withStyle(style -> style.withColor(0xFF5555)));
            return;
        }

        HouseData data = HouseData.get(player.server);
        ResourceLocation current = data.publicHouseOf(player.getUUID());
        boolean locked = readOnly || data.isHidden(player.getUUID())
                || (current != null && !EtherealConfig.ALLOW_RECHOOSE.get());

        List<HouseOption> options = new ArrayList<>(houses.size());
        for (House house : houses) {
            options.add(new HouseOption(house, data.membersOf(house.id())));
        }

        // A permissao so e aberta quando a tela pode mesmo escolher: numa tela travada, guardar uma
        // pendencia daria a um cliente adulterado uma janela de escolha que a GUI nao oferece.
        if (!locked) {
            PendingSelections.open(player, altar);
        }
        PacketDistributor.sendToPlayer(player,
                new OpenHouseSelectionPayload(options, Optional.ofNullable(current), locked));
    }

    /** Processa o que o cliente escolheu na grade e devolve o veredito para a tela. */
    public static Result choose(ServerPlayer player, ResourceLocation houseId) {
        Result result = validateAndApply(player, houseId);

        House house = HouseCatalog.get(houseId);
        Component message = switch (result) {
            case OK -> Component.translatable("aurorion_ethereal.house.chosen",
                    house != null ? house.coloredName() : Component.literal(houseId.toString()));
            case NO_HOUSES -> Component.translatable("aurorion_ethereal.house.no_houses");
            case UNKNOWN_HOUSE -> Component.translatable("aurorion_ethereal.house.error.unknown");
            case NOT_AT_ALTAR -> Component.translatable("aurorion_ethereal.house.error.not_at_altar");
            case ALREADY_CHOSEN -> Component.translatable("aurorion_ethereal.house.error.already_chosen");
            case FULL -> Component.translatable("aurorion_ethereal.house.error.full");
            case UNCHANGED -> Component.translatable("aurorion_ethereal.house.error.same");
        };

        PacketDistributor.sendToPlayer(player, new HouseChoiceResultPayload(
                result == Result.OK || result == Result.UNCHANGED, message));

        if (result == Result.OK && house != null) {
            announce(player.server, player.getDisplayName(), house);
        }
        return result;
    }

    private static Result validateAndApply(ServerPlayer player, ResourceLocation houseId) {
        // A permissao e gasta antes de qualquer outra checagem: uma tela aberta vale uma tentativa,
        // certa ou errada. Do contrario daria para varrer ids ate achar uma casa com vaga.
        if (!PendingSelections.consume(player)) {
            return Result.NOT_AT_ALTAR;
        }
        if (EtherealConfig.CEREMONY_REQUIRED.get()) {
            // A config pode ter mudado entre abrir a tela e confirmar. Quem vincula e a cerimonia.
            return Result.NOT_AT_ALTAR;
        }
        if (HouseCatalog.isEmpty()) {
            return Result.NO_HOUSES;
        }

        House house = HouseCatalog.get(houseId);
        if (house == null) {
            return Result.UNKNOWN_HOUSE;
        }

        HouseData data = HouseData.get(player.server);
        UUID uuid = player.getUUID();
        if (data.isHidden(uuid)) {
            // Cadastro da staff ainda nao revelado: a tela nem abre escolhivel, mas um cliente
            // adulterado recebe a mesma resposta de uma tela velha, sem saber que ha casa gravada.
            return Result.NOT_AT_ALTAR;
        }
        ResourceLocation current = data.houseOf(uuid);

        if (current != null) {
            if (current.equals(houseId)) {
                return Result.UNCHANGED;
            }
            if (!EtherealConfig.ALLOW_RECHOOSE.get()) {
                return Result.ALREADY_CHOSEN;
            }
        }

        // Lotacao conferida aqui, e nao no cliente: entre abrir a tela e confirmar, outra pessoa
        // pode ter pego a ultima vaga.
        if (house.hasCapacity() && data.membersOf(houseId) >= house.capacity()) {
            return Result.FULL;
        }

        // Escolha do proprio jogador: nao ha segredo a guardar, entao ja nasce revelada.
        bind(player.server, uuid, houseId, false);
        return Result.OK;
    }

    /**
     * O unico lugar que grava a casa de alguem — cerimonia, comando de staff e escolha direta passam
     * todos por aqui.
     *
     * <p>Ele existe para que a atualizacao do placar nao dependa de ninguem lembrar dela: o total de
     * uma casa e a soma dos pontos dos membros dela, entao <b>mudar de casa muda o placar</b> mesmo
     * sem nenhum ponto ter sido dado ou tirado. Deixar esse refresh a cargo de cada chamador seria a
     * mesma armadilha que o {@code SavedDataAccess} do core ja evitou (SDD §3.1).
     *
     * <p>Placar e tab so sao avisados quando muda a casa <b>publica</b> (a revelada): um cadastro
     * oculto da staff nao pode repintar o nome de ninguem nem mexer no total de uma casa.
     *
     * @param house casa nova, ou {@code null} para desvincular.
     * @param hidden grava o cadastro como oculto ate {@link #reveal}.
     * @return true se algo mudou de fato.
     */
    public static boolean bind(MinecraftServer server, UUID player, @Nullable ResourceLocation house, boolean hidden) {
        HouseData data = HouseData.get(server);
        ResourceLocation previous = data.houseOf(player);
        ResourceLocation publicBefore = data.publicHouseOf(player);
        boolean moved = data.setHouse(player, house);
        boolean visibility = house != null && data.setHidden(player, hidden);
        if (!moved && !visibility) {
            return false;
        }
        if (moved) publishChange(server, player, previous, house);
        afterPublicChange(server, player, publicBefore);
        return true;
    }

    /** Atalho para a casa ja revelada (escolha propria, API e testes). */
    public static boolean bind(MinecraftServer server, UUID player, @Nullable ResourceLocation house) {
        return bind(server, player, house, false);
    }

    private static void afterPublicChange(MinecraftServer server, UUID player, @Nullable ResourceLocation before) {
        if (Objects.equals(before, HouseData.get(server).publicHouseOf(player))) return;
        BoardService.refreshHouses(server);
        // Quem mostra algo pela casa (cor na tab) atualiza aqui, em vez de conferir por tick.
        NeoForge.EVENT_BUS.post(new HouseChangedEvent(server, player));
    }

    /**
     * Avisa o site da troca de casa. O backend guarda o fato visivel so para a equipe: a casa pode
     * revelar o resultado da cerimonia antes da hora, e quem decide quando publicar e a staff.
     *
     * <p>A casa vai escrita aqui, e nao pelo {@code Subject}: o {@code HouseGate} (de onde o Subject
     * le) so responde a casa revelada, e este fato precisa levar o cadastro mesmo oculto.
     */
    private static void publishChange(MinecraftServer server, UUID player, @Nullable ResourceLocation previous,
                                      @Nullable ResourceLocation house) {
        if (!GameFacts.installed()) return;
        try {
            JsonObject payload = new JsonObject();
            if (previous != null) payload.addProperty("previous_house", HouseGate.nameOf(previous).getString());
            if (house != null) payload.addProperty("house", HouseGate.nameOf(house).getString());
            GameFacts.publish(GameFacts.HOUSE_CHANGED, GameFacts.subject(server, player), payload);
        } catch (RuntimeException e) {
            AurorionEthereal.LOGGER.warn("Nao consegui publicar a troca de casa de {}", player, e);
        }
    }

    /** Anuncio no chat do servidor, se a config deixar. */
    public static void announce(MinecraftServer server, Component playerName, House house) {
        if (EtherealConfig.ANNOUNCE_IN_CHAT.get()) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("aurorion_ethereal.house.announce", playerName, house.coloredName()), false);
        }
    }

    // --- Consulta e administracao (comando /casa) ---

    /** A casa cadastrada, revelada ou nao. Regra de servidor e ferramenta de staff; nunca para o jogador. */
    @Nullable
    public static ResourceLocation houseIdOf(MinecraftServer server, UUID player) {
        return HouseData.get(server).houseOf(player);
    }

    /** A casa que o jogo pode mostrar e usar: {@code null} enquanto o cadastro estiver oculto. */
    @Nullable
    public static ResourceLocation publicHouseIdOf(MinecraftServer server, UUID player) {
        return HouseData.get(server).publicHouseOf(player);
    }

    public static boolean isHidden(MinecraftServer server, UUID player) {
        return HouseData.get(server).isHidden(player);
    }

    /**
     * Cadastro da staff ({@code /casa definir} e a cerimonia): grava oculto ate a revelacao.
     * Repetir a casa que a pessoa ja tem nao muda nada — nem esconde de novo uma casa ja revelada.
     */
    public static boolean register(MinecraftServer server, UUID player, ResourceLocation house) {
        if (house.equals(houseIdOf(server, player))) return false;
        return bind(server, player, house, true);
    }

    /** Torna publico um cadastro oculto: e o que a cerimonia faz no instante da revelacao. */
    public static boolean reveal(MinecraftServer server, UUID player) {
        return setHidden(server, player, false);
    }

    /** Volta a esconder uma casa (correcao da staff, ou cadastro antigo feito antes desta regra). */
    public static boolean conceal(MinecraftServer server, UUID player) {
        return setHidden(server, player, true);
    }

    private static boolean setHidden(MinecraftServer server, UUID player, boolean hidden) {
        HouseData data = HouseData.get(server);
        ResourceLocation before = data.publicHouseOf(player);
        if (!data.setHidden(player, hidden)) return false;
        afterPublicChange(server, player, before);
        return true;
    }

    /**
     * @return a casa do jogador, vazio se ele nao tem casa <em>ou</em> se a casa dele sumiu do
     * datapack — o id continua gravado, mas nao ha definicao para mostrar.
     */
    public static Optional<House> houseOf(MinecraftServer server, UUID player) {
        ResourceLocation id = houseIdOf(server, player);
        return id == null ? Optional.empty() : Optional.ofNullable(HouseCatalog.get(id));
    }

    /**
     * Atribuicao ja revelada: ignora ritual, trava de re-escolha e lotacao. Para o cadastro secreto
     * da staff use {@link #register}.
     */
    public static boolean assign(MinecraftServer server, UUID player, ResourceLocation house) {
        return bind(server, player, house, false);
    }

    public static boolean clear(MinecraftServer server, UUID player) {
        return bind(server, player, null);
    }

    public static int membersOf(MinecraftServer server, ResourceLocation house) {
        return HouseData.get(server).membersOf(house);
    }
}
