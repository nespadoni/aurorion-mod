package com.aurorion.limbo.rescue;

import com.aurorion.core.level.SafeSpot;
import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.config.LimboConfig;
import com.aurorion.limbo.exile.ExileRecord;
import com.aurorion.limbo.exile.ForgottenDoor;
import com.aurorion.limbo.exile.LimboData;
import com.aurorion.limbo.exile.LimboManager;
import com.aurorion.limbo.narrate.LimboText;
import com.aurorion.limbo.registry.LimboItems;
import com.aurorion.limbo.report.AuditEvent;
import com.aurorion.limbo.report.AuditLog;
import com.aurorion.vidas.lives.ExileSpot;
import com.aurorion.vidas.lives.LivesData;
import com.aurorion.vidas.lives.LivesManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
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

        active.forEach((id, record) -> {
            if (record.remainingMillis() <= 0 || com.aurorion.core.character.CharacterData.get(server).isDead(id)) return;
            out.add(new ExileInfo(
                id,
                record.lastName(),
                record.remainingMillis(),
                record.rescueAttempted(),
                server.getPlayerList().getPlayer(id) != null));
        });

        // Prazo mais curto primeiro: quem esta mais perto de sumir aparece no topo.
        out.sort((a, b) -> Long.compare(a.remainingMillis(), b.remainingMillis()));
        return out;
    }

    // --- Abrir a passagem ----------------------------------------------------------------------

    /**
     * Cobra a vida e abre a passagem na frente de quem pagou.
     *
     * <p>A ordem importa: a vida so e debitada <b>depois</b> de a passagem existir no mundo. Se o
     * lugar nao servir, ninguem paga por uma passagem que nao abriu.
     */
    public static Refusal openPassage(ServerPlayer rescuer, UUID target) {
        MinecraftServer server = rescuer.server;
        UUID rescuerId = rescuer.getUUID();

        if (rescuerId.equals(target)) return Refusal.SELF;
        if (LivesManager.isExiled(server, rescuerId)) return Refusal.RESCUER_EXILED;

        ExileRecord record = LimboData.get(server).record(target);
        if (record == null || record.remainingMillis() <= 0 || !LivesManager.isExiled(server, target)
                || com.aurorion.core.character.CharacterData.get(server).isDead(target)) return Refusal.NOT_EXILED;

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

        // Alguem foi buscar: a Porta do Esquecido nao arma mais para essa pessoa, tendo o resgate
        // dado certo ou nao. Foi procurada, e a historia dela deixa de ser a de quem o mundo esqueceu.
        LimboManager.markRescueAttempt(server, target);

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
     * @return {@code true} se a travessia aconteceu — o chamador para o laco, porque a lista de
     *         entidades que ele estava percorrendo acabou de mudar de dimensao.
     */
    public static boolean enterPassage(ServerPlayer player, UUID target) {
        MinecraftServer server = player.server;
        ServerLevel limbo = server.getLevel(LimboManager.dimension());
        if (limbo == null || player.level().dimension() == LimboManager.dimension()) return false;

        // Exilado nao usa passagem de resgate para sair do proprio exilio.
        if (LivesManager.isExiled(server, player.getUUID())) return false;

        BlockPos arrival = arrivalNear(server, limbo, target);
        if (arrival == null) return false;

        ForgottenDoor.authorize(player, limbo.dimension());
        try {
            var moved = player.changeDimension(new DimensionTransition(limbo, arrival.getBottomCenter(),
                    Vec3.ZERO, player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
            if (moved == null || moved.level() != limbo) return false;
        } finally {
            ForgottenDoor.clear();
        }

        ServerPlayer arrived = server.getPlayerList().getPlayer(player.getUUID());
        if (arrived != null) {
            giveBonds(arrived);
            LimboManager.narrator().passageCrossed(arrived);
        }
        return true;
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
            BlockPos found = SafeSpot.aroundColumn(limbo, near, 12, near.getY() + 24);
            if (found != null) return found;
        }
        return ExileSpot.resolve(limbo, LivesData.get(server));
    }

    private static void giveBonds(ServerPlayer player) {
        int count = LimboConfig.BOND_COUNT.get();
        for (int i = 0; i < count; i++) {
            ItemStack bond = new ItemStack(LimboItems.SOUL_BOND.get());
            if (!player.getInventory().add(bond)) {
                player.drop(bond, false);
            }
        }

        // A entrega acontece imediatamente depois de changeDimension, fora do fluxo comum de coleta.
        // O inventario do servidor ja esta correto, mas sem este broadcast o cliente pode continuar
        // mostrando o snapshot anterior ate outra alteracao de slot ou uma reconexao.
        player.inventoryMenu.broadcastChanges();
    }

    // --- Concluir ------------------------------------------------------------------------------

    /**
     * O Vinculo foi usado em alguem.
     *
     * <p>Nao teleporta o exilado: devolve a vida e deixa a varredura do {@code LimboManager} fazer o
     * resto, que e exatamente o que o {@code /vidas dar} da staff ja provoca. Um caminho so para sair
     * do Limbo.
     */
    public static boolean completeRescue(ServerPlayer rescuer, ServerPlayer exiled, ItemStack stack) {
        MinecraftServer server = rescuer.server;
        UUID exiledId = exiled.getUUID();

        ExileRecord eligible = LimboData.get(server).record(exiledId);
        if (eligible == null || eligible.remainingMillis() <= 0
                || com.aurorion.limbo.finale.FinaleManager.isDead(exiled)
                || com.aurorion.limbo.finale.FinaleManager.isDead(rescuer)) return false;

        if (rescuer.getUUID().equals(exiledId)) {
            rescuer.displayClientMessage(LimboText.bondSelf(), true);
            return false;
        }
        if (!LivesManager.isExiled(server, exiledId) || !LimboData.get(server).isTracked(exiledId)) {
            rescuer.displayClientMessage(LimboText.bondNotExiled(), true);
            return false;
        }

        int target = LimboConfig.LIVES_ON_RESCUE.get();
        LivesManager.setLives(server, exiledId, target);

        ExileRecord record = LimboData.get(server).record(exiledId);
        String name = record != null ? record.lastName() : exiled.getGameProfile().getName();

        AuditLog.record(server, new AuditEvent(AuditEvent.Type.VINCULO_USADO, exiledId, name,
                target, record != null ? record.remainingMillis() : 0L, 0,
                LimboData.get(server).forgottenExits(exiledId),
                "resgatado por " + rescuer.getGameProfile().getName()));

        stack.shrink(1);
        LimboManager.narrator().bondUsed(rescuer, name);

        // O resgatador sai junto. Sem isto ele ficaria no Limbo sem estar exilado — sem prazo, sem
        // Porta e sem registro, que e o unico estado do mod que nada consegue resolver sozinho.
        if (rescuer.level().dimension() == LimboManager.dimension() && !LimboManager.returnToOverworld(rescuer)) {
            AurorionLimbo.LOGGER.warn("Resgate concluido mas {} nao voltou do Limbo; tentara no proximo login.",
                    rescuer.getGameProfile().getName());
        }
        return true;
    }
}
