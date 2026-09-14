package com.aurorion.limbo.client;

import com.aurorion.limbo.network.LimboNoticePayload;
import com.aurorion.limbo.network.LimboStatusPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/** Estado somente do dono da tela; rede por transicao, interpolacao local entre snapshots. */
public final class ClientLimbo {
    public static final ResourceLocation FONT = ResourceLocation.parse("aurorion_limbo:limbo");
    private static final String[] NOTICE_KEYS = {"queda", "chegada", "prazo", "coleira", "janela",
            "porta", "saida", "resgate", "vencido", "publico"};
    public static final Component EYEBROW = Component.translatable("aurorion_limbo.ui.eyebrow");
    public static final Component NAME = title("aurorion_limbo.ui.name");
    public static final Component REMAINING = Component.translatable("aurorion_limbo.ui.remaining");
    private static final Component[] STAGES = {
            Component.translatable("aurorion_limbo.ui.waiting"), Component.translatable("aurorion_limbo.ui.walking"),
            Component.translatable("aurorion_limbo.ui.door"), Component.translatable("aurorion_limbo.ui.expired")};
    private static long remaining = -1, lastTickNanos;
    private static long clockSecond = -1;
    private static String clock = "00:00:00";
    private static int stage, particleTick;
    private static BlockPos door = BlockPos.ZERO;
    private static Direction facing = Direction.SOUTH;
    private static LimboNoticePayload notice;
    private static Component noticeTitle = Component.empty();
    private static long noticeStart;
    private static int revision;

    private ClientLimbo() { }
    private static Component title(String key) { return Component.translatable(key).withStyle(s -> s.withFont(FONT)); }

    public static void status(LimboStatusPayload payload) {
        remaining = Math.clamp(payload.remainingMillis(), -1, 14L * 24 * 3_600_000);
        stage = Math.clamp(payload.stage(), 0, 3);
        door = payload.door();
        facing = Direction.from2DDataValue(Math.floorMod(payload.facing(), 4));
        lastTickNanos = System.nanoTime();
        updateClock();
    }

    public static void notice(LimboNoticePayload payload) {
        int kind = payload.kind();
        if (kind < 0 || kind >= NOTICE_KEYS.length) return;
        // Aviso publico/coleira nao apagam a cena privada da queda ou chegada.
        if ((kind == LimboNoticePayload.PUBLIC || kind == LimboNoticePayload.LEASH)
                && notice != null && ageSeconds() < durationSeconds()) return;
        notice = payload;
        noticeTitle = title("aurorion_limbo.title." + NOTICE_KEYS[kind]);
        noticeStart = System.nanoTime();
        revision++;
        var player = Minecraft.getInstance().player;
        if (player != null && kind != LimboNoticePayload.PUBLIC && kind != LimboNoticePayload.LEASH) {
            // SOUL_ESCAPE e declarado como Holder.Reference no vanilla e AMETHYST_BLOCK_CHIME como
            // SoundEvent; sem o value() os dois ramos do ternario nao tem tipo comum.
            player.playSound(kind == LimboNoticePayload.DOOR || kind == LimboNoticePayload.RESCUED
                    ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.SOUL_ESCAPE.value(), .28F, .7F);
        }
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (remaining >= 0 && !mc.isPaused()) {
            remaining = Math.max(0, remaining - Math.clamp((now - lastTickNanos) / 1_000_000L, 0, 5000));
            updateClock();
        }
        lastTickNanos = now;
        if (notice != null && ageSeconds() >= durationSeconds()) { notice = null; revision++; }
        if (++particleTick % 4 != 0 || stage != 2 || remaining < 0 || mc.isPaused()
                || mc.level == null || mc.player == null || mc.options.particles().get() == ParticleStatus.MINIMAL
                || mc.player.distanceToSqr(door.getCenter()) > 48 * 48) return;
        // Duas particulas a cada quatro ticks, so perto da Porta do dono.
        double t = (particleTick % 120) / 120.0 * Math.PI * 2;
        Direction side = facing.getClockWise();
        double offset = Math.cos(t) * .7;
        double x = door.getX() + .5 + side.getStepX() * offset;
        double z = door.getZ() + .5 + side.getStepZ() * offset;
        double y = door.getY() + 1.7 + Math.sin(t) * .8;
        mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, .012, 0);
        mc.level.addParticle(ParticleTypes.ENCHANT, x, y + .2, z, 0, .06, 0);
    }

    private static void updateClock() {
        long second = Math.max(0, (remaining + 999) / 1000);
        if (second == clockSecond) return;
        clockSecond = second;
        clock = two(second / 3600) + ":" + two(second / 60 % 60) + ":" + two(second % 60);
    }
    private static String two(long value) { return value < 10 ? "0" + value : Long.toString(value); }
    public static boolean active() { return remaining >= 0; }
    public static String clock() { return clock; }
    public static Component stage() { return STAGES[stage]; }
    public static LimboNoticePayload notice() { return notice; }
    public static Component noticeTitle() { return noticeTitle; }
    public static int revision() { return revision; }
    public static float ageSeconds() { return (System.nanoTime() - noticeStart) / 1_000_000_000F; }
    public static boolean major() {
        return notice != null && notice.kind() != LimboNoticePayload.PUBLIC
                && notice.kind() != LimboNoticePayload.DEADLINE && notice.kind() != LimboNoticePayload.LEASH;
    }
    public static float durationSeconds() { return major() ? 8 : 4.5F; }
    public static int accent() {
        if (notice == null) return 0x9BBFD6;
        return switch (notice.kind()) {
            case LimboNoticePayload.RESCUED, LimboNoticePayload.DEADLINE -> 0xDDB878;
            case LimboNoticePayload.FALL, LimboNoticePayload.EXPIRED -> 0xD99891;
            default -> 0x9BBFD6;
        };
    }
    public static void clear() {
        remaining = -1; notice = null; particleTick = 0; clockSecond = -1; revision++;
    }
}
