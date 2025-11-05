package xin.vanilla.aotake.network;

import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.network.packet.*;

public class ModNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static int ID = 0;
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            AotakeSweep.createResource("main_network")
            , () -> PROTOCOL_VERSION
            , clientVersion -> true      // 客户端版本始终有效
            , serverVersion -> true       // 服务端版本始终有效
    );

    public static int nextID() {
        return ID++;
    }

    public static void registerPackets() {
        INSTANCE.registerMessage(nextID(), OpenDustbinToServer.class, OpenDustbinToServer::toBytes, OpenDustbinToServer::new, OpenDustbinToServer::handle);
        INSTANCE.registerMessage(nextID(), ClearDustbinToServer.class, ClearDustbinToServer::toBytes, ClearDustbinToServer::new, ClearDustbinToServer::handle);
        INSTANCE.registerMessage(nextID(), ClientLoadedToServer.class, ClientLoadedToServer::toBytes, ClientLoadedToServer::new, ClientLoadedToServer::handle);
        INSTANCE.registerMessage(nextID(), CustomConfigSyncToClient.class, CustomConfigSyncToClient::toBytes, CustomConfigSyncToClient::new, CustomConfigSyncToClient::handle);
        INSTANCE.registerMessage(nextID(), SweepTimeSyncToClient.class, SweepTimeSyncToClient::toBytes, SweepTimeSyncToClient::new, SweepTimeSyncToClient::handle);
    }
}
