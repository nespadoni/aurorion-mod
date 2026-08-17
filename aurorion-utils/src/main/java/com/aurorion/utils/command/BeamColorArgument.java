package com.aurorion.utils.command;

import com.aurorion.utils.abduction.BeamColor;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Argumento {@code <cor>} do {@code /abduzir}: sugere a paleta de {@link BeamColor} no chat e
 * devolve o RGB ja resolvido.
 *
 * <p>O ponto de ser um {@link ArgumentType} proprio, em vez de um {@code StringArgumentType.word()}
 * com {@code suggests(...)}, e <b>recusar</b> o que nao for cor. {@code EntityArgument.player()}
 * aceita qualquer palavra de ate 16 caracteres como nome (so falha na hora de resolver o jogador),
 * entao {@code /abduzir Fulano Beltrano} e {@code /abduzir Fulano ciano} sao indistinguiveis pro
 * Brigadier se os dois ramos aceitarem tudo. Com este tipo, o ramo {@code <cor>} falha no parse de
 * "Beltrano" e o comando cai sozinho no ramo {@code <destino>}.</p>
 */
public final class BeamColorArgument implements ArgumentType<Integer> {
    private static final Collection<String> EXAMPLES = List.of("roxo", "ciano", "FF7A00");

    private static final DynamicCommandExceptionType ERROR_UNKNOWN_COLOR =
            new DynamicCommandExceptionType(value ->
                    Component.translatable("argument.aurorion_utils.cor.invalid", value));

    private BeamColorArgument() {
    }

    public static BeamColorArgument beamColor() {
        return new BeamColorArgument();
    }

    public static int getBeamColor(CommandContext<?> context, String name) {
        return context.getArgument(name, Integer.class);
    }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String raw = reader.readUnquotedString();

        Integer rgb = BeamColor.parse(raw);
        if (rgb == null) {
            reader.setCursor(start);
            throw ERROR_UNKNOWN_COLOR.createWithContext(reader, raw);
        }
        return rgb;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(BeamColor.ids(), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
