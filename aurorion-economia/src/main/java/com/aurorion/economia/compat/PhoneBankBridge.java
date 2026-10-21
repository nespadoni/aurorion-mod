package com.aurorion.economia.compat;

import com.aurorion.core.rate.ActionCooldown;
import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.money.Transfer;
import com.aurorion.economia.server.Wallet;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.Util;

/**
 * Liga o app de banco do telefone MikasRevs/Mattupolis ao saldo do Aurorion.
 *
 * <h2>Por que precisa de mixin</h2>
 *
 * <p>O telefone tem a tela, os pacotes, o extrato, o log de auditoria e o teto diario prontos, mas o
 * saldo em si nunca existiu: no bytecode da versao instalada, {@code getSnapshot} devolve
 * <i>sempre</i> "Phone Bank is running without an economy integration" e {@code transfer} devolve
 * <i>sempre</i> "Bank transfers are disabled because no economy integration is installed" — sem
 * desvio nenhum, sem interface de servico, sem ponto de extensao. Nenhum mod de economia instalado
 * ao lado faz o app funcionar. Interceptar os dois metodos e o unico caminho.</p>
 *
 * <h2>Por que reflexao</h2>
 *
 * <p>O telefone e opcional e nao entra no classpath de compilacao, entao {@code BankSnapshot} e
 * {@code ActionResult} nao existem como tipo aqui. Os construtores sao resolvidos uma vez e
 * guardados; se o telefone nao estiver instalado, tudo isto fica inerte e o mod segue funcionando
 * pelos comandos.</p>
 *
 * <h2>Unidade</h2>
 *
 * <p>O campo de quantia do app e um inteiro, entao o numero que o jogador digita ali e lido em
 * <b>fragmentos</b> — a unidade indivisivel. Digitar 123 paga 12 óbolos e 3 fragmentos. Ler em
 * obolos impediria pagar qualquer troco pelo celular.</p>
 *
 * <h2>Limites que o HEAD do mixin pulava</h2>
 *
 * <p>Interceptar no HEAD substitui o metodo inteiro do telefone, inclusive o que ele teria de
 * protecao. Por isso aqui ha: um intervalo de {@value #TRANSFER_INTERVAL_MS} ms entre transferencias
 * da mesma conta (cada uma grava o extrato do telefone em disco, de forma sincrona, na thread do
 * servidor — sem intervalo, um cliente forjado transformava isso em I/O por pacote), e o teto
 * diario do proprio telefone ({@code DAILY_TRANSFER_LIMIT}, contado pelo extrato dele), lido por
 * reflexao. Se a versao do telefone nao tiver o teto, so o intervalo vale.</p>
 */
public final class PhoneBankBridge {
    private static final String STORE = "com.mattupolis.phone.server.bank.PhoneBankServerStore";

    private static boolean resolved;
    private static Constructor<?> snapshotConstructor;
    private static Constructor<?> actionConstructor;
    private static Method recordTransferDetailed;
    private static Method recordTransferWithNote;
    private static Method recordTransferLegacy;
    private static Method syncToPlayer;
    /** {@code getSpentToday(ServerPlayer)} privado do telefone; null se a versao nao tiver. */
    private static Method spentToday;
    private static long dailyLimit = -1L;

    static final long TRANSFER_INTERVAL_MS = 1_000L;
    private static final ActionCooldown TRANSFERS = new ActionCooldown(TRANSFER_INTERVAL_MS);

    private PhoneBankBridge() {
    }

    /** @return o {@code BankSnapshot} do telefone, ou null para deixar o comportamento original. */
    public static Object snapshot(ServerPlayer player) {
        if (!resolve()) return null;

        long balance = Wallet.balance(player.server, player.getUUID());
        return construct(snapshotConstructor, true, Money.format(balance), balance, "");
    }

    /** @return o {@code ActionResult} do telefone, ou null para deixar o comportamento original. */
    public static Object transfer(ServerPlayer payer, String targetName, long amount, String note) {
        if (!resolve()) return null;

        if (!TRANSFERS.acquire(payer.getUUID(), Util.getMillis()))
            return construct(actionConstructor, false, "Aguarde um instante entre uma transferencia e outra.");

        ServerPlayer target = payer.server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            // O telefone roteia por nick da conta, nao pelo nome do personagem que a tela mostra.
            return construct(actionConstructor, false, "Jogador offline ou inexistente.");
        }
        if (overDailyLimit(payer, amount))
            return construct(actionConstructor, false, "Limite diario de transferencias do celular atingido.");

        Transfer.Result result = Wallet.transfer(payer.server, payer.getUUID(), target.getUUID(), amount, "celular");
        if (!result.ok()) {
            return construct(actionConstructor, false, switch (result) {
                case INSUFFICIENT -> "Saldo insuficiente: voce tem " + Money.describe(
                        Wallet.balance(payer.server, payer.getUUID())) + ".";
                case SAME_ACCOUNT -> "Nao da para transferir para si mesmo.";
                case TARGET_FULL -> "A conta de destino nao comporta esse valor.";
                case SENDER_BLOCKED -> "Seu personagem nao pode enviar dinheiro agora (Limbo, morte ou sem nome).";
                case STARTER_LOCKED -> "A bolsa inicial ainda nao pode ser repassada; gaste-a ou jogue mais um pouco.";
                case INVALID_AMOUNT, OK -> "Quantia invalida.";
            });
        }

