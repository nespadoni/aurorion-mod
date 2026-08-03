package com.aurorion.talk.mixin;

import com.aurorion.talk.client.BalloonMessage;
import com.aurorion.talk.config.TalkConfig;
import com.aurorion.talk.util.BalloonHolder;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin extends Player implements BalloonHolder {
    @Unique
    private final List<BalloonMessage> aurorion_talk$balloons = new ArrayList<>(4);

    public AbstractClientPlayerMixin(Level level, BlockPos pos, float yRot, GameProfile profile) {
        super(level, pos, yRot, profile);
    }

    @Override
    public void aurorion_talk$addBalloon(BalloonMessage message) {
        aurorion_talk$balloons.add(message);

        int max = TalkConfig.MAX_BALLOONS.get();
        while (aurorion_talk$balloons.size() > max) {
            aurorion_talk$balloons.remove(0);
        }
    }

    @Override
    public void aurorion_talk$pruneBalloons(long gameTime) {
        for (int i = aurorion_talk$balloons.size() - 1; i >= 0; i--) {
            if (aurorion_talk$balloons.get(i).expiresAtTick() <= gameTime) {
                aurorion_talk$balloons.remove(i);
            }
        }
    }

    @Override
    public List<BalloonMessage> aurorion_talk$getBalloons() {
        return aurorion_talk$balloons;
    }
}
