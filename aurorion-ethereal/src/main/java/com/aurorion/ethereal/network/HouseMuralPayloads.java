package com.aurorion.ethereal.network;

import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/** Payloads limitados e orientados a evento usados pelo Mural da Casa. */
public final class HouseMuralPayloads {
    public static final int TAB_VAULT = 0;
    public static final int TAB_UPGRADES = 1;
    public static final int TAB_PROTECTOR = 2;

    private HouseMuralPayloads() { }

    /** Fragmentos para texto, igual ao {@code Money.format} do aurorion-economia: {@code "12,3 O"}. */
    public static String formatMoney(long fragments) {
        return (fragments < 0 ? "-" : "") + Math.abs(fragments / 10L) + "," + Math.abs(fragments % 10L) + " O";
    }

    /**
     * Lê o que o jogador digitou ({@code "12"}, {@code "12,5"}, {@code "12.5"}) com a regra do
     * {@code Money.parse}: no máximo uma casa decimal, porque a decimal <b>é</b> o fragmento.
     *
     * @return fragmentos, ou -1 para texto inválido
     */
    public static long parseMoney(String input) {
        if (input == null) return -1L;
        String text = input.trim().replace('.', ',');
        int comma = text.indexOf(',');
        String whole = comma < 0 ? text : text.substring(0, comma);
        String decimal = comma < 0 ? "0" : text.substring(comma + 1);
        if (whole.isEmpty()) whole = "0";
        if (decimal.length() != 1 || !whole.chars().allMatch(Character::isDigit)
                || !Character.isDigit(decimal.charAt(0)) || whole.length() > 15) return -1L;
        long fragments = Long.parseLong(whole) * 10L + (decimal.charAt(0) - '0');
        return fragments > 0 ? fragments : -1L;
    }

