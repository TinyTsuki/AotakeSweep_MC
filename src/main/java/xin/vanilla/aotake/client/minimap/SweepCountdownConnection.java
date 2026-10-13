package xin.vanilla.aotake.client.minimap;

import xin.vanilla.banira.common.network.BaniraNetworkContext;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class SweepCountdownConnection {
    private final Supplier<Object> currentConnection;
    private final SweepCountdownState state;
    private Object connection;
    private long generation;

    public SweepCountdownConnection(LongSupplier clock, Supplier<Object> currentConnection) {
        this.currentConnection = Objects.requireNonNull(currentConnection, "currentConnection");
        this.state = new SweepCountdownState(clock);
    }

    public synchronized void beginConnection() {
        Object key = currentConnection.get();
        if (key != connection || key == null) {
            connection = key;
            generation = state.beginConnection();
        }
    }

    public void accept(BaniraNetworkContext context, long now, long deadline, long interval, Runnable accepted) {
        if (!context.isClientSide()) return;
        Object key = context.connectionKey();
        final long packetGeneration;
        synchronized (this) {
            if (key == null || key != currentConnection.get()) return;
            beginConnection();
            packetGeneration = generation;
        }
        context.enqueueWork(() -> {
            synchronized (SweepCountdownConnection.this) {
                if (connection != key || key != currentConnection.get() || generation != packetGeneration) return;
                if (state.accept(packetGeneration, now, deadline, interval)) accepted.run();
            }
        });
    }

    public synchronized String formattedTime() {
        if (connection == null || connection != currentConnection.get()) return "";
        return state.formattedTime();
    }

    public synchronized void reset() {
        connection = null;
        generation = state.beginConnection();
    }
}
