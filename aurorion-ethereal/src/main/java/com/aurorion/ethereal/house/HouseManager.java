package com.aurorion.ethereal.house;

import com.aurorion.ethereal.config.EtherealConfig;
import com.aurorion.ethereal.network.HouseChoiceResultPayload;
import com.aurorion.ethereal.network.OpenHouseSelectionPayload;
import com.aurorion.ethereal.ranking.BoardService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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
        FULL
    }

    private HouseManager() {
    }

    /**
     * Monta o catalogo do momento e manda a grade de casas para o jogador.
     *
     * <p>Com {@code ceremonyRequired} ligado (o padrao), esta tela e so de leitura: e como o jogador
     * que ja tem casa consulta a propria no altar. Quem vincula gente e a cerimonia.
     */
    public static void openSelection(ServerPlayer player, BlockPos altar, boolean readOnly) {
        List<House> houses = HouseCatalog.all();
        if (houses.isEmpty()) {
            player.sendSystemMessage(Component.translatable("aurorion_ethereal.house.no_houses")
                    .withStyle(style -> style.withColor(0xFF5555)));
            return;
        }

        HouseData data = HouseData.get(player.server);
        ResourceLocation current = data.houseOf(player.getUUID());
        boolean locked = readOnly || (current != null && !EtherealConfig.ALLOW_RECHOOSE.get());

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
        };

        PacketDistributor.sendToPlayer(player, new HouseChoiceResultPayload(result == Result.OK, message));

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
        ResourceLocation current = data.houseOf(uuid);

        if (current != null) {
            if (current.equals(houseId)) {
                return Result.OK;
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

        bind(player.server, uuid, houseId);
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
     * @param house casa nova, ou {@code null} para desvincular.
     * @return true se algo mudou de fato.
     */
    public static boolean bind(MinecraftServer server, UUID player, @Nullable ResourceLocation house) {
        if (!HouseData.get(server).setHouse(player, house)) {
            return false;
        }
        BoardService.refreshHouses(server);
        return true;
    }

    /** Anuncio no chat do servidor, se a config deixar. */
    public static void announce(MinecraftServer server, Component playerName, House house) {
        if (EtherealConfig.ANNOUNCE_IN_CHAT.get()) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("aurorion_ethereal.house.announce", playerName, house.coloredName()), false);
        }
    }

    // --- Consulta e administracao (comando /casa) ---

    @Nullable
    public static ResourceLocation houseIdOf(MinecraftServer server, UUID player) {
        return HouseData.get(server).houseOf(player);
    }

    /**
     * @return a casa do jogador, vazio se ele nao tem casa <em>ou</em> se a casa dele sumiu do
     * datapack — o id continua gravado, mas nao ha definicao para mostrar.
     */
    public static Optional<House> houseOf(MinecraftServer server, UUID player) {
        ResourceLocation id = houseIdOf(server, player);
        return id == null ? Optional.empty() : Optional.ofNullable(HouseCatalog.get(id));
    }

    /** Atribuicao por admin: ignora ritual, trava de re-escolha e lotacao. */
    public static boolean assign(MinecraftServer server, UUID player, ResourceLocation house) {
        return bind(server, player, house);
    }

    public static boolean clear(MinecraftServer server, UUID player) {
        return bind(server, player, null);
    }

    public static int membersOf(MinecraftServer server, ResourceLocation house) {
        return HouseData.get(server).membersOf(house);
    }
}
