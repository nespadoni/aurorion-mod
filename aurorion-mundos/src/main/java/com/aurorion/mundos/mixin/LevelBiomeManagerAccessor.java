package com.aurorion.mundos.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Abre o {@code biomeManager} de {@link Level} para escrita.
 *
 * <p>O campo e {@code private final} e so e escrito no construtor, a partir de um seed que chega por
 * parametro. Trocar o parametro exigiria injetar em argumento de construtor; trocar o campo depois
 * de pronto e uma atribuicao. Ver {@link ServerLevelSeedMixin} para o porque de ele precisar mudar.
 *
 * <p>Mixin de acessor nao acrescenta bytecode a metodo nenhum — e a forma mais barata de mexer numa
 * classe tao disputada quanto {@code Level} num modpack pesado.
 */
@Mixin(Level.class)
public interface LevelBiomeManagerAccessor {

    @Mutable
    @Accessor("biomeManager")
    void aurorion_mundos$setBiomeManager(BiomeManager biomeManager);
}
