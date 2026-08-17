package com.aurorion.ato2.house;

import com.aurorion.ato2.config.HouseConfig;
import com.aurorion.ato2.network.HouseChoiceResultPayload;
import com.aurorion.ato2.network.OpenHouseSelectionPayload;
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
 * Regras da escolha de casa. Tudo aqui roda no servidor: o cliente so desenha a tela e aponta um id.
 *
 * <p>Nada neste mod roda por tick. A escolha e um evento raro (uma vez por jogador, na vida do
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

    /** Monta o catalogo do momento e manda a tela para o jogador. Chamado pelo clique no altar. */
    public static void openSelection(ServerPlayer player, BlockPos altar) {
        List<House> houses = HouseCatalog.all();
        if (houses.isEmpty()) {
            player.sendSystemMessage(Component.translatable("aurorion_ato2.house.no_houses").withStyle(style -> style.withColor(0xFF5555)));
            return;
        }

        HouseData data = HouseData.get(player.server);
        ResourceLocation current = data.houseOf(player.getUUID());
        boolean locked = current != null && !HouseConfig.ALLOW_RECHOOSE.get();

        List<HouseOption> options = new ArrayList<>(houses.size());
        for (House house : houses) {
            options.add(new HouseOption(house, data.membersOf(house.id())));
        }

        // Mesmo travada a tela abre: e assim que o jogador consulta a propria casa no altar.
        PendingSelections.open(player, altar);
        PacketDistributor.sendToPlayer(player, new OpenHouseSelectionPayload(options, Optional.ofNullable(current), locked));
    }

    /** Processa o que o cliente escolheu e devolve o veredito para a tela. */
    public static Result choose(ServerPlayer player, ResourceLocation houseId) {
        Result result = validateAndApply(player, houseId);

        House house = HouseCatalog.get(houseId);
        Component message = switch (result) {
            case OK -> Component.translatable("aurorion_ato2.house.chosen",
                    house != null ? house.coloredName() : Component.literal(houseId.toString()));
            case NO_HOUSES -> Component.translatable("aurorion_ato2.house.no_houses");
            case UNKNOWN_HOUSE -> Component.translatable("aurorion_ato2.house.error.unknown");
            case NOT_AT_ALTAR -> Component.translatable("aurorion_ato2.house.error.not_at_altar");
            case ALREADY_CHOSEN -> Component.translatable("aurorion_ato2.house.error.already_chosen");
            case FULL -> Component.translatable("aurorion_ato2.house.error.full");
        };

        PacketDistributor.sendToPlayer(player, new HouseChoiceResultPayload(result == Result.OK, message));

        if (result == Result.OK && house != null && HouseConfig.ANNOUNCE_IN_CHAT.get()) {
            player.server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("aurorion_ato2.house.announce", player.getDisplayName(), house.coloredName()), false);
        }
        return result;
    }

    private static Result validateAndApply(ServerPlayer player, ResourceLocation houseId) {
        // A permissao e gasta antes de qualquer outra checagem: uma tela aberta vale uma tentativa,
        // certa ou errada. Do contrario daria para varrer ids ate achar uma casa com vaga.
        if (!PendingSelections.consume(player)) {
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
            if (!HouseConfig.ALLOW_RECHOOSE.get()) {
                return Result.ALREADY_CHOSEN;
            }
        }

        // Lotacao conferida aqui, e nao no cliente: entre abrir a tela e confirmar, outra pessoa
        // pode ter pego a ultima vaga.
        if (house.hasCapacity() && data.membersOf(houseId) >= house.capacity()) {
            return Result.FULL;
        }

        data.setHouse(uuid, houseId);
        return Result.OK;
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
        return HouseData.get(server).setHouse(player, house);
    }

    public static boolean clear(MinecraftServer server, UUID player) {
        return HouseData.get(server).setHouse(player, null);
    }

    public static int membersOf(MinecraftServer server, ResourceLocation house) {
        return HouseData.get(server).membersOf(house);
    }
}
