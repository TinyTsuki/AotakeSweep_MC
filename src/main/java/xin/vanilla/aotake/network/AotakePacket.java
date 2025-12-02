package xin.vanilla.aotake.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public interface AotakePacket {
    ResourceLocation id();

    FriendlyByteBuf toBytes(FriendlyByteBuf buf);
}
