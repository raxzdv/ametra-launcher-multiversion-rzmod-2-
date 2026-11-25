package com.ametra.net;

import com.ametra.Compat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Canal de red (API de payloads, Minecraft 1.20.5 en adelante). */
final class Link {
    private Link() {}

    record Frame(byte[] data) implements CustomPayload {
        static final CustomPayload.Id<Frame> ID = new CustomPayload.Id<>(Compat.id("ametra", "sync"));
        static final PacketCodec<RegistryByteBuf, Frame> CODEC = new PacketCodec<RegistryByteBuf, Frame>() {
            @Override
            public Frame decode(RegistryByteBuf buf) {
                byte[] b = new byte[buf.readableBytes()];
                buf.readBytes(b);
                return new Frame(b);
            }

            @Override
            public void encode(RegistryByteBuf buf, Frame value) {
                buf.writeBytes(value.data());
            }
        };

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    static void init() {
        PayloadTypeRegistry.playS2C().register(Frame.ID, Frame.CODEC);
        PayloadTypeRegistry.playC2S().register(Frame.ID, Frame.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Frame.ID, (payload, context) -> Probe.onFrame(payload.data()));
    }

    static void send(byte[] data) {
        try {
            ClientPlayNetworking.send(new Frame(data));
        } catch (Throwable ignored) {
            // sin conexión
        }
    }
}
