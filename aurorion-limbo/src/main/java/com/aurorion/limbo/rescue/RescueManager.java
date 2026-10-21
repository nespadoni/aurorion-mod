package com.aurorion.limbo.rescue;

import com.aurorion.core.level.SafeSpot;
import com.aurorion.core.character.CharacterData;
import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.config.LimboConfig;
import com.aurorion.limbo.exile.ExileRecord;
import com.aurorion.limbo.exile.ForgottenDoor;
import com.aurorion.limbo.exile.LimboData;
import com.aurorion.limbo.exile.LimboManager;
import com.aurorion.limbo.exile.LimboSpawn;
import com.aurorion.limbo.finale.FinaleManager;
import com.aurorion.limbo.narrate.LimboText;
import com.aurorion.limbo.network.OpenOraclePayload;
import com.aurorion.limbo.registry.LimboItems;
import com.aurorion.limbo.report.AuditEvent;
import com.aurorion.limbo.report.AuditLog;
import com.aurorion.vidas.lives.LivesManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O resgate: abrir a passagem, atravessar, achar a pessoa e traze-la de volta.
 *
 * <h2>Onde as regras moram</h2>
 *
 * <p>Todas aqui, e nenhuma no cliente. A tela do Oraculo manda um UUID e nada mais; quem confere se a
 * pessoa esta mesmo no Limbo, se quem clicou tem vida para pagar e se ela nao esta tentando resgatar a
 * si mesma e este arquivo. A diretriz 5 da §7 vale em dobro aqui, porque <b>uma escolha de tela custa
 * uma vida</b> — e vida e o recurso mais caro do servidor.
 *
 * <h2>A conclusao reusa o caminho que ja existe</h2>
 *
 * <p>Usar o Vinculo nao teleporta o exilado. Ele <b>devolve as vidas</b>, e a varredura do
 * {@code LimboManager} percebe no proximo segundo que aquela pessoa deixou de estar exilada e faz o
 * resto — volta ao overworld, fecha o registro, apaga a Porta, narra e audita.
 *
 * <p>Isso nao e preguica: e a mesma saida que o {@code /vidas dar} da staff usa, entao resgate por
 * item e resgate por comando percorrem exatamente o mesmo codigo. Dois caminhos separados para "sair
 * do Limbo" seria a garantia de que um dia eles discordariam.
 *
 * <h2>Uma vida paga, um resgate</h2>
 *
 * <p>O Vinculo nasce <b>atado</b>: guarda no proprio item quem pagou, quem e o alvo e qual queda do
 * alvo (o {@code startedAt} do registro). Sem isso, os dois Vinculos de uma travessia resgatavam duas
 * pessoas diferentes, e o que sobrava podia ser guardado num bau para a proxima — uma vida comprando
 * varios resgates. Atado, o reserva so serve para o caso que ele existe para cobrir: perder o primeiro.
 * E o que sobra e recolhido quando o resgatador sai do Limbo ou conclui o resgate.
 */
public final class RescueManager {
    /** Uma linha da tela do Oraculo. O cliente recebe isto pronto e nao calcula nada. */
    public record ExileInfo(UUID id, String name, long remainingMillis, boolean attempted, boolean online) {
    }

    /** Por que a passagem nao abriu. O cliente so exibe; a decisao ja foi tomada aqui. */
    public enum Refusal {
        OK,
        NOT_EXILED,
        SELF,
        RESCUER_EXILED,
        NOT_ENOUGH_LIVES,
        NO_ROOM
    }

    /** Onde o Vinculo guarda a quem foi atado, dentro do {@code minecraft:custom_data} do item. */
    private static final String BOND_KEY = AurorionLimbo.MOD_ID + ":vinculo";
    private static final String BOND_TARGET = "Alvo";
    private static final String BOND_PAYER = "Pagante";
    private static final String BOND_EXILE = "Queda";
    private static final String BOND_NAME = "Nome";

    /** Entre duas tentativas de devolver ao overworld quem ficou no Limbo sem motivo. */
    private static final int STRAY_RETRY_TICKS = 20 * 10;

