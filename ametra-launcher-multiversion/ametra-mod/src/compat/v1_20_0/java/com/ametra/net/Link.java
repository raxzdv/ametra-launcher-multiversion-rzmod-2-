package com.ametra.net;

import com.ametra.Compat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Canal de red (API clásica con PacketByteBuf, Minecraft 1.20.1 a 1.20.4). */
final class Link {
    private Link() {}

    private static final Identifier ID = Compat.id("ametra", "sync");

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (client, handler, buf, sender) -> {
            byte[] b = new byte[buf.readableBytes()];
            buf.readBytes(b);
            client.execute(() -> Probe.onFrame(b));
        });
    }

    static void send(byte[] data) {
        try {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBytes(data);
            ClientPlayNetworking.send(ID, buf);
        } catch (Throwable ignored) {
            // sin conexión
        }
    }
}
