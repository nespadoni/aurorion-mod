package com.aurorion.limbo.exile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import org.jetbrains.annotations.Nullable;

/**
 * O estado de uma pessoa dentro do Limbo. Uma entrada por exilado, e nenhuma para quem nao esta la.
 *
 * <h2>Por que o prazo e "quanto falta" e nao "quando vence"</h2>
 *
 * <p>Guardar o instante do vencimento seria mais simples de ler, e estaria errado: um servidor que
 * fica tres dias fora do ar voltaria com todo mundo morto sem que ninguem tivesse tido a chance de
 * jogar. Guardando o <b>restante</b> e descontando so o tempo em que o servidor esteve de pe, uma
 * manutencao longa nao mata ninguem.
 *
 * <p>O custo assumido e nao dar para dizer "vence sabado as 21h" — so "faltam 6h". Para esta
 * mecanica isso e o que a pessoa precisa saber de qualquer jeito.
 */
public final class ExileRecord {
    private static final String KEY_STARTED = "Started";
    private static final String KEY_REMAINING = "Remaining";
    private static final String KEY_NAME = "Name";
    private static final String KEY_RESCUE_TRIED = "RescueTried";
    private static final String KEY_DOOR_TARGET = "DoorTarget";
    private static final String KEY_WALK_BASE = "WalkBase";
    private static final String KEY_DOOR_POS = "DoorPos";
    private static final String KEY_WARN_STAGE = "WarnStage";

    /** Quando a pessoa caiu, em epoch millis. Serve a auditoria; o prazo nao e calculado a partir daqui. */
    private final long startedAt;

    /** Quanto ainda falta, em millis de servidor de pe. */
    private long remainingMillis;

    /**
     * Ultimo nome conhecido. Guardado porque o relatorio precisa citar gente offline, e resolver
     * UUID para nome sem o jogador conectado depende do cache de perfis, que pode nao ter a entrada.
     */
    private String lastName;

    /**
     * Alguem ja tentou buscar esta pessoa.
     *
     * <p>E o que desliga a Porta do Esquecido: ela e a saida de quem <em>ninguem</em> foi buscar. Se
     * uma equipe abriu a passagem e falhou, a pessoa nao foi esquecida — ela foi procurada, e a
     * historia dela e outra.
     */
    private boolean rescueAttempted;

    /** Blocos a caminhar para a Porta aparecer. Zero enquanto a janela nao abriu. */
    private int doorTarget;

    /** Estatistica de caminhada no instante em que a janela abriu, em centimetros. */
    private long walkBaseline;

    @Nullable
    private BlockPos doorPos;

    /**
     * Qual faixa de aviso de prazo ja foi enviada. So anda para frente.
     *
     * <p>Mesmo motivo do cursor de avisos do {@code aurorion-portais} (SDD §8.5): um servidor que
     * travou um minuto volta <b>pulando</b> os avisos vencidos, em vez de despejar todos de uma vez.
     */
    private int warnStage;
    private boolean expirationReported;
    @Nullable private Direction doorFacing;
    @Nullable private String doorBlock;
    @Nullable private String doorDimension;
    // Cooldown de tentativas caras; pertence a esta sessao, nao ao save.
    private long nextDoorTry;

    public ExileRecord(long startedAt, long remainingMillis, String lastName) {
        this.startedAt = startedAt;
        this.remainingMillis = Math.max(0L, remainingMillis);
        this.lastName = lastName;
    }

    // --- Prazo ---------------------------------------------------------------------------------

    public long remainingMillis() {
        return remainingMillis;
    }

    /** @return true se o prazo chegou a zero agora. */
    public boolean drain(long millis) {
        if (remainingMillis <= 0L || millis <= 0L) return false;

        remainingMillis = Math.max(0L, remainingMillis - millis);
        return remainingMillis == 0L;
    }

    /**
     * Define o restante direto, para o ajuste manual da staff.
     *
     * <p>Separado do {@link #drain} de proposito: o drain nao mexe em quem ja venceu (ele so consome
     * tempo de quem ainda tem), e e exatamente o registro vencido que a staff mais precisa poder
     * reabrir — foi o caso em que ela vai querer dar mais tempo a alguem.
     */
    public void setRemaining(long millis) {
        remainingMillis = Math.max(0L, millis);
        if (remainingMillis > 0L) expirationReported = false;
        warnStage = 0;
        nextDoorTry = 0L;
    }

    /** Inclusive prazo zerado pela staff: uma transicao, um evento, mesmo depois de reiniciar. */
    public boolean reportExpiration() {
        if (remainingMillis > 0L || expirationReported) return false;
        expirationReported = true;
        return true;
    }

    public boolean advanceWarning(long[] bands) {
        int next = warnStage;
        while (next < bands.length && remainingMillis <= bands[next]) next++;
        if (next == warnStage) return false;
        warnStage = next;
        return true;
    }

    public long startedAt() {
        return startedAt;
    }

    public int warnStage() {
        return warnStage;
    }

    public void setWarnStage(int stage) {
        warnStage = stage;
    }

    // --- Identidade ----------------------------------------------------------------------------

    public String lastName() {
        return lastName;
    }

    public void setLastName(String name) {
        lastName = name;
    }

    // --- Resgate -------------------------------------------------------------------------------

    public boolean rescueAttempted() {
        return rescueAttempted;
    }

    public void markRescueAttempted() {
        rescueAttempted = true;
        disarm();
    }

