package xin.vanilla.aotake.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xin.vanilla.aotake.network.packet.ClearDustbinNotice;
import xin.vanilla.aotake.network.packet.OpenDustbinNotice;

public class ModNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";


    public static void registerPackets(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToServer(OpenDustbinNotice.TYPE, OpenDustbinNotice.STREAM_CODEC, OpenDustbinNotice::handle);
        registrar.playToServer(ClearDustbinNotice.TYPE, ClearDustbinNotice.STREAM_CODEC, ClearDustbinNotice::handle);
    }
}
