package net.puffish.skillsmod.api;

import net.minecraft.server.network.ServerPlayerEntity;

public interface Skill {
	Category getCategory();

	String getId();

	State getState(ServerPlayerEntity player);

	void unlock(ServerPlayerEntity player);

	void lock(ServerPlayerEntity player);

	enum State {
		LOCKED,
		AVAILABLE,
		AFFORDABLE,
		UNLOCKED,
		EXCLUDED
	}
}