    // --- Porta do Esquecido --------------------------------------------------------------------

    public boolean doorArmed() {
        return doorTarget > 0;
    }

    public int doorTarget() {
        return doorTarget;
    }

    public long walkBaseline() {
        return walkBaseline;
    }

    public void arm(int targetBlocks, long walkBaselineCm) {
        doorTarget = targetBlocks;
        walkBaseline = walkBaselineCm;
    }

    @Nullable
    public BlockPos doorPos() {
        return doorPos;
    }

    public void setDoorPos(BlockPos pos) {
        doorPos = pos;
    }

    public void setDoor(BlockPos pos, Direction facing, String block, String dimension) {
        doorPos = pos.immutable();
        doorFacing = facing;
        doorBlock = block;
        doorDimension = dimension;
    }

    @Nullable public Direction doorFacing() { return doorFacing; }
    @Nullable public String doorBlock() { return doorBlock; }
    @Nullable public String doorDimension() { return doorDimension; }

    public void clearDoor() {
        doorPos = null;
        doorFacing = null;
        doorBlock = null;
        doorDimension = null;
    }

    public void disarm() {
        doorTarget = 0;
        walkBaseline = 0L;
        nextDoorTry = 0L;
    }

    public boolean canTryDoor(long tick) { return tick >= nextDoorTry; }
    public void delayDoor(long tick) { nextDoorTry = tick + 200L; }

    // --- Sincronia com a tela do dono ----------------------------------------------------------
    //
    // O painel do exilado nao recebe um pacote por segundo: o cliente conta o relogio sozinho entre
    // um snapshot e o proximo. Entao o servidor so fala quando a ETAPA muda — caiu, armou, a Porta
    // apareceu, o prazo venceu — mais um reenvio ralo para corrigir a deriva das duas contagens.
    //
    // Rastrear a etapa ja enviada (em vez de chamar sync em cada transicao) e o que faz uma
    // transicao nova, inventada depois, nao nascer esquecida: qualquer mudanca de etapa vira pacote
    // sem ninguem precisar lembrar de avisar.
    //
    // Pertence a sessao, nao ao save: depois de um restart o cliente nao sabe de nada mesmo.

    /** Um reenvio por minuto. Com unidades de exilados, e trafego irrelevante. */
    private static final long SYNC_REFRESH_TICKS = 1200L;

    private int syncedStage = -1;
    private long nextSyncAt;

    public boolean syncDue(int stage, long tick) {
        return stage != syncedStage || tick >= nextSyncAt;
    }

    public void markSynced(int stage, long tick) {
        syncedStage = stage;
        nextSyncAt = tick + SYNC_REFRESH_TICKS;
    }

    /** Forca o proximo envio: login, respawn e troca de dimensao zeram o que a tela sabia. */
    public void invalidateSync() {
        syncedStage = -1;
        nextSyncAt = 0L;
    }

    /** Blocos ja caminhados dentro da janela, a partir da estatistica bruta em centimetros. */
    public int walkedSince(long walkNowCm) {
        return (int) Math.max(0L, (walkNowCm - walkBaseline) / 100L);
    }

    // --- NBT -----------------------------------------------------------------------------------

    public void write(CompoundTag tag) {
        tag.putLong(KEY_STARTED, startedAt);
        tag.putLong(KEY_REMAINING, remainingMillis);
        tag.putString(KEY_NAME, lastName);
        tag.putBoolean(KEY_RESCUE_TRIED, rescueAttempted);
        tag.putInt(KEY_DOOR_TARGET, doorTarget);
        tag.putLong(KEY_WALK_BASE, walkBaseline);
        tag.putInt(KEY_WARN_STAGE, warnStage);
        tag.putBoolean("ExpirationReported", expirationReported);

        if (doorPos != null) {
            tag.put(KEY_DOOR_POS, NbtUtils.writeBlockPos(doorPos));
            if (doorFacing != null) tag.putString("DoorFacing", doorFacing.getName());
            if (doorBlock != null) tag.putString("DoorBlock", doorBlock);
            if (doorDimension != null) tag.putString("DoorDimension", doorDimension);
        }
    }

    public static ExileRecord read(CompoundTag tag) {
        ExileRecord record = new ExileRecord(
                tag.getLong(KEY_STARTED),
                tag.getLong(KEY_REMAINING),
                tag.getString(KEY_NAME));

        record.rescueAttempted = tag.getBoolean(KEY_RESCUE_TRIED);
        record.doorTarget = tag.getInt(KEY_DOOR_TARGET);
        record.walkBaseline = tag.getLong(KEY_WALK_BASE);
        record.warnStage = Math.clamp(tag.getInt(KEY_WARN_STAGE), 0, 5);
        record.doorPos = tag.contains(KEY_DOOR_POS) ? NbtUtils.readBlockPos(tag, KEY_DOOR_POS).orElse(null) : null;
        record.doorFacing = Direction.byName(tag.getString("DoorFacing"));
        if (tag.contains("DoorBlock")) record.doorBlock = tag.getString("DoorBlock");
        if (tag.contains("DoorDimension")) record.doorDimension = tag.getString("DoorDimension");
        // Saves anteriores ja registravam o vencimento, mas nao tinham esta flag.
        record.expirationReported = tag.contains("ExpirationReported")
                ? tag.getBoolean("ExpirationReported") : record.remainingMillis == 0L;
        if (record.rescueAttempted) record.disarm();

        return record;
    }
}
