package com.aurorion.magia.passive;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.core.compat.TwilightTeleportCompat;
import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.spell.AurorionSpell;
import com.aurorion.magia.spell.Binding;
import com.aurorion.magia.spell.SoulRealm;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Bolso do Vazio</b> — Iris, representante do Vazio.
 *
 * <p>Iris tem um <b>vazio</b> so dela: {@code aurorion_magia:vazio} nao gera nada — nem chao, nem
 * terreno. Na primeira entrada, a celula dela ganha <b>um bloco</b> ({@code [vazio].blocoInicial}) para
 * comecar a construir; o que ela construir fica, e e sempre o mesmo lugar. La dentro, a dona tem o
 * <b>criativo</b>; os marcados ficam no modo que tinham. Enquanto ela estiver viva, la dentro e seguro:
 * ninguem que ela levou leva dano, e quem cai no vazio volta para o bloco inicial.
 *
 * <p><b>Nada entra, nada sai</b> ({@link PocketBelongings}): na porta, o inventario e os slots de
 * acessorio de cada um ficam guardados; na volta, o que foi pego la dentro some e o que era da pessoa
 * volta. Bau do ender (uma ponte para fora), cama e ancora de renascimento ficam bloqueados la dentro, e
 * nenhuma entidade atravessa a dimensao por portal (a trava do {@code MagiaServerEvents}).
 *
 * <h2>Tecla</h2>
 *
 * <ul>
 *   <li><b>Fora</b>, tecla: entra no bolso, levando os marcados que estiverem ate {@code raioLevar}.</li>
 *   <li><b>Fora</b>, Shift + tecla olhando alguem: marca (ou desmarca) essa pessoa. Ate
 *       {@code maximoMarcados}.</li>
 *   <li><b>Dentro</b>, tecla: sai, e todos voltam para onde estavam.</li>
 *   <li><b>Dentro</b>, Shift + tecla olhando um marcado: devolve so ele a realidade.</li>
 * </ul>
 *
 * <h2>A barreira</h2>
 *
 * <p>Onde Iris entrou fica uma <b>fenda</b>. Quanto mais marcados ela mantem la dentro, mais dificil e
 * sustentar a barreira: com 0 ou 1, ninguem entra; com 2, quem ficar agachado na fenda por
 * {@code segundosInvasaoMediana} rompe; com 3 ou mais, bastam {@code segundosInvasaoFacil}. Quem rompe e
 * puxado para dentro, e o bolso deixa de ser seguro ate Iris sair.
 *
 * <h2>Custo</h2>
 *
 * <p>Zero sem Iris no bolso. Com ela dentro, o relogio e o efeito {@code bolso_do_vazio}, uma vez por
 * segundo: uma volta nos membros (ate uns cinco) e uma em {@code level.players()} da dimensao da fenda,
 * so se o chunk da fenda estiver carregado. O dano protegido e uma consulta de mapa por golpe dentro do
 * bolso. Entrar guarda os pertences de cada um e grava o arquivo na hora — uma escrita por entrada.
 */
