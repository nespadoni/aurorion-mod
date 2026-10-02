package net.puffish.skillsmod.client.config;

import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.util.Identifier;

import java.util.Optional;

public sealed interface ClientFrameConfig permits ClientFrameConfig.AdvancementFrameConfig, ClientFrameConfig.TextureFrameConfig {

	record AdvancementFrameConfig(AdvancementFrame frame) implements ClientFrameConfig { }

	record TextureFrameConfig(
			Optional<Identifier> lockedTexture,
			Identifier availableTexture,
			Optional<Identifier> affordableTexture,
			Identifier unlockedTexture,
			Optional<Identifier> excludedTexture
	) implements ClientFrameConfig { }

}
