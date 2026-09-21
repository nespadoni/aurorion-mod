package com.aurorion.profissoes.mixin.spoil;

import com.aurorion.profissoes.compat.FoodThaw;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Conserta o descongelamento do FoodSpoil — ver {@link FoodThaw} para o bug e o porque.
 *
 * <p><b>Por que em {@code setState} e nao nos tres lugares que descongelam:</b> o mod descongela em
 * {@code onContainerClose}, {@code onPlayerTick} e {@code processContainerThawing}, e todos passam
 * por aqui. Um {@code @Inject} em cada um seria tres alvos para o mod quebrar numa atualizacao, e
 * ainda deixaria de fora um quarto lugar que aparecesse depois. A troca de estado e o evento; os tres
 * handlers sao so quem o dispara.
 *
 * <p><b>{@code @At("HEAD")}, e nao {@code RETURN}:</b> precisamos ler o estado <em>anterior</em> do
 * NBT para saber se a comida estava congelada. O {@code setState} do FoodSpoil copia a tag depois de
 * nos, entao o {@code SnapshotTime} que gravamos aqui sobrevive a escrita dele.
 *
 * <p><b>{@code @Coerce}:</b> o parametro e um {@code FreshnessState}, {@code enum} de dentro do
 * FoodSpoil, que nao existe no nosso classpath de compilacao. O {@code @Coerce} deixa recebe-lo como
 * {@code Object}; o nome do estado sai do {@code toString()} do proprio {@code enum}.
 */
@Pseudo
@Mixin(targets = "com.elcuruxa.foodspoil.data.FoodData", remap = false)
public abstract class FoodThawMixin {

    @Inject(method = "setState", at = @At("HEAD"))
    private static void aurorion$reanchorOnThaw(ItemStack stack, @Coerce Object state, CallbackInfo ci) {
        FoodThaw.beforeStateChange(stack, String.valueOf(state));
    }
}
