package com.aurorion.essentials.client;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tira da agenda do telefone (e dos favoritos de PIX) os personagens que morreram de vez. O porque
 * esta em {@code PhoneRetirements}; a regra do que sai, em {@link RetiredContacts}.
 *
 * <p>A agenda e do telefone, nao nossa: {@code PhoneMessagesStore} guarda os contatos em campos
 * privados e grava em {@code mattupolis_phone/contacts.properties}. Por isso a limpeza usa reflexao,
 * como o {@code MattupolisPhoneCompat} ja faz no servidor, e roda os proprios metodos do telefone para
 * carregar e gravar — o arquivo continua no formato dele. Os favoritos de PIX tem API publica
 * ({@code PhoneBankStore}).
 *
 * <p>Se o telefone mudar e a reflexao falhar, nada e marcado como feito: a proxima entrada tenta de
 * novo, e o aviso vai para o log uma vez.
 *
 * <p>Os ids de reset ja tratados ficam em {@code aurorion_retired_contacts.txt}, ao lado da agenda,
 * na pasta do personagem ({@link PhoneCharacterStorage}): processar o mesmo reset duas vezes apagaria
 * um contato do personagem novo, e cada personagem tem a propria agenda para limpar.
 *
 * <p>No reset da propria conta o celular inteiro e do morto: alem da agenda, a pasta do personagem
 * (notas, calendario, capa, fotos, PIN) e esvaziada e a memoria do telefone volta a de recem-ligado.
 *
 * <p><b>A marca vem antes da limpeza.</b> O servidor continua mandando o mesmo reset em todo login
 * durante dias; se a marca se perdesse (falha ao gravar, arquivo apagado), o login seguinte esvaziava
 * de novo o celular — agora o do personagem <b>novo</b>. Por isso: a marca e gravada atomica, em dois
 * lugares (dentro da pasta do personagem e ao lado dela, onde a limpeza nunca chega), e <b>antes</b> de
 * apagar qualquer coisa. Sem conseguir grava-la, nada e apagado e o login seguinte tenta de novo. Um
 * crash entre gravar a marca e limpar deixa o celular antigo como estava, que e o erro barato; apagar
 * o novo nao tem volta.
 */
public final class PhoneContactCleanup {
    private static final String MESSAGES = "com.mattupolis.phone.client.gui.PhoneMessagesStore";
    private static final String BANK = "com.mattupolis.phone.client.gui.PhoneBankStore";
    private static final String PROCESSED_FILE = "aurorion_retired_contacts.txt";

    private static boolean reportedFailure;

    private PhoneContactCleanup() {
    }

    /** Na thread do cliente. */
    public static void apply(Map<UUID, String> retired) {
        Minecraft minecraft = Minecraft.getInstance();
        Path characterDir = PhoneCharacterStorage.activeDir();
        Path processedFile = characterDir != null ? characterDir.resolve(PROCESSED_FILE)
                : minecraft.gameDirectory.toPath().resolve(PhoneCharacterStorage.ROOT).resolve(PROCESSED_FILE);
        // Copia ao lado da pasta (personagens/<uuid>.aurorion_retired_contacts.txt): a limpeza esvazia a
        // pasta, nunca o vizinho, e a copia continua sendo so deste perfil — o alt tem a propria agenda.
        Path backupFile = characterDir != null
                ? characterDir.resolveSibling(characterDir.getFileName() + "." + PROCESSED_FILE) : null;
        List<Path> markers = backupFile == null ? List.of(processedFile) : List.of(processedFile, backupFile);
        Set<UUID> processed = new LinkedHashSet<>();
        for (Path marker : markers) processed.addAll(readProcessed(marker));
        String self = minecraft.player != null ? minecraft.player.getGameProfile().getName() : minecraft.getUser().getName();

        RetiredContacts plan = RetiredContacts.plan(retired, processed, self);
        if (plan.isEmpty()) return;

        Set<UUID> before = new LinkedHashSet<>(processed);
        processed.addAll(plan.newlyProcessed());
        // Marca primeiro. A da pasta do personagem e obrigatoria: e ela que acompanha a pasta limpa.
        if (!writeProcessed(processedFile, processed)) return;
        if (backupFile != null) writeProcessed(backupFile, processed);

        try {
            forgetContacts(plan);
            forgetBankFavorites(plan);
            PhoneConversations.forgetGramContacts(plan);
        } catch (ClassNotFoundException phoneNotInstalled) {
            undoMarks(markers, before);
            return;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!reportedFailure) {
                reportedFailure = true;
                AurorionEssentials.LOGGER.warn("Nao consegui limpar da agenda do telefone os personagens aposentados; "
                        + "tento de novo na proxima entrada.", e);
            }
            // Nada foi apagado ainda da pasta: a proxima entrada pode tentar de novo.
            undoMarks(markers, before);
            return;
        }