    /** Um nível de melhoria do cofre; preço negativo = compra desativada pela staff. */
    public record Tier(long capacity, long price) {
        public static final int MAX = 8;
        public static final StreamCodec<RegistryFriendlyByteBuf, Tier> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Tier::capacity,
                ByteBufCodecs.VAR_LONG, Tier::price,
                Tier::new);
    }

    /** Linha do histórico: kind segue {@code HouseUpgradeData.Kind}; agoMillis evita depender do relógio do cliente. */
    public record Movement(String actor, int kind, long amount, long agoMillis) {
        public static final int MAX = 8;
        public static final StreamCodec<RegistryFriendlyByteBuf, Movement> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Movement::actor,
                ByteBufCodecs.VAR_INT, Movement::kind,
                ByteBufCodecs.VAR_LONG, Movement::amount,
                ByteBufCodecs.VAR_LONG, Movement::agoMillis,
                Movement::new);
    }

    /**
     * Estado completo do mural. É reenviado depois de cada ação: com a tela já aberta no mesmo mural,
     * o cliente só troca os dados, sem fechar nem perder o que estava digitado.
     */
    public record Open(
            BlockPos pos, Component houseName, int houseColor, boolean member,
            boolean economyAvailable, long balance, long capacity, int vaultLevel, long wallet,
            long salary, int salaryDays, long nextSalaryMillis,
            List<Tier> tiers, List<Movement> movements,
            int protectorLevel, long remainingMillis, boolean livesAvailable,
            int tab, Component notice, boolean noticeError
    ) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(id("open_house_mural"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC =
                StreamCodec.of(Open::write, Open::read);
        private static final StreamCodec<RegistryFriendlyByteBuf, List<Tier>> TIERS =
                Tier.STREAM_CODEC.apply(ByteBufCodecs.list(Tier.MAX));
        private static final StreamCodec<RegistryFriendlyByteBuf, List<Movement>> MOVEMENTS =
                Movement.STREAM_CODEC.apply(ByteBufCodecs.list(Movement.MAX));

        private static void write(RegistryFriendlyByteBuf buffer, Open value) {
            BlockPos.STREAM_CODEC.encode(buffer, value.pos);
            ComponentSerialization.STREAM_CODEC.encode(buffer, value.houseName);
            buffer.writeInt(value.houseColor);
            buffer.writeBoolean(value.member);
            buffer.writeBoolean(value.economyAvailable);
            buffer.writeVarLong(value.balance);
            buffer.writeVarLong(value.capacity);
            buffer.writeVarInt(value.vaultLevel);
            buffer.writeVarLong(value.wallet);
            buffer.writeVarLong(value.salary);
            buffer.writeVarInt(value.salaryDays);
            buffer.writeVarLong(value.nextSalaryMillis);
            TIERS.encode(buffer, value.tiers);
            MOVEMENTS.encode(buffer, value.movements);
            buffer.writeVarInt(value.protectorLevel);
            buffer.writeVarLong(value.remainingMillis);
            buffer.writeBoolean(value.livesAvailable);
            buffer.writeVarInt(value.tab);
            ComponentSerialization.STREAM_CODEC.encode(buffer, value.notice);
            buffer.writeBoolean(value.noticeError);
        }

        private static Open read(RegistryFriendlyByteBuf buffer) {
            return new Open(BlockPos.STREAM_CODEC.decode(buffer),
                    ComponentSerialization.STREAM_CODEC.decode(buffer), buffer.readInt(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt(),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarLong(),
                    TIERS.decode(buffer), MOVEMENTS.decode(buffer),
                    buffer.readVarInt(), buffer.readVarLong(), buffer.readBoolean(),
                    buffer.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buffer), buffer.readBoolean());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Target(UUID id, String name, int lives, int maxLives, boolean online) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Target> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Target::id,
                ByteBufCodecs.stringUtf8(64), Target::name,
                ByteBufCodecs.VAR_INT, Target::lives,
                ByteBufCodecs.VAR_INT, Target::maxLives,
                ByteBufCodecs.BOOL, Target::online,
                Target::new);
    }

    public record OpenTargets(BlockPos pos, Component houseName, List<Target> targets)
            implements CustomPacketPayload {
        public static final int MAX_TARGETS = 128;
        public static final Type<OpenTargets> TYPE = new Type<>(id("open_protector_targets"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenTargets> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenTargets::pos,
                ComponentSerialization.STREAM_CODEC, OpenTargets::houseName,
                Target.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TARGETS)), OpenTargets::targets,
                OpenTargets::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenProtector(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenProtector> TYPE = new Type<>(id("open_protector"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenProtector> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, OpenProtector::pos, OpenProtector::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record GrantLife(BlockPos pos, UUID target) implements CustomPacketPayload {
        public static final Type<GrantLife> TYPE = new Type<>(id("grant_house_life"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GrantLife> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, GrantLife::pos,
                UUIDUtil.STREAM_CODEC, GrantLife::target,
                GrantLife::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Depositar (true) ou sacar (false), em fragmentos. O servidor reconfere tudo. */
    public record VaultTransfer(BlockPos pos, boolean deposit, long amount) implements CustomPacketPayload {
        public static final Type<VaultTransfer> TYPE = new Type<>(id("house_vault_transfer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, VaultTransfer> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, VaultTransfer::pos,
                ByteBufCodecs.BOOL, VaultTransfer::deposit,
                ByteBufCodecs.VAR_LONG, VaultTransfer::amount,
                VaultTransfer::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Compra do próximo nível; o preço visto na tela vai junto e precisa bater com o do servidor. */
    public record BuyVaultUpgrade(BlockPos pos, int nextLevel, long quotedPrice) implements CustomPacketPayload {
        public static final Type<BuyVaultUpgrade> TYPE = new Type<>(id("buy_house_vault_upgrade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BuyVaultUpgrade> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, BuyVaultUpgrade::pos,
                ByteBufCodecs.VAR_INT, BuyVaultUpgrade::nextLevel,
                ByteBufCodecs.VAR_LONG, BuyVaultUpgrade::quotedPrice,
                BuyVaultUpgrade::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AurorionEthereal.MOD_ID, path);
    }
}