        record(payer, target, amount, note);
        return construct(actionConstructor, true, "Enviado: " + Money.describe(amount) + ".");
    }

    /** Empurra o saldo atual para o app do celular, quando o telefone estiver instalado. */
    public static void sync(ServerPlayer player) {
        if (!resolve()) return;
        try {
            syncToPlayer.invoke(null, player);
        } catch (ReflectiveOperationException e) {
            AurorionEconomia.LOGGER.warn("Nao foi possivel atualizar o saldo no celular.", e);
        }
    }

    /**
     * Devolve o pagamento ao extrato e a auditoria do proprio telefone, para que a aba de historico
     * e o painel de admin continuem contando a mesma historia que a carteira.
     *
     * <p>Falhar aqui nao desfaz o pagamento: o dinheiro ja mudou de dono na carteira, que e a fonte
     * de verdade. Some so a linha bonita no extrato do telefone, e isso vira aviso no log.</p>
     */
    private static void record(ServerPlayer payer, ServerPlayer target, long amount, String note) {
        try {
            String cleanNote = note == null ? "" : note;
            if (recordTransferDetailed != null) {
                recordTransferDetailed.invoke(null, payer, target.getUUID(), target.getGameProfile().getName(),
                        amount, "obolos", cleanNote, Money.describe(amount));
            } else if (recordTransferWithNote != null) {
                recordTransferWithNote.invoke(null, payer, target, amount, "obolos", cleanNote);
            } else {
                recordTransferLegacy.invoke(null, payer, target, amount, "obolos");
            }
            syncToPlayer.invoke(null, payer);
            syncToPlayer.invoke(null, target);
        } catch (ReflectiveOperationException e) {
            AurorionEconomia.LOGGER.warn("Pagamento feito, mas o extrato do telefone nao registrou.", e);
        }
    }

    /**
     * O teto diario do telefone, que ele conta no proprio extrato ({@code recordTransfer} soma ao
     * gasto do dia). Falha de reflexao nao bloqueia: o teto e uma protecao extra, nao a autoridade.
     */
    private static boolean overDailyLimit(ServerPlayer payer, long amount) {
        if (spentToday == null || dailyLimit <= 0 || amount <= 0) return false;
        try {
            long spent = (long) spentToday.invoke(null, payer);
            return spent > dailyLimit - amount;
        } catch (ReflectiveOperationException | ClassCastException error) {
            AurorionEconomia.LOGGER.warn("Nao consegui ler o teto diario do banco do telefone; seguindo sem ele.", error);
            spentToday = null;
            return false;
        }
    }

    private static Object construct(Constructor<?> constructor, Object... args) {
        try {
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            AurorionEconomia.LOGGER.error("Nao foi possivel montar a resposta do banco do telefone.", e);
            return null;
        }
    }

    private static synchronized boolean resolve() {
        if (resolved) return snapshotConstructor != null;
        resolved = true;

        try {
            Class<?> store = Class.forName(STORE);
            snapshotConstructor = store.getClassLoader()
                    .loadClass(STORE + "$BankSnapshot")
                    .getDeclaredConstructor(boolean.class, String.class, long.class, String.class);
            actionConstructor = store.getClassLoader()
                    .loadClass(STORE + "$ActionResult")
                    .getDeclaredConstructor(boolean.class, String.class);
            recordTransferDetailed = findMethod(store, "recordTransfer",
                    ServerPlayer.class, java.util.UUID.class, String.class, long.class, String.class, String.class, String.class);
            recordTransferWithNote = findMethod(store, "recordTransfer",
                    ServerPlayer.class, ServerPlayer.class, long.class, String.class, String.class);
            recordTransferLegacy = store.getMethod("recordTransfer",
                    ServerPlayer.class, ServerPlayer.class, long.class, String.class);
            syncToPlayer = store.getMethod("syncToPlayer", ServerPlayer.class);

            snapshotConstructor.setAccessible(true);
            actionConstructor.setAccessible(true);
            resolveDailyLimit(store);

            AurorionEconomia.LOGGER.info("Banco do telefone ligado a carteira do Aurorion.");
            return true;
        } catch (ClassNotFoundException e) {
            // O telefone e opcional: sem ele o mod vive dos comandos.
            snapshotConstructor = null;
            return false;
        } catch (ReflectiveOperationException e) {
            AurorionEconomia.LOGGER.error(
                    "O telefone esta instalado, mas o banco dele mudou de forma: o app vai continuar "
                            + "desligado. Conferir PhoneBankServerStore nesta versao do telefone.", e);
            snapshotConstructor = null;
            return false;
        }
    }

    /** Opcional: versoes sem o teto (ou com modulo fechado a reflexao) seguem so com o intervalo. */
    private static void resolveDailyLimit(Class<?> store) {
        try {
            Method method = store.getDeclaredMethod("getSpentToday", ServerPlayer.class);
            Field field = store.getDeclaredField("DAILY_TRANSFER_LIMIT");
            method.setAccessible(true);
            field.setAccessible(true);
            dailyLimit = field.getLong(null);
            spentToday = method;
        } catch (ReflectiveOperationException | RuntimeException error) {
            AurorionEconomia.LOGGER.info("Banco do telefone sem teto diario legivel; vale so o intervalo entre transferencias.");
            spentToday = null;
            dailyLimit = -1L;
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
