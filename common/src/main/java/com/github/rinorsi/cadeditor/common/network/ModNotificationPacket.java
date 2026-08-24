package com.github.rinorsi.cadeditor.common.network;

import net.minecraft.network.FriendlyByteBuf;

public final class ModNotificationPacket {
    /**
     * Mod notification reported by the client, carrying the client mod version,
     * used by the server to check whether the version is too old.
     * During read, empty packets from old clients (without a version field) are handled compatibly, returning an empty version.
     */
    public record Client(String modVersion) {
        public static final Client EMPTY = new Client("");
        public static final PacketSerializer<Client> SERIALIZER = new PacketSerializer<>() {
            @Override
            public void write(Client obj, FriendlyByteBuf buf) {
                buf.writeUtf(obj.modVersion());
            }

            @Override
            public Client read(FriendlyByteBuf buf) {
                try {
                    return new Client(buf.readUtf());
                } catch (Exception e) {
                    // Old clients send an empty packet, treated as unknown/too-old version
                    return EMPTY;
                }
            }
        };
    }

    public record Server() {
        public static final Server INSTANCE = new Server();
        public static final PacketSerializer<Server> SERIALIZER = PacketSerializer.empty(INSTANCE);
    }
}
