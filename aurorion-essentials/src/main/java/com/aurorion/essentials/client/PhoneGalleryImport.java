package com.aurorion.essentials.client;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Importacao de imagens do PC para a galeria do personagem: pelo botao da galeria (janela de escolher
 * arquivo do sistema), arrastando arquivos para a janela do jogo com a galeria aberta, ou pelo comando
 * {@code /celular importar}. Uma imagem por vez; arquivos arrastados juntos entram em fila.
 */
public final class PhoneGalleryImport {
    private static final Deque<Path> QUEUE = new ArrayDeque<>();
    private static boolean importing;
    private static boolean choosing;

    private PhoneGalleryImport() {
    }

    /** Abre a janela de escolher arquivo do sistema fora da thread do jogo. */
    public static void chooseFromPc() {
        if (choosing) return;
        if (!ready(PhoneUi::toast)) return;
        choosing = true;
        PhoneUi.toast("Escolha a imagem na janela do Windows...");
        Thread thread = new Thread(() -> {
            String chosen = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(3);
                filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg")).flip();
                chosen = TinyFileDialogs.tinyfd_openFileDialog("Importar foto para o celular", "",
                        filters, "Imagens PNG ou JPG", false);
            } catch (Throwable e) {
                AurorionEssentials.LOGGER.warn("Nao consegui abrir a janela de escolher arquivo.", e);
            }
            String result = chosen;
            Minecraft.getInstance().execute(() -> {
                choosing = false;
                if (result == null || result.isBlank()) return;
                try {
                    start(Path.of(result), PhoneUi::toast);
                } catch (InvalidPathException e) {
                    PhoneUi.toast("O caminho da imagem e invalido.");
                }
            });
        }, "Aurorion-Escolher-Foto");
        thread.setDaemon(true);
        thread.start();
    }

    /** Arquivos soltos na janela com a galeria aberta. */
    public static void importDropped(List<Path> paths) {
        if (paths.isEmpty() || !ready(PhoneUi::toast)) return;
        QUEUE.addAll(paths);
        if (!importing) start(QUEUE.poll(), PhoneUi::toast);
    }

    public static boolean busy() {
        return importing;
    }

    /**
     * Le e converte a imagem fora da renderizacao e grava na galeria do personagem ativo.
     * {@code feedback} recebe o resultado na thread do jogo.
     */
    public static boolean start(Path rawSource, Consumer<String> feedback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ready(feedback)) {
            // Fora do mundo a fila nao pode sobreviver: outro personagem herdaria os arquivos.
            QUEUE.clear();
            return false;
        }
        if (importing) {
            feedback.accept("Aguarde a importacao da imagem anterior.");
            return false;
        }
        Path characterDir = PhoneCharacterStorage.activeDir();
        var player = minecraft.player;
        Path source = minecraft.gameDirectory.toPath().resolve(rawSource).normalize();
        importing = true;
        feedback.accept("Importando imagem...");
        CompletableFuture.supplyAsync(() -> {
            try {
                return PhoneImageImporter.prepare(source);
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((png, failure) -> minecraft.execute(() -> {
            try {
                // Nao recria arquivos do personagem anterior apos sair/trocar de personagem.
                if (minecraft.player != player || !characterDir.equals(PhoneCharacterStorage.activeDir())) {
                    QUEUE.clear();
                    return;
                }
                if (failure != null) {
                    Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                    feedback.accept(cause instanceof IOException ? cause.getMessage() : "Nao foi possivel ler essa imagem.");
                    return;
                }
                PhoneImageImporter.save(png, characterDir);
                feedback.accept("Foto importada para a galeria.");
            } catch (IOException | RuntimeException e) {
                feedback.accept("Nao foi possivel salvar a foto na galeria.");
                AurorionEssentials.LOGGER.warn("Nao consegui importar a imagem para a galeria do telefone.", e);
            } finally {
                importing = false;
                Path next = QUEUE.poll();
                if (next != null) start(next, feedback);
            }
        }));
        return true;
    }

    private static boolean ready(Consumer<String> feedback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ModList.get().isLoaded("mattupolis_phone") || minecraft.player == null
                || PhoneCharacterStorage.activeDir() == null) {
            feedback.accept("Entre no mundo com o mod do telefone para importar uma foto.");
            return false;
        }
        return true;
    }
}
