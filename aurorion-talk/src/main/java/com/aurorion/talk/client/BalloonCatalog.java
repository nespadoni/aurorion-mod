package com.aurorion.talk.client;

import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.style.BalloonTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;

/**
 * Descobre em runtime as texturas de balao/enfeite disponiveis, escaneando
 * {@code assets/aurorion_talk/textures/gui/balloon/{skin,deco}/}.
 *
 * <p>E o que permite adicionar arte nova so soltando um PNG na pasta e reexportando o jar: nada
 * precisa ser registrado em codigo. Recarrega junto com o resto dos resource packs (F3+T ou troca
 * de pack), entao nem precisa reiniciar o cliente durante o desenvolvimento.</p>
 */
public final class BalloonCatalog extends SimplePreparableReloadListener<BalloonCatalog.Scan> {
    private static volatile List<ResourceLocation> skins = List.of(BalloonTextures.DEFAULT_SKIN);
    private static volatile List<ResourceLocation> decorations = List.of();

    /** Todas as skins encontradas, com {@link BalloonTextures#DEFAULT_SKIN} sempre em primeiro. */
    public static List<ResourceLocation> skins() {
        return skins;
    }

    /** Todos os enfeites encontrados. A opcao "nenhum" e responsabilidade da tela, nao do catalogo. */
    public static List<ResourceLocation> decorations() {
        return decorations;
    }

    @Override
    protected Scan prepare(ResourceManager manager, ProfilerFiller profiler) {
        return new Scan(
                scan(manager, BalloonTextures.SKIN_DIR),
                scan(manager, BalloonTextures.DECO_DIR)
        );
    }

    @Override
    protected void apply(Scan scan, ResourceManager manager, ProfilerFiller profiler) {
        List<ResourceLocation> foundSkins = new ArrayList<>(scan.skins());
        foundSkins.remove(BalloonTextures.DEFAULT_SKIN);
        foundSkins.add(0, BalloonTextures.DEFAULT_SKIN);

        skins = List.copyOf(foundSkins);
        decorations = List.copyOf(scan.decorations());

        AurorionTalk.LOGGER.info("Aurorion Talk: {} skin(s), {} enfeite(s) de balao encontrados",
                skins.size(), decorations.size());
    }

    private static List<ResourceLocation> scan(ResourceManager manager, String directory) {
        List<ResourceLocation> found = new ArrayList<>(manager
                .listResources(directory, path -> path.getPath().endsWith(".png"))
                .keySet()
                .stream()
                .filter(id -> id.getNamespace().equals(AurorionTalk.MOD_ID))
                .toList());

        found.sort(ResourceLocation::compareNamespaced);
        return found;
    }

    public record Scan(List<ResourceLocation> skins, List<ResourceLocation> decorations) {
    }
}