    /**
     * Viagem de resgate em andamento nesta sessao: quem atravessou, para quem, e para qual queda.
     *
     * <p>E o que permite ao resgatador que morreu dentro do Limbo renascer la (os Vinculos caem com o
     * corpo e ele precisa voltar para busca-los) sem abrir a mesma porta para qualquer um que esteja no
     * Limbo sem estar exilado. Nao vai para o save: depois de um reinicio, o Vinculo atado no inventario
     * e a prova da viagem (ver {@link #isRescuer}).
     */
    private record Trip(UUID target, long exileStamp) {
    }

    private static final Map<UUID, Trip> TRIPS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_STRAY_TRY = new HashMap<>();

    private RescueManager() {
    }

    // --- A lista do Oraculo --------------------------------------------------------------------

    /**
     * Quem esta no Limbo agora.
     *
     * <p>O Oraculo mostra <b>todo mundo</b>, sem taxa. A economia de informacao — casa sabe de graca,
     * o resto paga — esta desenhada mas nao construida; enquanto nao existir, cobrar meio preco por
     * meia mecanica so confundiria. Ver "O que ainda nao existe" no README.
     */
    public static List<ExileInfo> listExiles(MinecraftServer server) {
        Map<UUID, ExileRecord> active = LimboData.get(server).active();
        List<ExileInfo> out = new ArrayList<>(active.size());
        CharacterData characters = CharacterData.get(server);

        active.forEach((id, record) -> {
            CharacterData.Character character = characters.find(id);
            if (record.remainingMillis() <= 0 || character == null || character.dead()) return;
            String name = character.named() ? character.fullName() : record.lastName();
            if (name.length() > OpenOraclePayload.MAX_NAME) name = name.substring(0, OpenOraclePayload.MAX_NAME);
            out.add(new ExileInfo(
                id,
                name,
                record.remainingMillis(),
                record.rescueAttempted(),
                server.getPlayerList().getPlayer(id) != null));
        });

        // Prazo mais curto primeiro: quem esta mais perto de sumir aparece no topo.
        out.sort((a, b) -> Long.compare(a.remainingMillis(), b.remainingMillis()));
        return out;
    }

    /**
     * O registro de quem ainda pode ser resgatado, ou {@code null}.
     *
     * <p>Uma pergunta so, feita na abertura, na travessia, a cada tick da passagem e no uso do Vinculo:
     * entre o clique no Oraculo e a travessia podem passar quinze minutos, e nesse tempo a pessoa pode
     * ter sido resgatada por outra equipe, pela staff, ou ter morrido de vez.
     */
    @Nullable
    public static ExileRecord rescuable(MinecraftServer server, UUID target) {
        ExileRecord record = LimboData.get(server).record(target);
        if (record == null || record.remainingMillis() <= 0 || !LivesManager.isExiled(server, target)
                || CharacterData.get(server).isDead(target)) return null;
        return record;
    }

    // --- Abrir a passagem ----------------------------------------------------------------------

    /**
     * Cobra a vida e abre a passagem na frente de quem pagou.
     *
     * <p>A ordem importa: a vida so e debitada <b>depois</b> de a passagem existir no mundo. Se o
     * lugar nao servir, ninguem paga por uma passagem que nao abriu.
     *
     * <p>Abrir <b>nao</b> conta como tentativa de resgate: a tentativa so e marcada quando o dono
     * atravessa ({@link #enterPassage}). Antes disso, abrir e deixar fechar desligaria para sempre a
     * Porta do Esquecido de alguem sem que ninguem tivesse ido busca-lo de verdade.
     */
    public static Refusal openPassage(ServerPlayer rescuer, UUID target) {
        MinecraftServer server = rescuer.server;
        UUID rescuerId = rescuer.getUUID();

        if (rescuerId.equals(target)) return Refusal.SELF;
        if (LivesManager.isExiled(server, rescuerId)) return Refusal.RESCUER_EXILED;

        ExileRecord record = rescuable(server, target);
        if (record == null) return Refusal.NOT_EXILED;

        int cost = LimboConfig.RESCUE_LIFE_COST.get();
        if (LivesManager.livesOf(server, rescuerId) < LimboConfig.minLivesToRescue()) {
            return Refusal.NOT_ENOUGH_LIVES;
        }

        ServerLevel level = rescuer.serverLevel();
        Vec3 spot = passageSpot(rescuer);
        if (spot == null) return Refusal.NO_ROOM;

        RescuePortalEntity portal = new RescuePortalEntity(level, target, rescuerId,
                LimboConfig.PASSAGE_MINUTES.get() * 60 * 20);
        portal.setPos(spot);
        if (!level.addFreshEntity(portal)) return Refusal.NO_ROOM;

        if (cost > 0) {
            LivesManager.addLives(server, rescuerId, -cost);
        }

        LimboManager.narrator().passageOpened(rescuer, record.lastName(), cost);
        AuditLog.record(server, new AuditEvent(AuditEvent.Type.PASSAGEM_ABERTA, target, record.lastName(),
                LivesManager.livesOf(server, target), record.remainingMillis(), 0,
                LimboData.get(server).forgottenExits(target),
                "aberta por " + rescuer.getGameProfile().getName() + " por " + cost + " vida(s)"));

        return Refusal.OK;
    }