public final class VoidPocket {
    /**
     * Dimensao nova, e nao a antiga {@code bolso_do_vazio}: aquela era uma copia do overworld e ja tem
     * terreno gerado em disco, que apareceria em volta. A pasta antiga fica sem uso no mundo.
     */
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, AurorionMagia.id("vazio"));
    /** Altura do bloco inicial; quem chega fica em cima dele. */
    private static final int FLOOR_Y = 64;
    private static final String BELONGINGS = "Belongings";
    private static final String GAME_MODE = "GameMode";
    private static final double RIFT_RADIUS_SQR = 3 * 3;
    private static final double MARK_RANGE = 6;

    /** Quanto tempo cada invasor ja esta forcando a fenda, por dona. So em memoria: e pressao, nao estado. */
    private static final Map<UUID, Map<UUID, Integer>> PRESSURE = new HashMap<>();
    /** Ligado enquanto o proprio mod teleporta alguem para dentro ou para fora: o bloqueio de dimensao deixa passar. */
    private static boolean travelling;

    private VoidPocket() {
    }

    public static boolean isInside(Entity entity) {
        return entity.level().dimension().equals(DIMENSION);
    }

    /** Este teleporte entre dimensoes e nosso? O {@code EntityTravelToDimensionEvent} pergunta. */
    public static boolean travelling() {
        return travelling;
    }

    // --- Tecla -------------------------------------------------------------------------------------

    /**
     * Entrar e sair: 3 s entre um e outro. Cada um troca de dimensao Iris e ate 8 convidados, com chunk
     * carregado dos dois lados; sem limite, um cliente modificado fazia isso varias vezes por tick.
     * Marcar e mandar de volta (Shift) nao trocam a dimensao de quem usa e ficam livres.
     */
    private static final com.aurorion.core.rate.ActionCooldown CROSSING = new com.aurorion.core.rate.ActionCooldown(3_000L);

    public static void use(ServerPlayer iris, boolean alternate) {
        if (isInside(iris) && isOwnerInside(iris)) {
            if (alternate) sendBackLooked(iris);
            else if (CROSSING.acquire(iris.getUUID(), net.minecraft.Util.getMillis())) collapse(iris, true);
            return;
        }
        if (alternate) toggleMark(iris);
        else if (CROSSING.acquire(iris.getUUID(), net.minecraft.Util.getMillis())) enter(iris);
    }

    private static void toggleMark(ServerPlayer iris) {
        ServerPlayer target = PassiveTargets.lookedAt(iris, MARK_RANGE, other -> true);
        if (target == null) {
            iris.displayClientMessage(Component.translatable("aurorion_magia.vazio.sem_alvo")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        VoidPocketData data = VoidPocketData.get(iris.server);
        Set<UUID> marked = data.marked(iris.getUUID());
        if (marked.remove(target.getUUID())) {
            data.setMarked(iris.getUUID(), marked);
            iris.displayClientMessage(Component.translatable("aurorion_magia.vazio.desmarcado", target.getDisplayName())
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int max = MagiaConfig.POCKET_MAX_GUESTS.get();
        if (marked.size() >= max) {
            iris.displayClientMessage(Component.translatable("aurorion_magia.vazio.cheio", max)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        marked.add(target.getUUID());
        data.setMarked(iris.getUUID(), marked);
        MagiaNetwork.sendVisual(iris, target, SpellVisualPayload.Kind.VOID_MARK, 40);
        iris.displayClientMessage(Component.translatable("aurorion_magia.vazio.marcado", target.getDisplayName(),
                marked.size(), max).withStyle(ChatFormatting.DARK_PURPLE), true);
        target.displayClientMessage(Component.translatable("aurorion_magia.vazio.voce_marcado")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    private static void enter(ServerPlayer iris) {
        MinecraftServer server = iris.server;
        ServerLevel pocket = server.getLevel(DIMENSION);
        if (pocket == null) {
            iris.sendSystemMessage(Component.translatable("aurorion_magia.vazio.indisponivel"));
            return;
        }
        if (isInside(iris) || AurorionSpell.castBlocked(iris) || Binding.isBound(iris) || SoulRealm.active(iris)
                || AnachronicSight.isActive(iris)) {
            iris.displayClientMessage(Component.translatable("aurorion_magia.vazio.preso")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        VoidPocketData data = VoidPocketData.get(server);
        CompoundTag session = data.session(iris.getUUID());
        // A celula dela: sempre o mesmo lugar, para o que ela construiu estar la na proxima vez.
        int[] cell = data.cell(iris.getUUID());
        BlockPos floor = new BlockPos(cell[0], FLOOR_Y - 1, cell[1]);
        prepareCell(pocket, floor, session);
        Vec3 center = Vec3.atBottomCenterOf(floor.above());

        double bringSqr = MagiaConfig.POCKET_BRING_RADIUS.get() * (double) MagiaConfig.POCKET_BRING_RADIUS.get();
        List<ServerPlayer> candidates = new java.util.ArrayList<>();
        for (UUID id : data.marked(iris.getUUID())) {
            ServerPlayer other = server.getPlayerList().getPlayer(id);
            if (other == null || other.level() != iris.level() || !other.isAlive() || other.isSpectator()
                    || other.distanceToSqr(iris) > bringSqr || Binding.isBound(other) || SoulRealm.active(other)
                    || AnachronicSight.isActive(other) || data.anchor(id) != null) continue;
            candidates.add(other);
        }

        // Os pertences de cada um ficam na porta antes de qualquer teleporte (ver PocketBelongings).
        CompoundTag own = PocketBelongings.stash(iris);
        if (own == null) {
            iris.displayClientMessage(Component.translatable("aurorion_magia.vazio.preso")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        data.remember(iris, iris.getUUID(), VoidPocketData.ROLE_OWNER);
        CompoundTag ownAnchor = data.anchor(iris.getUUID());
        ownAnchor.put(BELONGINGS, own);
        ownAnchor.putString(GAME_MODE, iris.gameMode.getGameModeForPlayer().getName());
        List<ServerPlayer> companions = new java.util.ArrayList<>();
        for (ServerPlayer candidate : candidates) {
            CompoundTag theirs = PocketBelongings.stash(candidate);
            if (theirs == null) continue;
            data.remember(candidate, iris.getUUID(), VoidPocketData.ROLE_GUEST);
            data.anchor(candidate.getUUID()).put(BELONGINGS, theirs);
            companions.add(candidate);
        }
        session.putBoolean("Inside", true);
        session.putBoolean("Broken", false);
        session.putString("RiftDim", iris.level().dimension().location().toString());
        session.putDouble("RiftX", iris.getX());
        session.putDouble("RiftY", iris.getY());
        session.putDouble("RiftZ", iris.getZ());
        session.putDouble("CenterX", center.x);
        session.putDouble("CenterY", center.y);
        session.putDouble("CenterZ", center.z);
        data.setDirty();
        // Os pertences guardados nao esperam o autosave: um crash agora nao pode apaga-los.
        VoidPocketData.flush(server);
        PRESSURE.remove(iris.getUUID());
        ServerLevel origin = iris.serverLevel();
        Vec3 rift = iris.position();

        moveInto(iris, pocket, center);
        if (iris.level() != pocket) {
            // Outro mod recusou a travessia: nada de metade do grupo do outro lado, e tudo devolvido.
            giveBack(iris, ownAnchor);
            for (ServerPlayer companion : companions) giveBack(companion, data.anchor(companion.getUUID()));
            session.putBoolean("Inside", false);
            iris.sendSystemMessage(Component.translatable("aurorion_magia.vazio.indisponivel"));
            return;
        }
        // O vazio e dela: la dentro, criativo. Na volta, o modo de antes (ver restore).
        iris.setGameMode(GameType.CREATIVE);
        iris.addEffect(new MobEffectInstance(MagiaEffects.VOID_POCKET, MobEffectInstance.INFINITE_DURATION, 0,
                false, false, true));
        int i = 0;
        for (ServerPlayer companion : companions) {
            double angle = (i++) * Math.PI * 2 / Math.max(1, companions.size());
            // No ar em volta do bloco inicial, nao no vazio abaixo: quem nao voa cai no bloco ou e trazido de volta.
            moveInto(companion, pocket, center.add(Math.cos(angle) * 0.6, 0.2, Math.sin(angle) * 0.6));
            if (companion.level() != pocket) {
                giveBack(companion, data.anchor(companion.getUUID()));
                continue;
            }
            companion.sendSystemMessage(Component.translatable("aurorion_magia.vazio.levado", iris.getDisplayName())
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        MagiaNetwork.sendVisualAt(origin, iris, SpellVisualPayload.Kind.VOID_RIFT, 40, rift, 0);
        origin.playSound(null, rift.x, rift.y, rift.z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4F, 0.4F);
        iris.sendSystemMessage(Component.translatable("aurorion_magia.vazio.entrou", stabilityName(guestsInside(iris)))
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static void sendBackLooked(ServerPlayer iris) {
        VoidPocketData data = VoidPocketData.get(iris.server);
        ServerPlayer target = PassiveTargets.lookedAt(iris, 8, other -> {
            CompoundTag anchor = data.anchor(other.getUUID());
            return anchor != null && anchor.getUUID("Owner").equals(iris.getUUID());
        });
        if (target == null) {
            iris.displayClientMessage(Component.translatable("aurorion_magia.vazio.sem_alvo")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        restore(target, false);
        iris.displayClientMessage(Component.translatable("aurorion_magia.vazio.devolvido", target.getDisplayName(),
                stabilityName(guestsInside(iris))).withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    // --- Relogio -----------------------------------------------------------------------------------

    /** Uma vez por segundo, pelo efeito {@code bolso_do_vazio} de Iris. {@code false} fecha o bolso. */
    public static boolean tick(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer iris) || !iris.isAlive()) return false;
        if (!isInside(iris) || !isOwnerInside(iris)) return false;
        VoidPocketData data = VoidPocketData.get(iris.server);
        CompoundTag session = data.session(iris.getUUID());
        Vec3 center = new Vec3(session.getDouble("CenterX"), session.getDouble("CenterY"), session.getDouble("CenterZ"));
        double radius = MagiaConfig.POCKET_RADIUS.get();

        int guests = 0;
        for (UUID id : data.members(iris.getUUID())) {
            ServerPlayer member = iris.server.getPlayerList().getPlayer(id);
            if (member == null || !isInside(member)) continue;
            CompoundTag anchor = data.anchor(id);
            if (anchor != null && VoidPocketData.ROLE_GUEST.equals(anchor.getString("Role"))) guests++;
            // Embaixo nao ha nada: quem cai volta para o bloco inicial antes de chegar ao fim do mundo.
            if (member.getY() < member.level().getMinBuildHeight() + 8) {
                TwilightTeleportCompat.immediateRun(() -> member.connection.teleport(center.x, center.y + 0.2, center.z,
                        member.getYRot(), member.getXRot()));
                member.resetFallDistance();
                continue;
            }
            double dx = member.getX() - center.x, dz = member.getZ() - center.z;
            if (dx * dx + dz * dz > radius * radius) {
                double scale = (radius - 2) / Math.sqrt(dx * dx + dz * dz);
                double toX = center.x + dx * scale, toZ = center.z + dz * scale;
                TwilightTeleportCompat.immediateRun(() -> member.connection.teleport(toX, member.getY(), toZ,
                        member.getYRot(), member.getXRot()));
                member.displayClientMessage(Component.translatable("aurorion_magia.vazio.borda")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
            }
        }
        pressRift(iris, session, guests);
        return true;
    }

    /** Quem esta agachado na fenda forca a barreira; quanto mais marcados la dentro, mais rapido ela cede. */
    private static void pressRift(ServerPlayer iris, CompoundTag session, int guests) {
        ResourceLocation dim = ResourceLocation.tryParse(session.getString("RiftDim"));
        ServerLevel level = dim == null ? null : iris.server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        Vec3 rift = new Vec3(session.getDouble("RiftX"), session.getDouble("RiftY"), session.getDouble("RiftZ"));
        if (level == null || !level.isLoaded(BlockPos.containing(rift))) return;

        int stability = stability(guests);
        int need = stability == 1 ? MagiaConfig.POCKET_MEDIUM_SECONDS.get() : MagiaConfig.POCKET_FRAGILE_SECONDS.get();
        Map<UUID, Integer> pressure = PRESSURE.computeIfAbsent(iris.getUUID(), ignored -> new HashMap<>());
        Set<UUID> pressing = new HashSet<>();
        float strongest = 0;
        VoidPocketData data = VoidPocketData.get(iris.server);
        boolean broken = session.getBoolean("Broken");
        for (ServerPlayer other : level.players()) {
            if (!other.isAlive() || other.isSpectator() || !other.isShiftKeyDown()
                    || other.position().distanceToSqr(rift) > RIFT_RADIUS_SQR || data.anchor(other.getUUID()) != null) continue;
            if (stability == 0 && !broken) {
                other.displayClientMessage(Component.translatable("aurorion_magia.vazio.nao_cede")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
                continue;
            }
            pressing.add(other.getUUID());
            int seconds = broken ? need : pressure.merge(other.getUUID(), 1, Integer::sum);
            strongest = Math.max(strongest, Math.min(1, seconds / (float) need));
            if (seconds >= need) {
                invade(iris, other, session);
                pressing.remove(other.getUUID());
            } else {
                other.displayClientMessage(Component.translatable("aurorion_magia.vazio.forcando", seconds, need)
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
            }
        }
        pressure.keySet().retainAll(pressing);
        MagiaNetwork.sendVisualAt(level, iris, SpellVisualPayload.Kind.VOID_RIFT, 30, rift, strongest);
    }

    private static void invade(ServerPlayer iris, ServerPlayer invader, CompoundTag session) {
        VoidPocketData data = VoidPocketData.get(iris.server);
        ServerLevel pocket = iris.server.getLevel(DIMENSION);
        if (pocket == null) return;
        data.remember(invader, iris.getUUID(), VoidPocketData.ROLE_INVADER);
        boolean first = !session.getBoolean("Broken");
        session.putBoolean("Broken", true);
        data.setDirty();
        Vec3 center = new Vec3(session.getDouble("CenterX"), session.getDouble("CenterY"), session.getDouble("CenterZ"));
        // O relogio roda dentro do tick de entidades do bolso: trocar alguem de dimensao fica para o fim do tick.
        iris.server.tell(new TickTask(iris.server.getTickCount(), () -> {
            CompoundTag anchor = data.anchor(invader.getUUID());
            if (!invader.isAlive() || anchor == null) return;
            // Invasor tambem entra sem nada: a regra da porta vale para todo mundo.
            CompoundTag theirs = PocketBelongings.stash(invader);
            if (theirs == null) {
                data.forget(invader.getUUID());
                return;
            }
            anchor.put(BELONGINGS, theirs);
            data.setDirty();
            VoidPocketData.flush(iris.server);
            moveInto(invader, pocket, center.add(0, 0.2, 0));
            if (invader.level() != pocket) giveBack(invader, anchor);
        }));
        if (first) {
            for (UUID id : data.members(iris.getUUID())) {
                ServerPlayer member = iris.server.getPlayerList().getPlayer(id);
                if (member != null) member.sendSystemMessage(Component.translatable("aurorion_magia.vazio.rompida",
                        invader.getDisplayName()).withStyle(ChatFormatting.RED));
            }
        }
    }

    // --- Seguranca ---------------------------------------------------------------------------------

    /**
     * O bolso protege quem Iris levou (e ela mesma) enquanto ela estiver viva la dentro e a barreira de
     * pe. {@code /kill} e o vazio do mundo passam, como na estase.
     */
    public static boolean protects(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof ServerPlayer player) || !isInside(player)) return false;
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) return false;
        VoidPocketData data = VoidPocketData.get(player.server);
        CompoundTag anchor = data.anchor(player.getUUID());
        if (anchor == null || VoidPocketData.ROLE_INVADER.equals(anchor.getString("Role"))) return false;
        UUID owner = anchor.getUUID("Owner");
        CompoundTag session = data.session(owner);
        if (!session.getBoolean("Inside") || session.getBoolean("Broken")) return false;
        ServerPlayer iris = player.server.getPlayerList().getPlayer(owner);
        return iris != null && iris.isAlive() && isInside(iris);
    }

    // --- Saida ------------------------------------------------------------------------------------

    /**
     * Fecha o bolso: todo mundo de volta, Iris por ultimo.
     *
     * @param immediate {@code false} quando chamado de dentro do tick de entidades (fim do efeito): as
     *                  trocas de dimensao vao para o fim do tick, como no Reino das Almas
     */
    public static void collapse(ServerPlayer iris, boolean immediate) {
        MinecraftServer server = iris.server;
        VoidPocketData data = VoidPocketData.get(server);
        CompoundTag session = data.session(iris.getUUID());
        session.putBoolean("Inside", false);
        session.putBoolean("Broken", false);
        data.setDirty();
        PRESSURE.remove(iris.getUUID());
        Runnable work = () -> {
            for (UUID id : data.members(iris.getUUID())) {
                ServerPlayer member = server.getPlayerList().getPlayer(id);
                if (member != null && member != iris) {
                    restore(member, false);
                    member.sendSystemMessage(Component.translatable("aurorion_magia.vazio.fechou")
                            .withStyle(ChatFormatting.DARK_PURPLE));
                }
            }
            restore(iris, false);
            if (iris.hasEffect(MagiaEffects.VOID_POCKET)) iris.removeEffect(MagiaEffects.VOID_POCKET);
        };
        if (immediate) work.run();
        else server.tell(new TickTask(server.getTickCount(), work));
    }

    /** Morte la dentro: o corpo volta antes de Vidas/Limbo registrarem a morte. Morte de Iris fecha tudo. */
    public static void onDeath(ServerPlayer player) {
        VoidPocketData data = VoidPocketData.get(player.server);
        CompoundTag anchor = data.anchor(player.getUUID());
        if (anchor == null) return;
        if (VoidPocketData.ROLE_OWNER.equals(anchor.getString("Role"))) {
            data.session(player.getUUID()).putBoolean("Inside", false);
            data.setDirty();
            PRESSURE.remove(player.getUUID());
            for (UUID id : data.members(player.getUUID())) {
                ServerPlayer member = player.server.getPlayerList().getPlayer(id);
                if (member != null && member != player) restore(member, false);
            }
        }
        restore(player, true);
    }

    /** Saiu do jogo la dentro: volta antes de o save gravar a posicao. */
    public static void onLogout(ServerPlayer player) {
        CompoundTag anchor = VoidPocketData.get(player.server).anchor(player.getUUID());
        if (anchor == null) return;
        if (VoidPocketData.ROLE_OWNER.equals(anchor.getString("Role"))) collapse(player, true);
        else restore(player, false);
    }

    /**
     * Esta no bolso sem motivo (crash, relog, /tp): volta. Login, renascer e a checagem de um em um
     * segundo que o mod ja faz por jogador.
     */
    public static void recover(ServerPlayer player) {
        if (!player.isAlive()) return;
        VoidPocketData data = VoidPocketData.get(player.server);
        CompoundTag anchor = data.anchor(player.getUUID());
        if (!isInside(player)) {
            // Fora do vazio com ancora: crash entre a saida e o save, ou alguem que estava no antigo
            // bolso_do_vazio (que deixou de existir). Os pertences guardados voltam, e a ancora some.
            if (anchor != null) giveBack(player, anchor);
            return;
        }
        if (anchor == null) {
            if (player.hasPermissions(2) && player.isCreative()) return;
            ServerLevel overworld = player.server.overworld();
            Vec3 spawn = Vec3.atBottomCenterOf(overworld.getSharedSpawnPos());
            teleport(player, overworld, spawn, player.getYRot(), player.getXRot());
            return;
        }
        UUID owner = anchor.getUUID("Owner");
        ServerPlayer iris = player.server.getPlayerList().getPlayer(owner);
        boolean open = data.session(owner).getBoolean("Inside") && iris != null && isInside(iris);
        if (owner.equals(player.getUUID())) {
            if (!open || !player.hasEffect(MagiaEffects.VOID_POCKET)) collapse(player, true);
        } else if (!open) {
            restore(player, false);
        }
    }

    private static void restore(ServerPlayer player, boolean dying) {
        VoidPocketData data = VoidPocketData.get(player.server);
        CompoundTag anchor = data.anchor(player.getUUID());
        if (anchor == null) return;
        // Morto ainda sera clonado pelo vanilla; guardar a ancora deixa o retorno para o renascer.
        if (!player.isAlive() && !dying) return;
        data.forget(player.getUUID());
        if (isInside(player)) {
            ResourceLocation id = ResourceLocation.tryParse(anchor.getString("Dim"));
            ServerLevel home = id == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            Vec3 to = new Vec3(anchor.getDouble("X"), anchor.getDouble("Y"), anchor.getDouble("Z"));
            if (home == null || home.dimension().equals(DIMENSION) || !Double.isFinite(to.lengthSqr())) {
                home = player.server.overworld();
                to = Vec3.atBottomCenterOf(home.getSharedSpawnPos());
            }
            teleport(player, home, to, anchor.getFloat("Yaw"), anchor.getFloat("Pitch"));
            // Um veto de outro mod nao pode apagar o unico registro de retorno — nem os pertences: eles so
            // voltam quando a pessoa sai de verdade.
            if (isInside(player)) {
                data.anchorAgain(player.getUUID(), anchor);
                return;
            }
            player.resetFallDistance();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRAVEL,
                    SoundSource.PLAYERS, 0.25F, 1.6F);
        }
        // Fora do vazio (acabou de sair, ou o crash a deixou fora com a ancora): o de dentro fica la, o seu volta.
        // Morrendo, isto acontece antes da queda dos itens: cai o que era dela, onde o corpo voltou.
        giveBack(player, anchor);
    }

    /** Devolve pertences e modo de jogo, e apaga a ancora. Seguro chamar mais de uma vez. */
    private static void giveBack(ServerPlayer player, @Nullable CompoundTag anchor) {
        VoidPocketData data = VoidPocketData.get(player.server);
        data.forget(player.getUUID());
        if (anchor == null) return;
        if (anchor.contains(BELONGINGS, Tag.TAG_COMPOUND)) {
            PocketBelongings.unstash(player, anchor.getCompound(BELONGINGS));
            anchor.remove(BELONGINGS);
        }
        if (anchor.contains(GAME_MODE, Tag.TAG_STRING)) {
            player.setGameMode(GameType.byName(anchor.getString(GAME_MODE), GameType.SURVIVAL));
            anchor.remove(GAME_MODE);
        }
    }

    /** Na primeira entrada da dona, o unico bloco do vazio dela. Depois disso, o lugar e dela. */
    private static void prepareCell(ServerLevel pocket, BlockPos floor, CompoundTag session) {
        if (session.getBoolean("CellBuilt")) return;
        ResourceLocation id = ResourceLocation.tryParse(MagiaConfig.POCKET_START_BLOCK.get());
        Block block = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR) block = Blocks.STONE;
        pocket.getChunk(floor.getX() >> 4, floor.getZ() >> 4);
        pocket.setBlock(floor, block.defaultBlockState(), Block.UPDATE_ALL);
        session.putBoolean("CellBuilt", true);
    }

    // --- Apoio ------------------------------------------------------------------------------------

    private static void moveInto(ServerPlayer player, ServerLevel pocket, Vec3 at) {
        player.stopRiding();
        teleport(player, pocket, at, player.getYRot(), player.getXRot());
        player.resetFallDistance();
    }

    private static void teleport(ServerPlayer player, ServerLevel level, Vec3 at, float yaw, float pitch) {
        travelling = true;
        try {
            TwilightTeleportCompat.immediateRun(() -> player.teleportTo(level, at.x, at.y, at.z, Set.of(), yaw, pitch));
        } finally {
            travelling = false;
        }
    }

    private static boolean isOwnerInside(ServerPlayer iris) {
        CompoundTag anchor = VoidPocketData.get(iris.server).anchor(iris.getUUID());
        return anchor != null && VoidPocketData.ROLE_OWNER.equals(anchor.getString("Role"))
                && VoidPocketData.get(iris.server).session(iris.getUUID()).getBoolean("Inside");
    }

    private static int guestsInside(ServerPlayer iris) {
        VoidPocketData data = VoidPocketData.get(iris.server);
        int guests = 0;
        for (UUID id : data.members(iris.getUUID())) {
            CompoundTag anchor = data.anchor(id);
            ServerPlayer member = iris.server.getPlayerList().getPlayer(id);
            if (anchor != null && member != null && isInside(member)
                    && VoidPocketData.ROLE_GUEST.equals(anchor.getString("Role"))) guests++;
        }
        return guests;
    }

    /** 0 estavel (0 ou 1 marcado), 1 mediana (2), 2 fragil (3+). */
    private static int stability(int guests) {
        return guests <= 1 ? 0 : guests == 2 ? 1 : 2;
    }

    private static Component stabilityName(int guests) {
        return Component.translatable("aurorion_magia.vazio.barreira." + stability(guests));
    }

    /** Personagem novo: o bolso da antiga Iris se fecha e as marcas somem. */
    public static void reset(MinecraftServer server, UUID owner, @Nullable ServerPlayer online) {
        if (online != null && isOwnerInside(online)) collapse(online, true);
        VoidPocketData.get(server).clearSession(owner);
    }
}
