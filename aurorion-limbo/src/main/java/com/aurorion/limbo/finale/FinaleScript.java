package com.aurorion.limbo.finale;

import com.aurorion.limbo.config.FinaleConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.List;

/** Bounded content snapshot: a config reload cannot shorten a running character's final minute. */
public record FinaleScript(int riseSeconds, int phraseSeconds, int creditsSeconds,
                           String phrase, List<String> paragraphs, String music) {
    public static final int HOLD_SECONDS = 60;
    public static final int MAX_PARAGRAPHS = 128;
    public static final int MAX_TEXT = 512;

    public FinaleScript {
        riseSeconds = Math.clamp(riseSeconds, 5, 120);
        phraseSeconds = Math.clamp(phraseSeconds, 5, 120);
        creditsSeconds = Math.clamp(creditsSeconds, 20, 1800);
        phrase = phrase.substring(0, Math.min(MAX_TEXT, phrase.length()));
        paragraphs = paragraphs.stream().limit(MAX_PARAGRAPHS)
                .map(s -> s.substring(0, Math.min(MAX_TEXT, s.length()))).toList();
        if (music.length() > 256 || net.minecraft.resources.ResourceLocation.tryParse(music) == null) music = "aurorion_limbo:finale";
    }

    public long creditsStartMillis() { return (riseSeconds + phraseSeconds) * 1000L; }
    public long deathTitleMillis() { return creditsStartMillis() + creditsSeconds * 1000L; }
    public long totalMillis() { return deathTitleMillis() + HOLD_SECONDS * 1000L; }

    public static FinaleScript configured() {
        return new FinaleScript(FinaleConfig.RISE_SECONDS.get(), FinaleConfig.PHRASE_SECONDS.get(),
                FinaleConfig.CREDITS_SECONDS.get(), FinaleConfig.PHRASE.get(),
                List.copyOf(FinaleConfig.PARAGRAPHS.get()), FinaleConfig.MUSIC.get());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(riseSeconds); buf.writeVarInt(phraseSeconds); buf.writeVarInt(creditsSeconds);
        buf.writeUtf(phrase, MAX_TEXT); buf.writeVarInt(paragraphs.size());
        paragraphs.forEach(s -> buf.writeUtf(s, MAX_TEXT));
        buf.writeUtf(music, 256);
    }

    public static FinaleScript read(RegistryFriendlyByteBuf buf) {
        int rise = buf.readVarInt(), phraseTime = buf.readVarInt(), credits = buf.readVarInt();
        String phrase = buf.readUtf(MAX_TEXT);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_PARAGRAPHS) throw new IllegalArgumentException("Invalid finale paragraph count");
        var lines = new java.util.ArrayList<String>(count);
        for (int i = 0; i < count; i++) lines.add(buf.readUtf(MAX_TEXT));
        return new FinaleScript(rise, phraseTime, credits, phrase, lines, buf.readUtf(256));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Rise", riseSeconds); tag.putInt("PhraseTime", phraseSeconds); tag.putInt("Credits", creditsSeconds);
        tag.putString("Phrase", phrase); tag.putString("Music", music);
        ListTag lines = new ListTag();
        paragraphs.forEach(s -> lines.add(StringTag.valueOf(s)));
        tag.put("Paragraphs", lines);
        return tag;
    }

    public static FinaleScript load(CompoundTag tag) {
        var lines = tag.getList("Paragraphs", Tag.TAG_STRING).stream().map(Tag::getAsString).toList();
        return new FinaleScript(tag.getInt("Rise"), tag.getInt("PhraseTime"), tag.getInt("Credits"),
                tag.getString("Phrase"), lines, tag.getString("Music"));
    }
}
