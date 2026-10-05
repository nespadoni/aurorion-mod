package com.aurorion.essentials.client;

import com.aurorion.essentials.AurorionEssentials;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Uma pasta do telefone por personagem, no PC de quem joga.
 *
 * <p>O telefone grava tudo do cliente em {@code .minecraft/mattupolis_phone/} — agenda, notas,
 * calendario, capa, papel de parede, PIN, favoritos de PIX, fotos recebidas. A pasta e da maquina,
 * nao do personagem: quem tem um alt via as notas e a agenda do principal no celular do alt, e o
 * personagem novo depois de uma morte herdava o celular do morto.
 *
 * <p>Aqui cada caminho que o telefone pede e reescrito para
 * {@code mattupolis_phone/personagens/<uuid>/<arquivo>}. A chave e o UUID do perfil com que a
 * conexao entrou: o alt e outro perfil (ver {@code AltData}) e a troca de personagem reconecta, entao
 * a chave muda exatamente quando o personagem muda. A morte com personagem novo mantem o UUID — essa
 * pasta e esvaziada pelo {@link PhoneContactCleanup} no reset da propria conta.
 *
 * <p>Fora de uma conexao (menu principal) nada e reescrito, e o telefone ve a pasta de sempre.
 *
 * <p>Classe pura de proposito (so {@code java.nio}): o redirecionamento e a migracao sao testados sem
 * subir o jogo. Quem conhece o Minecraft e o {@link PhoneSession}.
 */
public final class PhoneCharacterStorage {
    public static final String ROOT = "mattupolis_phone";
    public static final String CHARACTERS = "personagens";
    /** Marca, dentro da pasta da conta principal, que os arquivos antigos da raiz ja vieram para ca. */
    static final String MIGRATED_MARKER = ".aurorion_migrado";

    @Nullable
    private static volatile Path activeDir;

    private PhoneCharacterStorage() {
    }

    /** A pasta do personagem conectado, ou {@code null} fora do mundo. */
    @Nullable
    public static Path activeDir() {
        return activeDir;
    }

    static void activate(@Nullable Path dir) {
        activeDir = dir;
    }

    public static Path characterDir(Path gameDir, UUID profile) {
        return gameDir.resolve(ROOT).resolve(CHARACTERS).resolve(profile.toString());
    }

    /** Servidor offline troca o UUID da conta pelo derivado do nick; alt continua sendo outro perfil. */
    static boolean isMainProfile(UUID profile, String profileName, UUID userId, String userName) {
        if (profile.equals(userId)) return true;
        if (profileName == null || userName == null || !profileName.equalsIgnoreCase(userName)) return false;
        UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + profileName).getBytes(StandardCharsets.UTF_8));
        return profile.equals(offline);
    }

    /**
     * O mesmo arquivo, dentro da pasta do personagem. So mexe em arquivo que esta direto na raiz do
     * telefone ({@code mattupolis_phone/<arquivo>}): o que ja esta numa subpasta passa intacto.
     */
    public static Path redirect(Path original) {
        return redirect(original, activeDir);
    }

    static Path redirect(Path original, @Nullable Path dir) {
        if (original == null || dir == null) return original;
        Path parent = original.getParent();
        Path fileName = original.getFileName();
        if (parent == null || fileName == null || parent.getFileName() == null) return original;
        if (!ROOT.equals(parent.getFileName().toString())) return original;
        return dir.resolve(fileName.toString());
    }

    /**
     * Para as pastas de foto, que o telefone monta com {@code gameDir.resolve("mattupolis_phone")}
     * seguido do nome da subpasta: a raiz vira a pasta do personagem e a subpasta vem dentro dela.
     */
    public static Path resolveRoot(Path base, String other) {
        Path dir = activeDir;
        if (dir != null && ROOT.equals(other)) return dir;
        return base.resolve(other);
    }

    /**
     * Traz para a pasta da conta principal o que o telefone gravou antes desta separacao, uma vez so.
     * Arquivos sem conflito sao movidos. Contatos conflitantes sao unidos de forma aditiva quando
     * ainda nao houve reset, mantendo o legado na raiz como copia de recuperacao; os demais
     * conflitos tambem ficam preservados. So a conta principal pode executar esta migracao, entao
     * um alt nao herda a copia.
     */
    static void migrateLegacy(Path gameDir, Path dir) {
        Path root = gameDir.resolve(ROOT);
        Path marker = dir.resolve(MIGRATED_MARKER);
        if (Files.exists(marker) || !Files.isDirectory(root)) return;
        try {
            Files.createDirectories(dir);
            // A conta principal pode ter duas pastas (UUID online e o derivado do nick em servidor
            // offline). Se uma ja migrou, o que sobrou na raiz e copia de recuperacao ou conflito: a
            // segunda nao importa, senao contatos de um personagem resetado voltariam no outro.
            if (otherFolderMigrated(root.resolve(CHARACTERS), dir)) {
                Files.writeString(marker, "outra pasta da conta principal ja migrou a raiz");
                return;
            }
            try (Stream<Path> entries = Files.list(root)) {
                for (Path entry : (Iterable<Path>) entries::iterator) {
                    String name = entry.getFileName().toString();
                    if (name.equals(CHARACTERS)) continue;
                    Path target = dir.resolve(name);
                    if (Files.exists(target)) {
                        // Sem historico de reset, recupera tambem os contatos que ficaram na raiz.
                        // Se ja houve reset nesta conta, importacao automatica poderia ressuscitar
                        // contatos do morto: os arquivos conflitantes ficam intactos para recuperacao.
                        if (name.equals("contacts.properties")
                                && !Files.exists(dir.resolve("aurorion_retired_contacts.txt"))
                                && !Files.exists(root.resolve("aurorion_retired_contacts.txt"))
                                && !Files.exists(dir.resolve("aurorion_messages.properties"))) {
                            try {
                                PhoneLegacyContacts.merge(entry, target);
                            } catch (IOException | RuntimeException unreadable) {
                                // Agenda antiga ilegivel fica na raiz para recuperacao; o resto migra.
                                AurorionEssentials.LOGGER.warn("Nao consegui unir a agenda antiga {}", entry, unreadable);
                            }
                        }
                        continue;
                    }
                    try {
                        Files.move(entry, target, StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                        Files.move(entry, target);
                    }
                }
            }
            Files.writeString(marker, "migrado da raiz do telefone");
        } catch (IOException e) {
            AurorionEssentials.LOGGER.warn("Nao consegui trazer os dados antigos do telefone para {}", dir, e);
        }
    }

    private static boolean otherFolderMigrated(Path characters, Path dir) throws IOException {
        if (!Files.isDirectory(characters)) return false;
        try (Stream<Path> folders = Files.list(characters)) {
            return folders.anyMatch(folder -> !folder.equals(dir) && Files.exists(folder.resolve(MIGRATED_MARKER)));
        }
    }

    /**
     * Esvazia a pasta do personagem, menos os arquivos em {@code keep}. Usado quando o proprio
     * personagem morre de vez: o novo recebe um celular limpo.
     */
    static void wipe(Path dir, Set<String> keep) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : (Iterable<Path>) entries::iterator) {
                if (keep.contains(entry.getFileName().toString())) continue;
                deleteTree(entry);
            }
        } catch (IOException e) {
            AurorionEssentials.LOGGER.warn("Nao consegui limpar a pasta do telefone {}", dir, e);
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            Files.deleteIfExists(path);
            return;
        }
        try (Stream<Path> tree = Files.walk(path)) {
            for (Path entry : (Iterable<Path>) tree.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(entry);
            }
        }
    }
}
