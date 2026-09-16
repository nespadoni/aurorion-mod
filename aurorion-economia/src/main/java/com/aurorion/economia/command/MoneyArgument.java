package com.aurorion.economia.command;

import com.aurorion.economia.money.Money;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Le a quantia de um comando como texto e converte para fragmentos.
 *
 * <p>Nao e um {@code ArgumentType} registrado de proposito: um tipo proprio precisaria ser
 * sincronizado com o cliente, e um cliente sem o mod deixaria de conseguir digitar o comando. Uma
 * palavra comum funciona em qualquer cliente.</p>
 */
public final class MoneyArgument {
    private static final SimpleCommandExceptionType INVALID = new SimpleCommandExceptionType(
            Component.translatable("commands.aurorion_economia.quantiaInvalida"));

    private MoneyArgument() {
    }

    public static long get(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        try {
            return Money.parse(com.mojang.brigadier.arguments.StringArgumentType.getString(context, name));
        } catch (Money.MoneyFormatException e) {
            throw INVALID.create();
        }
    }
}
