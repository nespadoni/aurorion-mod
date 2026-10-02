package net.puffish.skillsmod.client.config;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public sealed interface ClientIconConfig permits ClientIconConfig.EffectIconConfig, ClientIconConfig.ItemIconConfig, ClientIconConfig.TextureIconConfig {

	record ItemIconConfig(ItemStack item) implements ClientIconConfig { }

	record EffectIconConfig(StatusEffect effect) implements ClientIconConfig { }

	record TextureIconConfig(Identifier texture) implements ClientIconConfig { }

}
