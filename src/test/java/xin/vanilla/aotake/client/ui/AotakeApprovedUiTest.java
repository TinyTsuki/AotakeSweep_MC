package xin.vanilla.aotake.client.ui;

import net.minecraft.inventory.Inventory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiCloseReason;
import xin.vanilla.banira.api.client.ui.UiGameElement;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.internal.client.ui.NativeUiView;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class AotakeApprovedUiTest {
    @BeforeClass
    public static void bootstrap() {
        DustbinUiSessionTest.bootstrap();
    }

    private static class View extends NativeUiView {
        final Map<String, ScreenCoordinate> regions;

        View(Map<String, ScreenCoordinate> regions) {
            super(null);
            this.regions = regions;
        }

        @Override
        public ScreenCoordinate bounds(String id) {
            return regions.get(id);
        }

        UiGameElement element(String id) {
            return gameElements.get(id);
        }
    }

    private DustbinUiContainerIdentityTest.Menu menu() {
        return new DustbinUiContainerIdentityTest.Menu(71, new PlayerInventory(null));
    }

    private DustbinUiSession session(DustbinUiContainerIdentityTest.Menu menu, AtomicInteger closes) throws Exception {
        try {
            return DustbinUiSession.class.getConstructor(net.minecraft.inventory.container.Container.class,
                            boolean.class, BooleanSupplier.class, Consumer.class, Runnable.class, java.util.function.Supplier.class)
                    .newInstance(menu, false, (BooleanSupplier) () -> true,
                            (Consumer<DustbinUiSession.Action>) action -> {
                            }, (Runnable) closes::incrementAndGet, (java.util.function.Supplier<ItemStack>) menu::getCarried);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("Container close must delegate to the original screen", missing);
        }
    }

    @Test
    public void headerAndCloseNeverCoverNativeSlotRegions() {
        DustbinUiContainerIdentityTest.Menu menu = menu();
        Object[] refs = menu.slots.toArray();
        lombok.var session = new DustbinUiSession(menu, false, () -> true, action -> {
        });
        session.state(1, 3, false, false);
        lombok.var regions = session.regions(30, 10, 176, 222);
        for (String id : new String[]{"brand", "title", "page_label", "close"}) {
            ScreenCoordinate header = regions.get(id);
            assertNotNull("Missing compact header " + id, header);
            for (int i = 0; i < 90; i++)
                assertFalse(id + " overlaps slot " + i, overlaps(header, regions.get("slot_" + i)));
        }
        assertEquals(15, regions.get("close").width(), 0);
        assertEquals(15, regions.get("close").height(), 0);
        assertArrayEquals(refs, menu.slots.toArray());
        assertEquals(71, menu.containerId);
    }

    @Test
    public void carriedCloseDelegatesOnceWithoutHandlingTheStackAndStaleViewsCannotClose() throws Exception {
        DustbinUiContainerIdentityTest.Menu menu = menu();
        ItemStack carried = new ItemStack(Items.APPLE, 5);
        menu.setCarried(carried);
        AtomicInteger closes = new AtomicInteger();
        lombok.var session = session(menu, closes);
        session.state(1, 2, true, true);
        lombok.var regions = session.regions(30, 10, 176, 222);
        lombok.var old = new View(regions);
        session.bind(old);
        lombok.var next = new View(regions);
        session.bind(next);
        old.activate("close");
        assertEquals(0, closes.get());
        lombok.var close = regions.get("close");
        assertTrue(session.click(close.x() + 1, close.y() + 1, 0));
        assertTrue(session.release(0));
        next.activate("close");
        assertEquals(1, closes.get());
        assertSame(carried, menu.getCarried());
        session.close(UiCloseReason.USER_CLOSE);
        assertEquals(0, next.subscriptionCount());
    }

    @Test
    public void templatesReuseTheSharedEditorAndRealLocalIcons() throws Exception {
        Path root = java.nio.file.Paths.get("src/main/resources/assets/apricityui/apricity/aotake_sweep");
        String player = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/java/xin/vanilla/aotake/screen/PlayerConfigScreen.java")),  StandardCharsets.UTF_8);
        assertTrue("PlayerEntity settings must use the existing shared editor route", player.contains("preferences.shared().spec(this, uiSurfaceId())"));
        assertFalse("Do not ship a second player configuration template", Files.exists(root.resolve("player.html")));
        String dustbin = new String(java.nio.file.Files.readAllBytes(root.resolve("dustbin.html")),  StandardCharsets.UTF_8);
        for (String id : new String[]{"brand", "page_label", "close"})
            assertTrue(dustbin.contains("id=\"" + id + "\""));
        assertTrue(dustbin.contains("aui-mouse-events\" content=\"none"));
        for (String name : new String[]{"clear_cache", "clear_all", "clear_page", "refresh", "previous", "next"}) {
            byte[] bytes = Files.readAllBytes(root.resolve("icons/" + name + ".png"));
            assertEquals(137, bytes[0] & 255);
            assertEquals(32, java.nio.ByteBuffer.wrap(bytes, 16, 4).getInt());
        }
    }

    @Test
    public void unknownPagesKeepRefreshAndChunkVaultNeverShowsDestructiveTools() {
        for (boolean vault : new boolean[]{false, true}) {
            lombok.var session = new DustbinUiSession(menu(), vault, () -> true, action -> {
            });
            session.state(-1, -1, true, true);
            assertTrue(session.allowed(DustbinUiSession.Action.REFRESH));
            assertFalse(session.allowed(DustbinUiSession.Action.NEXT));
            lombok.var regions = session.regions(30, 10, 176, 222);
            assertEquals(!vault, regions.containsKey("clear_cache"));
            assertEquals(!vault, regions.containsKey("clear_all"));
        }
    }

    @Test
    public void containerRendererAcceptsTheSharedRetainedGameElements() throws Exception {
        lombok.var node = new org.objectweb.asm.tree.ClassNode();
        try (java.io.InputStream input = getClass().getClassLoader().getResourceAsStream("xin/vanilla/banira/internal/client/ui/AuiContainerDocument.class")) {
            assertNotNull(input);
            new org.objectweb.asm.ClassReader(input).accept(node, 0);
        }
        boolean supported = false, rendered = false;
        int regionUpdates = 0;
        for (lombok.var method : node.methods)
            if (method.name.equals("changed")) for (lombok.var instruction : method.instructions)
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode  && ((org.objectweb.asm.tree.MethodInsnNode) instruction).name.equals("gameElement"))
                    supported = true;
        assertTrue("Container icons must use the existing retained game-element renderer", supported);
        for (lombok.var method : node.methods)
            if (method.name.equals("render")) for (lombok.var instruction : method.instructions)
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode ) {
                    if (((org.objectweb.asm.tree.MethodInsnNode) instruction).owner.endsWith("/AuiDocumentAccess") && ((org.objectweb.asm.tree.MethodInsnNode) instruction).name.equals("render")) rendered = true;
                    if (((org.objectweb.asm.tree.MethodInsnNode) instruction).owner.endsWith("/UiDomAccess$Node") && ((org.objectweb.asm.tree.MethodInsnNode) instruction).name.equals("gameElement")) regionUpdates++;
                }
        assertTrue("Retained icons must be painted after the container document", rendered);
        assertEquals("Hidden icons must be cleared and restored by the current region list", 2, regionUpdates);
    }

    @Test
    public void permissionsAndCarriedStateUpdateRealSpritesWithoutChangingSlots() {
        DustbinUiContainerIdentityTest.Menu menu = menu();
        Object[] refs = menu.slots.toArray();
        lombok.var session = new DustbinUiSession(menu, false, () -> true, action -> {
        }, () -> {}, menu::getCarried);
        session.state(1, 3, true, true);
        lombok.var view = new View(session.regions(30, 10, 176, 222));
        session.bind(view);
        assertTrue(view.element("refresh_icon").option().texture()[0].location().getPath().endsWith("refresh.png"));
        menu.setCarried(new ItemStack(Items.APPLE));
        session.state(1, 3, true, true);
        assertTrue(view.element("refresh_icon").option().texture()[0].location().getPath().endsWith("refresh_disabled.png"));
        session.state(1, 3, false, false);
        assertFalse(session.regions(30, 10, 176, 222).containsKey("clear_cache_icon"));
        menu.setCarried(ItemStack.EMPTY);
        session.state(1, 3, true, true);
        assertTrue(session.regions(30, 10, 176, 222).containsKey("clear_cache_icon"));
        assertTrue(view.element("refresh_icon").option().texture()[0].location().getPath().endsWith("refresh.png"));
        assertArrayEquals(refs, menu.slots.toArray());
    }

    private static boolean overlaps(ScreenCoordinate a, ScreenCoordinate b) {
        return a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
                && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
    }
}
