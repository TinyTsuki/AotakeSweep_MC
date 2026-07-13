package xin.vanilla.aotake.network;

import org.junit.Test;
import xin.vanilla.aotake.network.packet.ChunkVaultNavigateToServer;
import xin.vanilla.aotake.network.packet.ChunkVaultPageSyncToClient;
import xin.vanilla.aotake.network.packet.ClearDustbinToServer;
import xin.vanilla.aotake.network.packet.DustbinPageSyncToClient;
import xin.vanilla.aotake.network.packet.GhostCameraToClient;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.network.packet.PlayerConfigSyncToServer;
import xin.vanilla.aotake.network.packet.SweepDataSyncToClient;
import xin.vanilla.banira.api.BaniraIdentifier;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 验证迁移到 Banira 网络抽象后的 packet 基本契约。
 */
public class NetworkPacketContractTest {

    @Test
    public void packetsUseLoaderNeutralMarker() {
        assertTrue(new OpenDustbinToServer(1) instanceof NetworkPacket);
        assertTrue(new ChunkVaultNavigateToServer(-1) instanceof NetworkPacket);
        assertTrue(new ClearDustbinToServer(true, false) instanceof NetworkPacket);
        assertTrue(new PlayerConfigSyncToServer(true, true) instanceof NetworkPacket);
        assertTrue(decodedSweepData() instanceof NetworkPacket);
        assertTrue(new GhostCameraToClient(7, false) instanceof NetworkPacket);
        assertTrue(new DustbinPageSyncToClient(2, 9) instanceof NetworkPacket);
        assertTrue(new ChunkVaultPageSyncToClient(3, 11) instanceof NetworkPacket);
    }

    @Test
    public void packetsRoundTripThroughBaniraBuffer() {
        assertRoundTrip(new OpenDustbinToServer(4), OpenDustbinToServer::toBytes, OpenDustbinToServer::new);
        assertRoundTrip(new ChunkVaultNavigateToServer(-2), ChunkVaultNavigateToServer::toBytes, ChunkVaultNavigateToServer::new);
        assertRoundTrip(new ClearDustbinToServer(true, false), ClearDustbinToServer::toBytes, ClearDustbinToServer::new);
        assertRoundTrip(new PlayerConfigSyncToServer(false, true), PlayerConfigSyncToServer::toBytes, PlayerConfigSyncToServer::new);
        assertRoundTrip(decodedSweepData(), SweepDataSyncToClient::toBytes, SweepDataSyncToClient::new);
        assertRoundTrip(new GhostCameraToClient(42, true), GhostCameraToClient::toBytes, GhostCameraToClient::new);
        assertRoundTrip(new DustbinPageSyncToClient(5, 13), DustbinPageSyncToClient::toBytes, DustbinPageSyncToClient::new);
        assertRoundTrip(new ChunkVaultPageSyncToClient(6, 17), ChunkVaultPageSyncToClient::toBytes, ChunkVaultPageSyncToClient::new);
    }

    private static SweepDataSyncToClient decodedSweepData() {
        TestBuffer buffer = new TestBuffer();
        buffer.writeLong(100L);
        buffer.writeLong(200L);
        buffer.writeLong(300L);
        buffer.writeBoolean(true);
        buffer.writeBoolean(false);
        return new SweepDataSyncToClient(buffer.reader());
    }

    private static <T extends NetworkPacket> void assertRoundTrip(T packet,
                                                                  BiConsumer<T, BaniraPacketBuffer> encoder,
                                                                  Function<BaniraPacketBuffer, T> decoder) {
        TestBuffer encoded = new TestBuffer();
        encoder.accept(packet, encoded);
        T decoded = decoder.apply(encoded.reader());

        TestBuffer reencoded = new TestBuffer();
        encoder.accept(decoded, reencoded);

        assertEquals(encoded.values, reencoded.values);
    }

    private static final class TestBuffer implements BaniraPacketBuffer {
        private final List<Object> values = new ArrayList<>();
        private int readIndex;

        private TestBuffer reader() {
            TestBuffer copy = new TestBuffer();
            copy.values.addAll(values);
            return copy;
        }

        @Override
        public int readVarInt() {
            return readInt();
        }

        @Override
        public void writeVarInt(int value) {
            writeInt(value);
        }

        @Override
        public String readUtf() {
            return (String) next();
        }

        @Override
        public String readUtf(int maxLength) {
            return readUtf();
        }

        @Override
        public void writeUtf(String value) {
            values.add(value);
        }

        @Override
        public void writeUtf(String value, int maxLength) {
            writeUtf(value);
        }

        @Override
        public int readInt() {
            return (Integer) next();
        }

        @Override
        public void writeInt(int value) {
            values.add(value);
        }

        @Override
        public long readLong() {
            return (Long) next();
        }

        @Override
        public void writeLong(long value) {
            values.add(value);
        }

        @Override
        public boolean readBoolean() {
            return (Boolean) next();
        }

        @Override
        public void writeBoolean(boolean value) {
            values.add(value);
        }

        @Override
        public byte readByte() {
            return (Byte) next();
        }

        @Override
        public void writeByte(int value) {
            values.add((byte) value);
        }

        @Override
        public double readDouble() {
            return (Double) next();
        }

        @Override
        public void writeDouble(double value) {
            values.add(value);
        }

        @Override
        public UUID readUuid() {
            return (UUID) next();
        }

        @Override
        public void writeUuid(UUID value) {
            values.add(value);
        }

        @Override
        public <T extends Enum<T>> T readEnum(Class<T> enumClass) {
            return enumClass.cast(next());
        }

        @Override
        public void writeEnum(Enum<?> value) {
            values.add(value);
        }

        @Override
        public BaniraIdentifier readIdentifier() {
            return (BaniraIdentifier) next();
        }

        @Override
        public void writeIdentifier(BaniraIdentifier value) {
            values.add(value);
        }

        private Object next() {
            return values.get(readIndex++);
        }
    }
}
