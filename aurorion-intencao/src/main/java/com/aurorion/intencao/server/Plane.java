package com.aurorion.intencao.server;

import com.aurorion.core.character.CharacterData;
import com.aurorion.core.compat.TwilightTeleportCompat;
import com.aurorion.core.house.HouseGate;
import com.aurorion.core.level.SafeSpot;
import com.aurorion.intencao.AurorionIntencao;
import com.aurorion.intencao.config.IntencaoConfig;
import com.aurorion.intencao.entity.EchoEntity;
import com.aurorion.intencao.entity.FragmentEntity;
import com.aurorion.intencao.network.IntencaoNetwork;
import com.aurorion.intencao.network.ScenePayload;
import com.aurorion.intencao.network.Tone;
import com.aurorion.intencao.network.WorldPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * O ritual inteiro: chamar, levar, proteger, dar o veredito e devolver.
 *
 * <p>Linha do tempo de uma batalha:
 *
 * <ol>
 *   <li><b>Chamado</b> ({@link #start}): arena montada, tela dos participantes escurece, selo no chao
 *       para quem esta perto. O corpo fica pesado ({@value #CALL_TICKS} ticks) para o Eco nascer
 *       onde o selo apareceu.</li>
 *   <li><b>Plano</b> ({@link #pull}): ancora gravada, Eco no lugar, teleporte. Sem limite de tempo —
 *       a mesa e quem decide. Dano e construcao cancelados; quem sai da borda volta ao seu lugar.</li>
 *   <li><b>Veredito</b> ({@link #verdict}): cada um ve o seu resultado por {@value #VERDICT_TICKS}
 *       ticks, depois volta pela ancora ({@link #restore}). Vencedor com aura, perdedor de joelhos.</li>
 * </ol>
 *
 * <p>Custo: tudo e por evento. O unico laco e a borda da arena, a cada 10 ticks, so sobre os
 * participantes de sessoes abertas — e nada quando nao ha batalha.
 */
@EventBusSubscriber(modid = AurorionIntencao.MOD_ID)
public final class Plane {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            AurorionIntencao.id("plano_intencao"));
    public static final int CALL_TICKS = 100;
    public static final int VERDICT_TICKS = 70;
    public static final int AURA_TICKS = 1200;
    public static final int MAX_MEMBERS = 8;
    private static final int CHECK_INTERVAL = 10;
    /** Sem sistema de Casas (ou sem Casa), cada lado ainda precisa de uma cor sua. */
    private static final int[] PALETTE = {0x6FA8FF, 0xFF7A5C, 0x9BE07A, 0xD9A6FF, 0xFFD36B, 0x6FE3D9, 0xFF8FC7, 0xC7C7C7};

    private Plane() {
    }

    public static boolean inPlane(Entity entity) {
        return entity.level().dimension().equals(DIMENSION);
    }

    // --- Chamado -------------------------------------------------------------------------------

    /**
     * @param sides lado de cada participante, na mesma ordem: 0 para "jogadores", 1 para "rivais".
     * @return {@code null} se a batalha comecou, ou o motivo de nao ter comecado.
     */
    @Nullable
    public static Component start(MinecraftServer server, Theme theme, List<ServerPlayer> players, List<Integer> sides) {
        if (players.size() < 2) return tr("erro.poucos");
        if (players.size() > MAX_MEMBERS) return tr("erro.muitos", MAX_MEMBERS);
        ServerLevel plane = server.getLevel(DIMENSION);
        if (plane == null) return tr("erro.plano");
        IntencaoData data = IntencaoData.get(server);
        for (ServerPlayer player : players) {
            if (data.sessionOf(player.getUUID()) != null || data.anchor(player.getUUID()) != null || inPlane(player)) {
                return tr("erro.ocupado", player.getDisplayName());
            }
            if (!player.isAlive()) return tr("erro.morto", player.getDisplayName());
        }
        BlockPos center = data.allocate();
        if (center == null) return tr("erro.cheio");

        List<UUID> ids = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            Integer house = HouseGate.colorOf(server, player.getUUID());
            int color = house != null ? house & 0xFFFFFF : PALETTE[i % PALETTE.length];
            // Dois rivais da mesma Casa teriam fatias, fios e nomes iguais: o segundo pega da paleta.
            for (int k = 0; colors.contains(color) && k < PALETTE.length; k++) color = PALETTE[(i + k) % PALETTE.length];
            ids.add(player.getUUID());
            colors.add(color);
            names.add(nameOf(server, player));
        }
        IntencaoData.Session session = data.open(theme.id(), center, ids, colors, names, sides, now(server));
        try {
            Arena.build(plane, center, theme, colors, session.id, plane.random);
        } catch (RuntimeException failure) {
            data.close(session.id);
            AurorionIntencao.LOGGER.error("Falha ao montar a arena da batalha de intencao em {}", center, failure);
            return tr("erro.arena");
        }

        List<ScenePayload.Member> members = members(session);
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            IntencaoNetwork.sendScene(player, new ScenePayload(ScenePayload.Kind.CALL, members, i, center, theme.fog(),
                    theme.ambience().toString(), theme.whisper().toString(), 0, 0, theme.call()));
            IntencaoNetwork.sendWorldAt(player.serverLevel(), WorldPayload.Kind.SEAL, player.position(), colors.get(i),
                    CALL_TICKS + 20);
            // O chamado prende: quase sem andar, para o Eco nascer onde o selo esta.
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CALL_TICKS + 10, 6, false, false, false));
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.2F, .5F);
        }
        UUID id = session.id;
        Scheduler.after(server.getTickCount(), CALL_TICKS, () -> enterAll(server, id));
        AurorionIntencao.LOGGER.info("Batalha de intencao {} aberta (tema {}): {}", id, theme.id(), names);
        return null;
    }

    private static void enterAll(MinecraftServer server, UUID id) {
        IntencaoData.Session session = IntencaoData.get(server).session(id);
        if (session == null || session.state != IntencaoData.State.ACTIVE) return;
        for (UUID member : session.members) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null && player.isAlive()) pull(player, session);
        }
    }

    /** Leva (ou reconecta) um participante ao plano. Chamar com ele ja la so reenvia a cena. */
    static boolean pull(ServerPlayer player, IntencaoData.Session session) {
        MinecraftServer server = player.server;
        ServerLevel plane = server.getLevel(DIMENSION);
        int index = session.indexOf(player.getUUID());
        if (plane == null || index < 0) return false;
        IntencaoData data = IntencaoData.get(server);
        Theme theme = Theme.resolve(session.theme);

        if (!inPlane(player)) {
            player.stopRiding();
            player.stopUsingItem();
            player.stopFallFlying();
            player.closeContainer();
            IntencaoData.Anchor anchor = data.anchor(player.getUUID());
            if (anchor == null || !anchor.session().equals(session.id)) {
                if (anchor != null) discardEcho(server, anchor);
                // Chamado no meio de um pulo ou de um voo: Eco e volta ficam no chao abaixo, e nao no ar —
                // voltar para o ponto exato seria uma queda, as vezes mortal.
                Vec3 ground = groundBelow(player);
                EchoEntity echo = EchoEntity.spawn(player, ground, session.colors.get(index), session.names.get(index));
                data.putAnchor(player.getUUID(), new IntencaoData.Anchor(session.id,
                        player.level().dimension().location(), ground, player.getYRot(), player.getXRot(),
                        echo.getUUID()));
            }
            Vec3 spawn = spawnPoint(session, theme, index);
            travel(player, plane, spawn, facing(session, spawn), 0);
            if (!inPlane(player)) {
                // Outro mod recusou a viagem: nada de Eco orfao nem ancora para um lugar onde ninguem foi.
                IntencaoData.Anchor left = data.anchor(player.getUUID());
                if (left != null) discardEcho(server, left);
                data.removeAnchor(player.getUUID());
                player.sendSystemMessage(tr("erro.recusado"));
                AurorionIntencao.LOGGER.warn("Teleporte de {} para o Plano da Intencao foi recusado.", player.getGameProfile().getName());
                return false;
            }
            player.resetFallDistance();
            plane.playSound(null, spawn.x, spawn.y, spawn.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, .5F);
        }
        sendPlane(player, session, theme, index);
        return true;
    }

    private static void sendPlane(ServerPlayer player, IntencaoData.Session session, Theme theme, int index) {
        int elapsed = (int) Math.max(0, (now(player.server) - session.startedAt) / 20);
        IntencaoNetwork.sendScene(player, new ScenePayload(ScenePayload.Kind.PLANE, members(session), index,
                session.center, theme.fog(), theme.ambience().toString(), theme.whisper().toString(),
                session.tone.ordinal(), elapsed, theme.name()));
    }

    // --- Durante -------------------------------------------------------------------------------

    public static void setTone(MinecraftServer server, IntencaoData.Session session, Tone tone) {
        session.tone = tone;
        IntencaoData.get(server).changed();
        for (UUID member : session.members) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null && inPlane(player)) {
                IntencaoNetwork.sendScene(player, ScenePayload.simple(ScenePayload.Kind.TONE, tone.ordinal(), ""));
            }
        }
        ServerLevel plane = server.getLevel(DIMENSION);
        if (plane != null && tone == Tone.RUPTURA) {
            BlockPos c = session.center;
            plane.playSound(null, c.getX() + .5, c.getY() + 2, c.getZ() + .5, SoundEvents.SCULK_SHRIEKER_SHRIEK,
                    SoundSource.AMBIENT, 2F, .5F);
        }
    }

    public static void touchFragment(ServerPlayer player, UUID sessionId, FragmentEntity fragment) {
        IntencaoData.Session session = IntencaoData.get(player.server).session(sessionId);
        if (session == null) return;
        Theme theme = Theme.resolve(session.theme);
        String phrase = theme.phrase(player.getRandom().nextInt(1 << 16));
        IntencaoNetwork.sendScene(player, ScenePayload.simple(ScenePayload.Kind.PHRASE, 0, phrase));
        fragment.level().playSound(null, fragment.getX(), fragment.getY(), fragment.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, .7F + player.getRandom().nextFloat() * .3F);
    }

    // --- Veredito ------------------------------------------------------------------------------

    /**
     * @param winner {@code null} e empate.
     * @return {@code false} se o veredito ja tinha sido dado.
     */
    public static boolean verdict(MinecraftServer server, IntencaoData.Session session, @Nullable UUID winner) {
        if (session.state != IntencaoData.State.ACTIVE) return false;
        session.state = IntencaoData.State.ENDING;
        IntencaoData.get(server).changed();
        Theme theme = Theme.resolve(session.theme);
        List<ScenePayload.Member> members = members(session);
        for (int i = 0; i < session.members.size(); i++) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.members.get(i));
            if (player == null) continue;
            ScenePayload.Outcome outcome = outcome(session, session.members.get(i), winner);
            String text = switch (outcome) {
                case WIN -> theme.win();
                case LOSE -> theme.lose();
                case DRAW -> theme.draw();
            };
            IntencaoNetwork.sendScene(player, new ScenePayload(ScenePayload.Kind.VERDICT, members, i, session.center,
                    theme.fog(), theme.ambience().toString(), theme.whisper().toString(), outcome.ordinal(), 0, text));
        }
        UUID id = session.id;
        Scheduler.after(server.getTickCount(), VERDICT_TICKS, () -> finish(server, id, winner));
        AurorionIntencao.LOGGER.info("Batalha de intencao {} encerrada; vencedor: {}", id, winner == null ? "empate" : winner);
        return true;
    }

    private static void finish(MinecraftServer server, UUID id, @Nullable UUID winner) {
        IntencaoData data = IntencaoData.get(server);
        IntencaoData.Session session = data.session(id);
        if (session == null) return;
        data.close(id);
        for (int i = 0; i < session.members.size(); i++) {
            UUID member = session.members.get(i);
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            // Offline: a ancora fica, e o login devolve (sem a sessao, que ja nao existe).
            if (player == null) continue;
            // Um por vez, isolado: uma falha na volta de um nao pode deixar os outros presos no plano.
            try {
                finishOne(player, session, i, winner);
            } catch (RuntimeException failure) {
                AurorionIntencao.LOGGER.error("Falha ao devolver {} no fim da batalha {}", member, id, failure);
            }
        }
    }

    private static void finishOne(ServerPlayer player, IntencaoData.Session session, int index, @Nullable UUID winner) {
        UUID member = session.members.get(index);
        int color = session.colors.get(index);
        IntencaoData.Anchor anchor = IntencaoData.get(player.server).anchor(member);
        boolean back;
        if (anchor != null && anchor.session().equals(session.id)) back = restore(player, anchor, color);
        else back = !inPlane(player) || recover(player);
        if (!back) return;
        switch (outcome(session, member, winner)) {
            case WIN -> {
                IntencaoNetwork.sendWorldOn(player, WorldPayload.Kind.AURA, color, AURA_TICKS);
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.25F);
            }
            case LOSE -> {
                // Volta de joelhos (o cliente agacha) e com a vista turva. Sem icone: e cena, nao status.
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, false));
            }
            case DRAW -> {
            }
        }
    }

    /** O veredito e por lado: quem esta no mesmo lado do vencedor vence junto. */
    private static ScenePayload.Outcome outcome(IntencaoData.Session session, UUID member, @Nullable UUID winner) {
        if (winner == null) return ScenePayload.Outcome.DRAW;
        int side = session.sideOf(winner);
        if (side < 0) return member.equals(winner) ? ScenePayload.Outcome.WIN : ScenePayload.Outcome.LOSE;
        return session.sideOf(member) == side ? ScenePayload.Outcome.WIN : ScenePayload.Outcome.LOSE;
    }

    // --- Volta ---------------------------------------------------------------------------------

    /**
     * Devolve pela ancora. A ancora sai <b>antes</b> do teleporte, para o evento de troca de dimensao
     * nao confundir a volta com uma fuga; se outro mod recusar a viagem, ela e reposta — a ancora e o
     * unico registro de para onde a pessoa volta, e nunca pode ser perdida por um veto.
     */
    static boolean restore(ServerPlayer player, IntencaoData.Anchor anchor, int color) {
        MinecraftServer server = player.server;
        IntencaoData data = IntencaoData.get(server);
        data.removeAnchor(player.getUUID());
        discardEcho(server, anchor);
        if (inPlane(player)) {
            ServerLevel home = server.getLevel(ResourceKey.create(Registries.DIMENSION, anchor.dimension()));
            Vec3 to = anchor.pos();
            if (home == null || home.dimension().equals(DIMENSION) || !valid(to)) {
                home = server.overworld();
                to = Vec3.atBottomCenterOf(home.getSharedSpawnPos());
            }
            ServerLevel destination = home;
            Vec3 landing = safeLanding(destination, to);
            float yaw = Float.isFinite(anchor.yaw()) ? anchor.yaw() : 0;
            float pitch = Float.isFinite(anchor.pitch()) ? anchor.pitch() : 0;
            try {
                travel(player, destination, landing, yaw, pitch);
            } catch (RuntimeException failure) {
                AurorionIntencao.LOGGER.error("Falha ao devolver {} do Plano da Intencao", player.getUUID(), failure);
            }
            if (inPlane(player)) {
                data.putAnchor(player.getUUID(), anchor.withEcho(null));
                AurorionIntencao.LOGGER.warn("Volta de {} do Plano da Intencao recusada; a ancora foi preservada.",
                        player.getGameProfile().getName());
                // A cena acabou mesmo sem a volta: o HUD nao pode fingir uma batalha que ja fechou.
                IntencaoNetwork.sendScene(player, ScenePayload.simple(ScenePayload.Kind.LEAVE, 0, ""));
                player.sendSystemMessage(tr("erro.volta"));
                return false;
            }
        }
        player.resetFallDistance();
        IntencaoNetwork.sendScene(player, ScenePayload.simple(ScenePayload.Kind.LEAVE, 0, ""));
        WorldPayload wave = new WorldPayload(WorldPayload.Kind.WAVE, -1, player.position(), color, 30);
        IntencaoNetwork.sendWorldAt(player.serverLevel(), wave.kind(), wave.pos(), color, wave.ttl());
        // Logo depois da troca de dimensao o proprio chunk ainda esta "pendente" para quem chegou: o envio
        // por chunk nao o alcanca. Ele recebe direto.
        IntencaoNetwork.sendWorldTo(player, wave);
        return true;
    }

    /** Quem esta no plano sem ancora (/tp da staff, NBT antigo): para o spawn, nunca preso no vazio. */
    public static boolean recover(ServerPlayer player) {
        MinecraftServer server = player.server;
        IntencaoData.Anchor anchor = IntencaoData.get(server).anchor(player.getUUID());
        if (anchor != null) return restore(player, anchor, 0xFFFFFF);
        if (!inPlane(player)) {
            IntencaoNetwork.sendScene(player, ScenePayload.simple(ScenePayload.Kind.LEAVE, 0, ""));
            return true;
        }
        ServerLevel overworld = server.overworld();
        IntencaoData.Anchor spawn = new IntencaoData.Anchor(new UUID(0, 0), overworld.dimension().location(),
                Vec3.atBottomCenterOf(overworld.getSharedSpawnPos()), player.getYRot(), 0, null);
        // Gravada antes: se a volta for vetada, a proxima tentativa ja tem para onde ir.
        IntencaoData.get(server).putAnchor(player.getUUID(), spawn);
        return restore(player, spawn, 0xFFFFFF);
    }

    // --- Viagem do ritual ----------------------------------------------------------------------

    /*
     * O aurorion_portais tranca toda dimensao fora de freeDimensions, e o plano nao esta la por padrao:
     * sem isto, ninguem entraria — e, pior, quem entrasse nao sairia. Mesmo desenho da Porta do Esquecido
     * do Limbo (SDD §9.2): a autorizacao vale para um jogador, um destino e um tick, e um listener de
     * prioridade mais baixa, que fala por ultimo, desfaz o veto so dessa viagem. Nenhum buraco no portais.
     */
    @Nullable private static ServerPlayer authorized;
    @Nullable private static ResourceKey<Level> authorizedTo;
    private static int authorizedTick;

    /** Teleporte do ritual: sincrono (Twilight Teleport) e autorizado por cima de trancas de dimensao. */
    private static void travel(ServerPlayer player, ServerLevel to, Vec3 at, float yaw, float pitch) {
        authorized = player;
        authorizedTo = to.dimension();
        authorizedTick = player.server.getTickCount();
        try {
            TwilightTeleportCompat.immediateRun(() -> player.teleportTo(to, at.x, at.y, at.z, Set.of(), yaw, pitch));
        } finally {
            authorized = null;
            authorizedTo = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (authorized == null || event.getEntity() != authorized || event.getDimension() != authorizedTo
                || authorizedTick != authorized.server.getTickCount()) return;
        // Consumida: vale para esta travessia so, nunca para uma viagem futura.
        authorized = null;
        authorizedTo = null;
        if (event.isCanceled()) event.setCanceled(false);
    }

    /** O chao logo abaixo de quem esta no ar; no chao (ou sem chao a 24 blocos), a propria posicao. */
    private static Vec3 groundBelow(ServerPlayer player) {
        if (player.onGround() || player.isInWater() || player.isInLava()) return player.position();
        BlockPos feet = SafeSpot.scanDown(player.serverLevel(), player.getBlockX(), player.getBlockZ(), player.getBlockY());
        if (feet == null || player.getBlockY() - feet.getY() > 24) return player.position();
        return new Vec3(player.getX(), feet.getY(), player.getZ());
    }

    private static void discardEcho(MinecraftServer server, IntencaoData.Anchor anchor) {
        if (anchor.echo() == null) return;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, anchor.dimension()));
        Entity echo = level == null ? null : level.getEntity(anchor.echo());
        // Chunk descarregado: o proprio Eco percebe que a ancora sumiu e se desfaz quando carregar.
        if (echo instanceof EchoEntity) echo.discard();
    }

    private static Vec3 safeLanding(ServerLevel home, Vec3 original) {
        BlockPos at = BlockPos.containing(original);
        // Se alguem construiu em cima do Eco, procura espaco para cima sem cavar o mapa.
        for (int y = 0; y <= 12; y++) {
            BlockPos feet = at.above(y);
            if (home.getBlockState(feet).getCollisionShape(home, feet).isEmpty()
                    && home.getBlockState(feet.above()).getCollisionShape(home, feet.above()).isEmpty()) {
                return y == 0 ? original : Vec3.atBottomCenterOf(feet);
            }
        }
        BlockPos near = SafeSpot.nearestVertical(home, at, 24);
        return near != null ? Vec3.atBottomCenterOf(near) : Vec3.atBottomCenterOf(home.getSharedSpawnPos());
    }

    private static boolean valid(Vec3 pos) {
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z)
                && Math.abs(pos.x) < 30_000_000 && Math.abs(pos.z) < 30_000_000 && Math.abs(pos.y) < 30_000_000;
    }

    // --- Geometria -----------------------------------------------------------------------------

    /** O primeiro participante em -X, os outros distribuidos em volta. */
    public static double spawnAngle(int index, int count) {
        return Math.PI + Math.PI * 2 * index / Math.max(1, count);
    }

    /**
     * De qual participante e a fatia da arena no ponto {@code (x, z)} relativo ao centro. Fatias iguais,
     * cada uma centrada onde o seu dono aparece. Usada pela arena (vitral) e pelo cliente (poeira).
     */
    public static int side(double x, double z, int count) {
        int n = Math.max(1, count);
        double angle = Math.atan2(z, x) - spawnAngle(0, n);
        double slice = Math.PI * 2 / n;
        return Math.floorMod((int) Math.floor((angle + slice / 2) / slice), n);
    }

    static Vec3 spawnPoint(IntencaoData.Session session, Theme theme, int index) {
        double angle = spawnAngle(index, session.members.size());
        BlockPos c = session.center;
        return new Vec3(c.getX() + .5 + Math.cos(angle) * theme.spawnRadius(), c.getY(),
                c.getZ() + .5 + Math.sin(angle) * theme.spawnRadius());
    }

    /** Yaw de quem esta em {@code from} olhando para o centro. */
    private static float facing(IntencaoData.Session session, Vec3 from) {
        double dx = session.center.getX() + .5 - from.x;
        double dz = session.center.getZ() + .5 - from.z;
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
    }

    // --- Utilidades ----------------------------------------------------------------------------

    static List<ScenePayload.Member> members(IntencaoData.Session session) {
        List<ScenePayload.Member> list = new ArrayList<>(session.members.size());
        for (int i = 0; i < session.members.size(); i++) {
            list.add(new ScenePayload.Member(session.members.get(i), session.names.get(i), session.colors.get(i)));
        }
        return list;
    }

    /** Nome do personagem quando ha um; senao o nome exibido. O nick da conta so como ultimo recurso. */
    private static String nameOf(MinecraftServer server, ServerPlayer player) {
        CharacterData.Character character = CharacterData.get(server).find(player.getUUID());
        String name = character != null && character.named() ? character.fullName() : player.getDisplayName().getString();
        // O nome exibido inclui prefixo/sufixo de time e pode ser trocado por outros mods: sem teto, um nome
        // acima de 64 caracteres derrubaria a conexao de todos os participantes ao codificar a cena.
        return name.length() <= 48 ? name : name.substring(0, 47) + "…";
    }

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    static MutableComponent tr(String key, Object... args) {
        return Component.translatable("aurorion_intencao." + key, args);
    }

    // --- Eventos -------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(Theme.THEMES.listener());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Scheduler.run(server.getTickCount());
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;
        IntencaoData data = IntencaoData.get(server);
        if (!data.hasSessions()) return;
        long now = now(server);
        long timeout = IntencaoConfig.TIMEOUT_MINUTES.get() * 1200L;
        for (IntencaoData.Session session : List.copyOf(data.sessions())) {
            if (session.state == IntencaoData.State.ACTIVE && now - session.startedAt > timeout) {
                AurorionIntencao.LOGGER.warn("Batalha de intencao {} passou do limite de tempo; desfeita em empate.", session.id);
                verdict(server, session, null);
                continue;
            }
            Theme theme = Theme.resolve(session.theme);
            for (int i = 0; i < session.members.size(); i++) {
                ServerPlayer player = server.getPlayerList().getPlayer(session.members.get(i));
                if (player != null && inPlane(player)) keepInside(player, session, theme, i);
            }
        }
    }

    private static void keepInside(ServerPlayer player, IntencaoData.Session session, Theme theme, int index) {
        BlockPos c = session.center;
        double dx = player.getX() - (c.getX() + .5), dz = player.getZ() - (c.getZ() + .5);
        double limit = theme.radius() + 1;
        if (dx * dx + dz * dz <= limit * limit && player.getY() > c.getY() - 4 && player.getY() < c.getY() + 48) return;
        Vec3 spawn = spawnPoint(session, theme, index);
        player.connection.teleport(spawn.x, spawn.y, spawn.z, facing(session, spawn), 0);
        player.resetFallDistance();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) settleLater(player, 20);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) settleLater(player, 5);
    }

    private static void settleLater(ServerPlayer player, int ticks) {
        // Um segundo depois do login: o cliente precisa ter o mundo para receber teleporte e cena.
        Scheduler.after(player.server.getTickCount(), ticks, () -> {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) == player && player.isAlive()) settle(player);
        });
    }

    /** Poe cada um onde deveria estar: no plano se a batalha segue, em casa se ela acabou. */
    private static void settle(ServerPlayer player) {
        IntencaoData data = IntencaoData.get(player.server);
        IntencaoData.Session session = data.sessionOf(player.getUUID());
        if (session != null) {
            // ENDING: o veredito esta na tela de alguem; o fim devolve quem estiver online.
            if (session.state == IntencaoData.State.ACTIVE) pull(player, session);
            return;
        }
        IntencaoData.Anchor anchor = data.anchor(player.getUUID());
        if (anchor != null) restore(player, anchor, 0xFFFFFF);
        else if (inPlane(player)) recover(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getFrom().equals(DIMENSION) || event.getTo().equals(DIMENSION)) return;
        // Nossa volta tira a ancora antes de viajar. Ancora ainda aqui = saiu por outro caminho
        // (/spawn, /tp da staff): o Eco nao pode ficar fingindo um transe que acabou.
        IntencaoData data = IntencaoData.get(player.server);
        IntencaoData.Anchor anchor = data.anchor(player.getUUID());
        if (anchor == null) return;
        discardEcho(player.server, anchor);
        data.removeAnchor(player.getUUID());
        IntencaoNetwork.sendScene(player, ScenePayload.simple(ScenePayload.Kind.LEAVE, 0, ""));
        AurorionIntencao.LOGGER.info("{} saiu do Plano da Intencao por fora do ritual.", player.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && inPlane(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player != null && !player.isCreative() && inPlane(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && !player.isCreative() && inPlane(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Scheduler.clear();
    }

    /** Para o comando: o id da sessao, curto. */
    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
