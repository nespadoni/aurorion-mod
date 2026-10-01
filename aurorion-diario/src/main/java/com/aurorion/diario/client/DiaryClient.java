package com.aurorion.diario.client;

import com.aurorion.diario.network.DiaryPayloads;
import net.minecraft.client.Minecraft;

/** Recebe os pacotes do diário no cliente. Só é carregada no cliente (ver DiaryNetwork). */
public final class DiaryClient {
    private DiaryClient() {
    }

    public static void open(DiaryPayloads.Open payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DiaryScreen screen) screen.refresh(payload);
        else minecraft.setScreen(new DiaryScreen(payload));
    }

    public static void entry(DiaryPayloads.Entry payload) {
        if (Minecraft.getInstance().screen instanceof DiaryScreen screen) screen.load(payload);
    }

    public static void status(DiaryPayloads.Status payload) {
        if (Minecraft.getInstance().screen instanceof DiaryScreen screen) screen.onStatus(payload);
    }

    public static void conflict(DiaryPayloads.Conflict payload) {
        if (Minecraft.getInstance().screen instanceof DiaryScreen screen) screen.onConflict(payload);
    }
}
