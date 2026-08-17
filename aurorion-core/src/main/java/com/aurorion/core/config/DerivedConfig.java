package com.aurorion.core.config;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Um valor caro derivado de uma config, recalculado sozinho quando a config muda.
 *
 * <p>Config guarda texto; o jogo quer objeto. Converter "minecraft:the_nether" em
 * {@code ResourceKey}, ou uma lista de strings num {@code Set}, custa alocacao — e essas conversoes
 * acontecem em caminho quente (a checagem de portal roda a cada tick enquanto alguem esta parado
 * dentro de um). Guardar o resultado e obrigatorio; o problema e <b>quando soltar</b>.
 *
 * <h2>Por que nao um evento de config</h2>
 *
 * <p>A alternativa usual e escutar {@code ModConfigEvent.Reloading} e zerar o cache. Foi o que o
 * {@code aurorion-portais} fazia, com uma classe inteira so para isso. O problema e o mesmo do
 * {@link com.aurorion.core.data.SavedDataAccess}: <b>funciona enquanto alguem lembra de ligar o
 * listener</b>, e some silenciosamente quando esquece — o cache fica velho e o servidor passa a
 * obedecer uma config que ninguem mais ve no arquivo.
 *
 * <p>Aqui o gatilho e o proprio dado: guardamos o valor cru junto com o convertido, e comparamos.
 * Se o texto da config mudou, a conversao refaz. Nao ha evento para esquecer, nao ha ordem de
 * inicializacao para acertar, e editar o arquivo com o servidor no ar aplica na proxima leitura.
 *
 * <pre>{@code
 * private static final DerivedConfig<List<? extends String>, Set<ResourceKey<Level>>> FREE =
 *         new DerivedConfig<>(TransitConfig.FREE_DIMENSIONS::get, TransitGate::parseDimensions);
 *
 * // no uso:
 * FREE.get().contains(dimension)
 * }</pre>
 *
 * <p>A comparacao testa identidade antes de igualdade, entao o caso normal (a config nao mudou e
 * devolve a mesma instancia) custa uma comparacao de referencia e mais nada.
 *
 * @param <S> o tipo cru, como sai da config
 * @param <T> o tipo util, como o jogo quer
 */
public final class DerivedConfig<S, T> {
    private final Supplier<S> source;
    private final Function<S, T> convert;

    @Nullable
    private S lastRaw;
    @Nullable
    private T cached;

    public DerivedConfig(Supplier<S> source, Function<S, T> convert) {
        this.source = source;
        this.convert = convert;
    }

    public T get() {
        S raw = source.get();

        if (cached != null && (raw == lastRaw || Objects.equals(raw, lastRaw))) {
            return cached;
        }

        T converted = convert.apply(raw);
        cached = converted;
        lastRaw = raw;
        return converted;
    }
}