    /** Um vao livre a frente de quem abriu, para a passagem nao nascer dentro da parede. */
    private static Vec3 passageSpot(ServerPlayer rescuer) {
        ServerLevel level = rescuer.serverLevel();
        Vec3 look = rescuer.getLookAngle();
        // Só a direção horizontal: olhar para o chão não deve enterrar a passagem.
        BlockPos ahead = BlockPos.containing(
                rescuer.getX() + look.x * 3, rescuer.getY(), rescuer.getZ() + look.z * 3);

        BlockPos ground = SafeSpot.aroundColumn(level, ahead, 4, ahead.getY() + 4);
        return ground == null ? null : ground.getBottomCenter();
    }

    // --- Atravessar ----------------------------------------------------------------------------

    /**
     * Entrou na passagem: vai para o Limbo, perto de quem foi abrir.
     *
     * <p>O alvo e reconferido aqui, e nao so na abertura: quem chega depois de a pessoa ja ter sido
     * resgatada ficaria no Limbo sem exilio, sem alvo e sem saida. A passagem tambem se fecha sozinha
     * nesse caso ({@code RescuePortalEntity#tick}); esta checagem cobre o mesmo tick.
     *
     * <p>E so aqui, com a travessia confirmada, que a tentativa de resgate e marcada.
     *
     * @return {@code true} se a travessia aconteceu — o chamador para o laco, porque a lista de
     *         entidades que ele estava percorrendo acabou de mudar de dimensao.
     */
    public static boolean enterPassage(ServerPlayer player, UUID target) {
        MinecraftServer server = player.server;
        ServerLevel limbo = server.getLevel(LimboManager.dimension());
        if (limbo == null) {
            return refuse(player, "a dimensao de exilio '" + LimboManager.dimension().location()
                    + "' nao existe neste servidor; confira exileDimension em config/aurorion/vidas-server.toml");
        }
        if (player.level().dimension() == LimboManager.dimension()) return false;

        // Exilado nao usa passagem de resgate para sair do proprio exilio.
        if (LivesManager.isExiled(server, player.getUUID())) return false;
        if (FinaleManager.isDead(player) || !player.isAlive() || player.isSpectator()) return false;

        ExileRecord record = rescuable(server, target);
        if (record == null) return false;

        BlockPos arrival = arrivalNear(server, limbo, target);
        if (arrival == null) return refuse(player, "nao achei chegada segura no exilio");

        ForgottenDoor.authorize(player, limbo.dimension());
        try {
            var moved = player.changeDimension(new DimensionTransition(limbo, arrival.getBottomCenter(),
                    Vec3.ZERO, player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
            // changeDimension devolve null quando alguem cancela EntityTravelToDimensionEvent. O
            // suspeito numero um e o aurorion_portais: ele trata como controlada toda dimensao fora
            // de freeDimensions e, com lockUnscheduledDimensions=true, uma dimensao sem linha de
            // horario fica trancada para sempre. A travessia cai aqui, o jogador nao viaja e nao
            // recebe Vinculo nenhum — e ate aqui isso acontecia em silencio absoluto.
            if (moved == null || moved.level() != limbo) {
                return refuse(player, "a viagem para '" + limbo.dimension().location() + "' foi cancelada por outro"
                        + " sistema (trava de dimensao do aurorion_portais?). O custo ja foi cobrado e a passagem"
                        + " continua aberta");
            }
        } finally {
            ForgottenDoor.clear();
        }

        ServerPlayer arrived = server.getPlayerList().getPlayer(player.getUUID());
        if (arrived == null) {
            return refuse(player, "o jogador saiu da lista do servidor durante a travessia");
        }
        TRIPS.put(arrived.getUUID(), new Trip(target, record.startedAt()));
        giveBonds(arrived, target, record, server);

        // Agora sim alguem foi buscar: a Porta do Esquecido nao arma mais para essa pessoa, tendo o
        // resgate dado certo ou nao. Foi procurada, e a historia dela deixa de ser a de quem o mundo
        // esqueceu. Marcar na abertura apagava a Porta de quem so viu uma passagem abrir e fechar.
        LimboManager.markRescueAttempt(server, target);
        LimboManager.narrator().passageCrossed(arrived);
        return true;
    }

    /**
     * Uma travessia que nao aconteceu nunca pode ser silenciosa.
     *
     * <p>Quem pagou uma vida e andou ate a passagem precisa saber por que nada aconteceu, e a staff
     * precisa do motivo no log — sem isso o sintoma que chega e "o mod nao entrega o Vinculo", que
     * manda procurar o defeito no lugar errado: a entrega esta certa, ela so nunca foi alcancada.
     */
    private static boolean refuse(ServerPlayer player, String reason) {
        AurorionLimbo.LOGGER.error("Resgate: travessia de {} falhou — {}.",
                player.getGameProfile().getName(), reason);
        player.displayClientMessage(LimboText.passageFailed(), true);
        return false;
    }

    /**
     * Onde o resgatador chega.
     *
     * <p>Perto do exilado se ele estiver online, no ponto de chegada do exilio se nao. Nunca <b>em
     * cima</b> dele: achar a pessoa e o trabalho que o resgate cobra, e teleportar em cima do alvo
     * transformaria a busca num clique.
     */
    private static BlockPos arrivalNear(MinecraftServer server, ServerLevel limbo, UUID target) {
        ServerPlayer exiled = server.getPlayerList().getPlayer(target);

        if (exiled != null && exiled.level().dimension() == limbo.dimension()) {
            BlockPos near = exiled.blockPosition().offset(24, 0, 24);
            BlockPos found = LimboSpawn.around(limbo, near, 12);
            if (found != null) return found;
        }
        return LimboSpawn.scattered(limbo);
    }

    private static void giveBonds(ServerPlayer player, UUID target, ExileRecord record, MinecraftServer server) {
        int count = LimboConfig.BOND_COUNT.get();
        int dropped = 0;
        String name = targetName(server, target, record);
        for (int i = 0; i < count; i++) {
            ItemStack bond = bind(new ItemStack(LimboItems.SOUL_BOND.get()), target, player.getUUID(),
                    record.startedAt(), name);
            if (!player.getInventory().add(bond)) {
                player.drop(bond, false);
                dropped++;
            }
        }

        // Reenvio COMPLETO, e nao o diff de broadcastChanges().
        //
        // A entrega acontece no tique seguinte a changeDimension, que acabou de fazer duas coisas: o
        // cliente recriou o jogador ao receber o ClientboundRespawnPacket, e o servidor mandou, por
        // sendAllPlayerInfo, um snapshot do inventario AINDA SEM os Vinculos. broadcastChanges() so
        // manda a diferenca contra esse snapshot; se ela se perder nessa janela, o item fica correto
        // no servidor e invisivel na tela — e o sintoma que chega e "o mod nao entrega o Vinculo".
        // Um ClientboundContainerSetContentPacket a mais por resgate custa nada e fecha a janela.
        player.inventoryMenu.sendAllDataToRemote();

        // O log existe para separar de uma vez as duas causas possiveis deste sintoma: se a contagem
        // aqui for a esperada, o item ESTA no servidor e o problema e de sincronia ou de quem olhou;
        // se vier zero, a falha e na entrega. Sem isto, as duas se parecem do lado de fora.
        AurorionLimbo.LOGGER.info(
                "Resgate: {} recebeu Vinculo(s) atados a {} — configurado={}, no inventario={}, caidos no chao={}.",
                player.getGameProfile().getName(), target, count,
                player.getInventory().countItem(LimboItems.SOUL_BOND.get()), dropped);
    }

    /** O nome que o tooltip mostra: o mesmo que o Oraculo mostrou na hora de escolher. */
    private static String targetName(MinecraftServer server, UUID target, ExileRecord record) {
        CharacterData.Character character = CharacterData.get(server).find(target);
        String name = character != null && character.named() ? character.fullName() : record.lastName();
        return name.length() > OpenOraclePayload.MAX_NAME ? name.substring(0, OpenOraclePayload.MAX_NAME) : name;
    }

    // --- O Vinculo atado -----------------------------------------------------------------------

    /**
     * Ata o Vinculo a um alvo, a quem pagou e a uma queda especifica do alvo.
     *
     * <p>{@code custom_data}, e nao um componente registrado: componente novo e mais uma entrada de
     * registro que o cliente precisa conhecer e que nunca mais sai do modpack (SDD §6.1), para guardar
     * tres campos que so o servidor le. O cliente ve o NBT, mas nao ganha nada com isso — quem decide e
     * {@link #completeRescue}, que confere tudo de novo.
     */
    static ItemStack bind(ItemStack stack, UUID target, UUID payer, long exileStamp, String name) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag bond = new CompoundTag();
            bond.putUUID(BOND_TARGET, target);
            bond.putUUID(BOND_PAYER, payer);
            bond.putLong(BOND_EXILE, exileStamp);
            bond.putString(BOND_NAME, name);
            tag.put(BOND_KEY, bond);
        });
        return stack;
    }

    /** O atado deste Vinculo, ou {@code null} se ele nao tem (item antigo, /give, criativo). */
    @Nullable
    private static CompoundTag binding(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        // getUnsafe: leitura pura, sem copiar o NBT.
        CompoundTag tag = data.getUnsafe();
        if (!tag.contains(BOND_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag bond = tag.getCompound(BOND_KEY);
        return bond.hasUUID(BOND_TARGET) && bond.hasUUID(BOND_PAYER) ? bond : null;
    }

    /** Para o tooltip: a quem este Vinculo foi atado, ou {@code null}. */
    @Nullable
    public static String boundName(ItemStack stack) {
        CompoundTag bond = binding(stack);
        return bond == null || !bond.contains(BOND_NAME, Tag.TAG_STRING) ? null : bond.getString(BOND_NAME);
    }

    /**
     * Recolhe todo Vinculo do inventario (bolsos, armadura, mao secundaria, grade de craft e o item no
     * cursor).
     *
     * <p>Todos, e nao so os desta viagem: fora do Limbo um Vinculo nao tem uso legitimo nenhum, e os
     * antigos, sem atado, sao justamente a sobra que comprava resgates extras. O bau de ender fica de
     * fora de proposito — vasculhar containers e caro e o que estiver la nao funciona sem atado valido.
     */
    public static int stripBonds(ServerPlayer player) {
        int removed = clearBonds(player.getInventory());
        removed += clearBonds(player.inventoryMenu.getCraftSlots());
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.is(LimboItems.SOUL_BOND.get())) {
            removed += carried.getCount();
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        if (removed > 0) {
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
            AurorionLimbo.LOGGER.info("Resgate: {} Vinculo(s) recolhido(s) de {}.", removed,
                    player.getGameProfile().getName());
        }
        return removed;
    }

    private static int clearBonds(Container container) {
        int removed = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && stack.is(LimboItems.SOUL_BOND.get())) {
                removed += stack.getCount();
                container.setItem(slot, ItemStack.EMPTY);
            }
        }
        return removed;
    }

    // --- Quem pode estar no Limbo sem estar exilado ---------------------------------------------

    /**
     * Este jogador esta no meio de um resgate valido?
     *
     * <p>Vale a viagem registrada nesta sessao ou, depois de um reinicio, um Vinculo no inventario
     * pago por ele e atado a alguem que ainda pode ser resgatado. Qualquer outra coisa — o alvo ja
     * saiu, morreu de vez, a viagem era de outra queda — encerra a viagem.
     */
    public static boolean isRescuer(ServerPlayer player) {
        MinecraftServer server = player.server;
        UUID id = player.getUUID();
        if (LivesManager.isExiled(server, id)) return false;

        Trip trip = TRIPS.get(id);
        if (trip != null) {
            ExileRecord record = rescuable(server, trip.target());
            if (record != null && record.startedAt() == trip.exileStamp()) return true;
            TRIPS.remove(id);
        }

        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            CompoundTag bond = binding(inventory.getItem(slot));
            if (bond == null || !id.equals(bond.getUUID(BOND_PAYER))) continue;
            UUID target = bond.getUUID(BOND_TARGET);
            ExileRecord record = rescuable(server, target);
            if (record != null && record.startedAt() == bond.getLong(BOND_EXILE)) {
                TRIPS.put(id, new Trip(target, record.startedAt()));
                return true;
            }
        }
        return false;
    }

    /** Saiu do Limbo (ou entrou fora dele): a viagem acabou e o que sobrou do kit e recolhido. */
    public static void leftLimbo(ServerPlayer player) {
        TRIPS.remove(player.getUUID());
        NEXT_STRAY_TRY.remove(player.getUUID());
        stripBonds(player);
    }

    /**
     * Devolve ao overworld quem esta no Limbo sem motivo para estar la.
     *
     * <p>Cobre o que antes era so uma promessa no log ("tentara no proximo login"): o resgatador cujo
     * retorno falhou, o que chegou depois de o alvo ja ter saido, o terceiro que entrou por uma versao
     * antiga da passagem. Sem isto, todos ficavam no unico estado que nada no mod resolve sozinho — no
     * Limbo, sem prazo, sem Porta e sem registro.
     *
     * <p>Uma vez por segundo, so sobre a lista de jogadores <b>da dimensao do Limbo</b> (quase sempre
     * curta). Staff (criativo, espectador ou permissao 2) fica de fora: visita de staff nao e
     * acidente. Quem ainda tem registro no Limbo tambem: a saida dessa pessoa e do
     * {@code LimboManager}, que fecha o registro junto.
     */
    public static void sweepStrays(MinecraftServer server) {
        ServerLevel limbo = server.getLevel(LimboManager.dimension());
        if (limbo == null) return;
        List<ServerPlayer> inside = limbo.players();
        if (inside.isEmpty()) {
            if (!NEXT_STRAY_TRY.isEmpty()) NEXT_STRAY_TRY.clear();
            return;
        }

        LimboData data = LimboData.get(server);
        long tick = server.getTickCount();
        List<ServerPlayer> strays = null;
        for (int i = 0; i < inside.size(); i++) {
            ServerPlayer player = inside.get(i);
            if (!player.isAlive() || player.isSpectator() || player.isCreative() || player.hasPermissions(2)) continue;
            UUID id = player.getUUID();
            if (FinaleManager.isDead(player) || LivesManager.isExiled(server, id) || data.isTracked(id)) continue;
            if (isRescuer(player)) continue;
            Long next = NEXT_STRAY_TRY.get(id);
            if (next != null && tick < next) continue;
            if (strays == null) strays = new ArrayList<>(2);
            strays.add(player);
        }
        if (strays == null) return;

        // Fora do laco: mudar de dimensao altera a lista de jogadores do Limbo que estava sendo lida.
        for (ServerPlayer player : strays) {
            NEXT_STRAY_TRY.put(player.getUUID(), tick + STRAY_RETRY_TICKS);
            if (LimboManager.returnToOverworld(player)) {
                AurorionLimbo.LOGGER.info("Resgate: {} estava no Limbo sem exilio nem resgate em andamento;"
                        + " devolvido ao overworld.", player.getGameProfile().getName());
            } else {
                AurorionLimbo.LOGGER.warn("Resgate: {} esta no Limbo sem exilio e o retorno falhou;"
                        + " nova tentativa em {} s (ou /limbo retornar).", player.getGameProfile().getName(),
                        STRAY_RETRY_TICKS / 20);
            }
        }
    }

    /** Estado de sessao: nao atravessa de um mundo para o proximo no servidor integrado. */
    public static void reset() {
        TRIPS.clear();
        NEXT_STRAY_TRY.clear();
    }

    // --- Concluir ------------------------------------------------------------------------------

    /**
     * O Vinculo foi usado em alguem.
     *
     * <p>Nao teleporta o exilado: devolve a vida e deixa a varredura do {@code LimboManager} fazer o
     * resto, que e exatamente o que o {@code /vidas dar} da staff ja provoca. Um caminho so para sair
     * do Limbo.
     *
     * <p>Quem usa tem que ser um resgatador de verdade: vivo, <b>nao exilado</b>, dentro do Limbo e
     * dono do Vinculo. Sem a checagem de exilio, um exilado que pegasse do chao o Vinculo de outra
     * pessoa resgatava um companheiro de Limbo — e a varredura, vendo os dois, devolvia so um, o que
     * ainda era o menor dos problemas. O Vinculo tambem precisa estar atado a <b>este</b> alvo, nesta
     * queda.
     */
    public static boolean completeRescue(ServerPlayer rescuer, ServerPlayer exiled, ItemStack stack) {
        MinecraftServer server = rescuer.server;
        UUID exiledId = exiled.getUUID();
        UUID rescuerId = rescuer.getUUID();

        if (FinaleManager.isDead(exiled) || FinaleManager.isDead(rescuer) || !rescuer.isAlive()) return false;

        if (rescuerId.equals(exiledId)) {
            rescuer.displayClientMessage(LimboText.bondSelf(), true);
            return false;
        }
        if (LivesManager.isExiled(server, rescuerId) || rescuer.isSpectator()
                || rescuer.level().dimension() != LimboManager.dimension()) {
            rescuer.displayClientMessage(LimboText.bondNotYours(), true);
            return false;
        }

        ExileRecord record = rescuable(server, exiledId);
        if (record == null || exiled.level().dimension() != LimboManager.dimension()) {
            rescuer.displayClientMessage(LimboText.bondNotExiled(), true);
            return false;
        }

        CompoundTag bond = binding(stack);
        if (bond == null || !rescuerId.equals(bond.getUUID(BOND_PAYER))) {
            rescuer.displayClientMessage(LimboText.bondNotYours(), true);
            return false;
        }
        if (!exiledId.equals(bond.getUUID(BOND_TARGET)) || bond.getLong(BOND_EXILE) != record.startedAt()) {
            rescuer.displayClientMessage(LimboText.bondOtherTarget(), true);
            return false;
        }

        int target = LimboConfig.LIVES_ON_RESCUE.get();
        LivesManager.setLives(server, exiledId, target);

        String name = record.lastName();
        AuditLog.record(server, new AuditEvent(AuditEvent.Type.VINCULO_USADO, exiledId, name,
                target, record.remainingMillis(), 0,
                LimboData.get(server).forgottenExits(exiledId),
                "resgatado por " + rescuer.getGameProfile().getName()));

        stack.shrink(1);
        // O resgate fechou: a viagem acaba e o reserva some agora, nao "um dia".
        TRIPS.remove(rescuerId);
        stripBonds(rescuer);
        LimboManager.narrator().bondUsed(rescuer, name);

        // O resgatador sai junto. Se o retorno falhar agora, sweepStrays tenta de novo a cada poucos
        // segundos: ele deixou de ser resgatador no instante em que o alvo voltou a ter vida.
        if (rescuer.level().dimension() == LimboManager.dimension() && !LimboManager.returnToOverworld(rescuer)) {
            AurorionLimbo.LOGGER.warn("Resgate concluido mas {} nao voltou do Limbo; a varredura tenta de novo.",
                    rescuer.getGameProfile().getName());
        }
        return true;
    }
}
