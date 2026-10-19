package com.aurorion.mundos.command;

import com.aurorion.mundos.config.MundosConfig;
import com.aurorion.mundos.perfil.MonsterScaling;
import com.aurorion.mundos.perfil.WorldProfile;
import com.aurorion.mundos.perfil.WorldProfiles;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code /mundos dificuldade} — qual perfil de mundo vale onde.
 *
 * <pre>
 * /mundos dificuldade                        perfis disponiveis e o que vale em cada dimensao carregada
 * /mundos dificuldade ver [dimensao]         os numeros em vigor naquela dimensao, e de onde vieram
 * /mundos dificuldade global [perfil]        o perfil de toda dimensao sem perfil proprio
 * /mundos dificuldade global limpar
 * /mundos dificuldade mundo [dim] [perfil]   o perfil de uma dimensao (aurorion_mundos:nenhum = vanilla)
 * /mundos dificuldade mundo [dim] limpar     volta ao datapack/global
 * /mundos dificuldade aplicar [dimensao]     reajusta agora os monstros ja carregados
 * </pre>
 *
 * <p>Trocar o perfil vale na hora para o dano, a experiencia e o teto de spawn, e para todo monstro
 * que nascer ou carregar com o chunk dali em diante. Os que ja estao andando sao ajustados quando o
 * chunk recarrega — ou agora, com {@code aplicar}, que e a unica coisa aqui que percorre entidades, uma
 * vez, quando a staff pede.
 */
final class DifficultyCommand {
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
            id -> Component.translatable("aurorion_mundos.dificuldade.desconhecido", String.valueOf(id)));

    private static final SuggestionProvider<CommandSourceStack> PROFILES = (context, builder) ->
            SharedSuggestionProvider.suggestResource(
                    Stream.concat(WorldProfiles.ids().stream(), Stream.of(WorldProfile.NONE_ID)), builder);

    private DifficultyCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("dificuldade")
                .executes(DifficultyCommand::overview)
                .then(Commands.literal("ver")
                        .then(Commands.argument("dimensao", DimensionArgument.dimension())
                                .executes(DifficultyCommand::show)))
                .then(Commands.literal("global")
                        .then(Commands.literal("limpar").executes(context -> setGlobal(context, null)))
                        .then(Commands.argument("perfil", ResourceLocationArgument.id()).suggests(PROFILES)
                                .executes(context -> setGlobal(context, profile(context)))))
                .then(Commands.literal("mundo")
                        .then(Commands.argument("dimensao", DimensionArgument.dimension())
                                .then(Commands.literal("limpar").executes(context -> setWorld(context, null)))
                                .then(Commands.argument("perfil", ResourceLocationArgument.id()).suggests(PROFILES)
                                        .executes(context -> setWorld(context, profile(context))))))
                .then(Commands.literal("aplicar")
                        .then(Commands.argument("dimensao", DimensionArgument.dimension())
                                .executes(DifficultyCommand::reapply)));
    }

    private static int overview(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.translatable("aurorion_mundos.dificuldade.perfis")
                .withStyle(ChatFormatting.GOLD), false);
        for (WorldProfile profile : WorldProfiles.all()) {
            source.sendSuccess(() -> Component.literal(" • ")
                    .append(Component.literal(profile.id().toString()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(profile.description().isEmpty() ? "" : " — " + profile.description())
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        String global = MundosConfig.GLOBAL_PROFILE.get();
        source.sendSuccess(() -> Component.translatable("aurorion_mundos.dificuldade.global",
                Component.literal(global.isBlank() ? "vanilla" : global).withStyle(ChatFormatting.YELLOW)), false);
        for (ServerLevel level : source.getServer().getAllLevels()) {
            WorldProfile profile = WorldProfiles.forLevel(level.dimension());
            source.sendSuccess(() -> Component.literal(" " + level.dimension().location() + ": ")
                    .append(Component.literal(profile == null ? "vanilla" : profile.id().toString())
                            .withStyle(profile == null ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA))
                    .append(Component.literal(" (" + WorldProfiles.origin(level.dimension()) + ")")
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return WorldProfiles.all().size();
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = DimensionArgument.getDimension(context, "dimensao");
        WorldProfile profile = WorldProfiles.forLevel(level.dimension());
        CommandSourceStack source = context.getSource();
        if (profile == null) {
            source.sendSuccess(() -> Component.translatable("aurorion_mundos.dificuldade.vanilla",
                    level.dimension().location().toString()), false);
            return 0;
        }
        WorldProfile.Monsters m = profile.monsters();
        List<String> lines = new ArrayList<>();
        lines.add("vida x" + m.health() + " • dano x" + m.damage() + " • armadura +" + m.armor()
                + " • resistência +" + m.toughness() + " • empurrão +" + m.knockback());
        lines.add("velocidade x" + m.speed() + " • alcance x" + m.followRange() + " • experiência x" + m.experience()
                + " • chefes " + (m.bosses() ? "sim" : "não") + " • spawners " + (m.spawners() ? "sim" : "não"));
        lines.add("teto de monstros x" + profile.spawn().monsterCap() + " • teto de animais x" + profile.spawn().creatureCap());
        lines.add("estruturas x" + profile.structures().density() + " (" + profile.structures().sets().size()
                + " conjunto(s) com valor próprio) — vale no próximo boot, só em chunk novo");
        source.sendSuccess(() -> Component.translatable("aurorion_mundos.dificuldade.ver",
                level.dimension().location().toString(), profile.id().toString(),
                WorldProfiles.origin(level.dimension())).withStyle(ChatFormatting.GOLD), false);
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(" " + line).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int setGlobal(CommandContext<CommandSourceStack> context, @Nullable ResourceLocation profile) {
        WorldProfiles.setGlobal(profile);
        context.getSource().sendSuccess(() -> Component.translatable("aurorion_mundos.dificuldade.global_definido",
                profile == null ? "vanilla" : profile.toString()), true);
        return 1;
    }

    private static int setWorld(CommandContext<CommandSourceStack> context, @Nullable ResourceLocation profile)
            throws CommandSyntaxException {
        ServerLevel level = DimensionArgument.getDimension(context, "dimensao");
        WorldProfiles.setFor(level.dimension(), profile);
        context.getSource().sendSuccess(() -> Component.translatable("aurorion_mundos.dificuldade.mundo_definido",
                level.dimension().location().toString(), profile == null ? "-" : profile.toString()), true);
        return 1;
    }

    /** A unica volta por entidades do modulo, e so quando a staff pede. */
    private static int reapply(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = DimensionArgument.getDimension(context, "dimensao");
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && mob.isAlive()) {
                MonsterScaling.apply(mob, false);
                count++;
            }
        }
        int total = count;
        context.getSource().sendSuccess(() -> Component.translatable("aurorion_mundos.dificuldade.aplicado",
                String.valueOf(total), level.dimension().location().toString()), true);
        return total;
    }

    private static ResourceLocation profile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(context, "perfil");
        if (WorldProfiles.get(id) == null) throw UNKNOWN.create(id);
        return id;
    }
}
