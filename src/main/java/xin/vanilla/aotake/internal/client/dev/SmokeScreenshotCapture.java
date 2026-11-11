package xin.vanilla.aotake.internal.client.dev;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Capture the completed frame before the smoke runner changes screens.
 */
public final class SmokeScreenshotCapture {
    private static final Queue<Request> REQUESTS = new ArrayDeque<>();

    private SmokeScreenshotCapture() {
    }

    public static CompletableFuture<Void> capture(Path path, Consumer<NativeImage> check) {
        var future = new CompletableFuture<Void>();
        REQUESTS.add(new Request(path, check, future));
        return future;
    }

    public static void afterFrame() {
        while (!REQUESTS.isEmpty()) {
            Request request = REQUESTS.remove();
            try {
                Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget(), image -> {
                    try (image) {
                        request.check.accept(image);
                        Files.createDirectories(request.path.toAbsolutePath().getParent());
                        image.writeToFile(request.path);
                        request.future.complete(null);
                    } catch (Throwable failure) {
                        request.future.completeExceptionally(failure);
                    }
                });
            } catch (Throwable failure) {
                request.future.completeExceptionally(failure);
            }
        }
    }

    private record Request(Path path, Consumer<NativeImage> check, CompletableFuture<Void> future) {
    }
}
