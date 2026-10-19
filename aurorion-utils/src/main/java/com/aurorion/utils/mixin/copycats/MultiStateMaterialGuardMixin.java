package com.aurorion.utils.mixin.copycats;

import com.aurorion.utils.render.CopycatsCompat;
import com.copycatsplus.copycats.foundation.copycat.multistate.MaterialItemStorage;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Copycats+ 3.0.9: o material de um copycat multiestado sem checagem de nulo.
 *
 * <p>O {@code getMaterial()} padrao da interface faz
 * {@code getMaterialItemStorage().getMaterialItem(getBlock().defaultProperty()).material()} direto. A
 * montagem de malha do Sodium roda em outra thread com uma foto do chunk; se o bloco foi quebrado (o
 * {@code invalidate()} limpa o armazenamento) ou a parte padrao nao esta no mapa, a chave volta nula e o
 * cliente cai com "Encountered exception while building chunk meshes" — o crash de 20/09 no
 * {@code copycat_byte}.
 *
 * <p>Este metodo, na classe concreta, toma o lugar do padrao da interface (metodo de classe vence
 * metodo {@code default}) e faz a mesma coisa, com reserva: a parte padrao; senao, qualquer parte com
 * material; senao, a base de copycat — que e o que o proprio Copycats usa quando nao ha material. Uma
 * passada de malha com o material errado num bloco que acabou de sumir, em vez de um crash.
 *
 * <p>Custo no caminho normal: o mesmo do original, sem o objeto anonimo que ele aloca quando o bloco nao
 * e multiestado.
 */
@Pseudo
@Mixin(targets = {
        "com.copycatsplus.copycats.foundation.copycat.multistate.MultiStateCopycatBlockEntity",
        "com.copycatsplus.copycats.content.copycat.cogwheel.CopycatCogWheelBlockEntity"
}, remap = false)
public abstract class MultiStateMaterialGuardMixin {
    @Shadow
    public abstract MaterialItemStorage getMaterialItemStorage();

    public BlockState getMaterial() {
        MaterialItemStorage storage = getMaterialItemStorage();
        if (storage == null) return CopycatsCompat.copycatBase();
        try {
            String key = CopycatsCompat.defaultProperty(((BlockEntity) (Object) this).getBlockState().getBlock());
            MaterialItemStorage.MaterialItem item = key == null ? null : storage.getMaterialItem(key);
            if (item != null && item.material() != null) return item.material();
            // Mapa lido por outra thread enquanto o principal escreve: um HashMap pode lancar no meio
            // da iteracao. Qualquer falha aqui vira a reserva, nunca um crash do mesher.
            for (MaterialItemStorage.MaterialItem other : storage.getAllMaterialItems()) {
                if (other != null && other.material() != null) return other.material();
            }
        } catch (RuntimeException ignored) {
            // cai na reserva
        }
        return CopycatsCompat.copycatBase();
    }
}
