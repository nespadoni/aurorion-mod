package com.aurorion.economia.land;

import com.aurorion.economia.money.Money;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** A cadastral rectangle in X/Z (inclusive blocks), owned by an immutable character ID. */
public record LandDeed(UUID id, UUID ownerCharacter, UUID brokerCharacter, String ownerName,
                       ResourceLocation dimension, int x, int z, int width, int length,
                       int zone, long paid, long soldAt) {
    public LandDeed {
        if (id == null || ownerCharacter == null || brokerCharacter == null || dimension == null
                || ownerName == null || ownerName.length() > 64 || width < 1 || length < 1
                || width > 1024 || length > 1024 || Math.abs((long) x) > 30_000_000
                || Math.abs((long) z) > 30_000_000 || (long) x + width > 30_000_000
                || (long) z + length > 30_000_000 || zone < 0 || zone > 4 || paid < 1 || paid > Money.MAX)
            throw new IllegalArgumentException("Invalid land deed");
    }

    public long area() { return (long) width * length; }
    public int endX() { return x + width - 1; }
    public int endZ() { return z + length - 1; }
    public boolean overlaps(LandDeed other) {
        return dimension.equals(other.dimension) && x <= other.endX() && endX() >= other.x
                && z <= other.endZ() && endZ() >= other.z;
    }
    public static long minimum(int width, int length, long rate, long floor) {
        if (width < 1 || length < 1 || width > 1024 || length > 1024
                || rate < 1 || rate > Money.MAX / ((long) width * length) || floor < 1 || floor > Money.MAX)
            throw new IllegalArgumentException("Invalid land price");
        return Math.max((long) width * length * rate, floor);
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id); tag.putUUID("OwnerCharacter", ownerCharacter);
        tag.putUUID("BrokerCharacter", brokerCharacter); tag.putString("OwnerName", ownerName);
        tag.putString("Dimension", dimension.toString()); tag.putInt("X", x); tag.putInt("Z", z);
        tag.putInt("Width", width); tag.putInt("Length", length); tag.putInt("Zone", zone);
        tag.putLong("Paid", paid); tag.putLong("SoldAt", soldAt);
        return tag;
    }
    public static LandDeed load(CompoundTag tag) {
        return new LandDeed(tag.getUUID("Id"), tag.getUUID("OwnerCharacter"), tag.getUUID("BrokerCharacter"),
                tag.getString("OwnerName"), ResourceLocation.parse(tag.getString("Dimension")),
                tag.getInt("X"), tag.getInt("Z"), tag.getInt("Width"), tag.getInt("Length"),
                tag.getInt("Zone"), tag.getLong("Paid"), tag.getLong("SoldAt"));
    }
}