        if (plan.wipeAll() && characterDir != null) {
            // A lista de resets tratados fica: e ela que impede este mesmo reset de apagar, na proxima
            // entrada, o que o personagem novo ja tiver gravado.
            PhoneConversations.resetCharacter(characterDir, Set.of(PROCESSED_FILE, PhoneCharacterStorage.MIGRATED_MARKER));
            PhoneSession.resetMemory();
            PhoneConversations.beginSession(characterDir);
        } else {
            PhoneConversations.messagesChanged();
            PhoneConversations.flush();
        }
    }

    /** A limpeza nao aconteceu: a marca volta ao que era, para a proxima entrada tentar de novo. */
    private static void undoMarks(List<Path> markers, Set<UUID> before) {
        for (Path marker : markers) writeProcessed(marker, before);
    }

    @SuppressWarnings("unchecked")
    private static void forgetContacts(RetiredContacts plan) throws ReflectiveOperationException {
        Class<?> store = Class.forName(MESSAGES);
        // Carrega a agenda do disco antes de mexer: ela e lida sob demanda, e gravar sem carregar
        // apagaria tudo.
        invoke(store, "loadPlayerContactsIfNeeded");

        List<Object> threads = (List<Object>) field(store, "THREADS");
        Map<String, ?> messages = (Map<String, ?>) field(store, "MESSAGES");
        Map<String, ?> unread = (Map<String, ?>) field(store, "UNREAD_COUNTS");
        Map<String, String> numberToPlayer = (Map<String, String>) field(store, "PHONE_ID_TO_PLAYER");
        Map<String, String> playerToNumber = (Map<String, String>) field(store, "PLAYER_TO_PHONE_ID");

        boolean changed = false;
        for (Iterator<Object> it = threads.iterator(); it.hasNext(); ) {
            Object thread = it.next();
            if (!(boolean) call(thread, "playerThread")) continue;
            if (!plan.forgets((String) call(thread, "title"))) continue;
            String id = (String) call(thread, "id");
            it.remove();
            messages.remove(id);
            unread.remove(id);
            changed = true;
        }

        // O numero antigo de quem morreu, ainda em memoria desta sessao: sem apagar, "adicionar por
        // numero" acharia o nick sem perguntar ao servidor. O proximo diretorio traz o numero novo.
        // No reset da propria conta nao mexe: o diretorio do login ja traz o numero certo dela.
        if (!plan.nicks().isEmpty()) {
            numberToPlayer.values().removeIf(plan.nicks()::contains);
            playerToNumber.keySet().removeIf(plan.nicks()::contains);
        }

        if (changed) invoke(store, "savePlayerContacts");
    }

    @SuppressWarnings("unchecked")
    private static void forgetBankFavorites(RetiredContacts plan) throws ReflectiveOperationException {
        Class<?> bank = Class.forName(BANK);
        List<String> favorites = (List<String>) bank.getMethod("getFavoriteAccounts").invoke(null);
        Method toggle = bank.getMethod("toggleFavoriteAccount", String.class);
        for (String favorite : new ArrayList<>(favorites)) {
            // Favorito existente: alternar remove.
            if (plan.forgets(favorite)) toggle.invoke(null, favorite);
        }
    }

    private static Object field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void invoke(Class<?> owner, String name) throws ReflectiveOperationException {
        Method method = owner.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(null);
    }

    private static Object call(Object target, String accessor) throws ReflectiveOperationException {
        return target.getClass().getMethod(accessor).invoke(target);
    }

    private static Set<UUID> readProcessed(Path file) {
        Set<UUID> processed = new LinkedHashSet<>();
        if (!Files.exists(file)) return processed;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                try {
                    if (!line.isBlank()) processed.add(UUID.fromString(line.trim()));
                } catch (IllegalArgumentException ignored) {
                    // Linha estragada: so ela se perde.
                }
            }
        } catch (IOException e) {
            AurorionEssentials.LOGGER.warn("Nao consegui ler {}", file, e);
        }
        return processed;
    }

    /** Atomico: um crash no meio deixa a lista anterior inteira, nunca uma lista vazia. */
    private static boolean writeProcessed(Path file, Set<UUID> processed) {
        List<String> lines = new ArrayList<>(processed.size());
        for (UUID id : processed) lines.add(id.toString());
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), PROCESSED_FILE, ".tmp");
            try {
                Files.write(temporary, lines, StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return true;
        } catch (IOException e) {
            AurorionEssentials.LOGGER.warn("Nao consegui gravar {}", file, e);
            return false;
        }
    }
}
