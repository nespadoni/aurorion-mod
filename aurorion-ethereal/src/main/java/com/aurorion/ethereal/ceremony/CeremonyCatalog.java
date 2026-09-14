package com.aurorion.ethereal.ceremony;

import com.aurorion.core.datapack.DatapackRegistry;
import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.Comparator;
import java.util.List;

/**
 * As perguntas da cerimonia, vindas dos datapacks e recarregadas a cada {@code /reload}.
 *
 * <p>Mesma estrutura do {@link com.aurorion.ethereal.house.HouseCatalog}, pelo mesmo motivo: e a
 * terceira pasta de JSON do ecossistema com exatamente este comportamento, e ela nao volta a ser
 * codigo copiado — quem faz o trabalho e o {@link DatapackRegistry} do {@code aurorion-core}.
 */
public final class CeremonyCatalog {
    public static final String DIRECTORY = "aurorion/ceremony_questions";

    private static final Comparator<CeremonyQuestion> ORDER = Comparator
            .comparingInt(CeremonyQuestion::order)
            .thenComparing(question -> question.id().toString());

    private static final DatapackRegistry<CeremonyQuestion> REGISTRY =
            new DatapackRegistry<>(DIRECTORY, AurorionEthereal.LOGGER, CeremonyQuestion::codec, ORDER);

    private CeremonyCatalog() {
    }

    public static PreparableReloadListener listener() {
        return REGISTRY.listener();
    }

    /** Todas as perguntas, na ordem em que serao feitas. Nunca nula, pode ser vazia. */
    public static List<CeremonyQuestion> all() {
        return REGISTRY.all();
    }

    public static boolean isEmpty() {
        return REGISTRY.isEmpty();
    }
}
