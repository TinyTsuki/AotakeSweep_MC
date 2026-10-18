package xin.vanilla.aotake.client.ui;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.internal.client.ui.NativeUiView;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class DustbinUiContainerIdentityTest {
    @BeforeClass public static void bootstrap() { DustbinUiSessionTest.bootstrap(); }
    private ChestMenu menu() { return ChestMenu.sixRows(19, new Inventory(null), new SimpleContainer(54)); }
    private static final class View extends NativeUiView {
        Map<String, ScreenCoordinate> regions;
        View(Map<String, ScreenCoordinate> regions) { super(null); this.regions = regions; }
        @Override public ScreenCoordinate bounds(String id) { return regions.get(id); }
    }

    @Test public void resizingSwitchingAndClosingLeaveCarriedStackAndEverySlotIntact() {
        ChestMenu menu = menu(); Object[] slots = menu.slots.toArray();
        menu.getSlot(0).set(new ItemStack(Items.APPLE, 7));
        ItemStack carried = new ItemStack(Items.DIAMOND, 3); menu.setCarried(carried);
        DustbinUiSession session = new DustbinUiSession(menu, false, () -> true, action -> fail("Toolbar stole carried input"));
        session.state(1, 3, true, true);
        for (int offset : new int[]{0, 31, 72}) {
            Map<String, ScreenCoordinate> geometry = session.regions(offset + 30, 10, 176, 222);
            View view = new View(geometry); session.bind(view);
            ScreenCoordinate button = geometry.get("refresh");
            assertTrue("Disabled toolbar must not forward a carried-stack click outside the menu", session.click(button.x() + 2, button.y() + 2, 0));
            view.activate("refresh");
            session.detach(UiDetachReason.SWITCH);
            assertTrue(session.release(0));
            assertFalse(session.release(0));
            assertEquals(0, view.subscriptionCount());
            assertArrayEquals(slots, menu.slots.toArray());
            assertSame(carried, menu.getCarried());
        }
        session.close(UiCloseReason.USER_CLOSE);
        assertArrayEquals(slots, menu.slots.toArray());
        assertSame(carried, menu.getCarried());
        assertEquals(7, menu.getSlot(0).getItem().getCount());
    }

    @Test public void dustbinAndChunkVaultPermissionsPagingAndClicksRemainSingle() {
        for (boolean vault : new boolean[]{false, true}) {
            ChestMenu menu = menu(); List<DustbinUiSession.Action> calls = new ArrayList<>();
            DustbinUiSession session = new DustbinUiSession(menu, vault, () -> true, calls::add);
            session.state(1, 3, true, true);
            Map<String, ScreenCoordinate> geometry = session.regions(30, 10, 176, 222);
            assertSame(geometry, session.regions(30, 10, 176, 222));
            View old = new View(geometry); session.bind(old);
            View next = new View(geometry); session.bind(next);
            old.activate("refresh"); assertTrue(calls.isEmpty());
            ScreenCoordinate refresh = geometry.get("refresh");
            assertTrue(session.click(refresh.x() + 3, refresh.y() + 3, 0));
            assertTrue(session.release(0));
            assertEquals(Collections.singletonList(DustbinUiSession.Action.REFRESH), calls);
            assertTrue(session.click(refresh.x(), refresh.y(), 1));
            assertTrue(session.release(1));
            ScreenCoordinate previous = geometry.get("previous");
            assertTrue(session.click(previous.x() + 2, previous.y() + 2, 0));
            session.detach(UiDetachReason.RELOAD);
            assertTrue(session.release(0));
            assertFalse(session.release(0));
            assertFalse(session.click(30 + menu.getSlot(0).x + 3, 10 + menu.getSlot(0).y + 3, 0));
            assertEquals(!vault, session.allowed(DustbinUiSession.Action.CLEAR_CACHE));
            assertEquals(!vault, session.allowed(DustbinUiSession.Action.CLEAR_ALL));
            session.state(0, 1, false, false);
            assertFalse(session.perform(DustbinUiSession.Action.PREVIOUS));
            assertFalse(session.perform(DustbinUiSession.Action.NEXT));
            assertFalse(session.perform(DustbinUiSession.Action.CLEAR_PAGE));
            assertEquals(1, calls.size());
            session.close(UiCloseReason.USER_CLOSE);
            next.activate("refresh"); assertEquals(1, calls.size());
        }
    }

    @Test public void replacementMenuOrSlotAndLostOwnershipRejectTheWholeBinding() {
        ChestMenu menu = menu(); AtomicBoolean owner = new AtomicBoolean(true);
        DustbinUiSession session = new DustbinUiSession(menu, false, owner::get, action -> fail("Stale menu action"));
        session.state(1, 3, true, true);
        assertFalse(session.matches(menu()));
        Slot original = menu.slots.get(0);
        menu.slots.set(0, new Slot(new SimpleContainer(1), 0, original.x, original.y));
        assertFalse(session.matches(menu)); assertTrue(session.regions(30, 0, 176, 222).isEmpty());
        assertFalse(session.perform(DustbinUiSession.Action.REFRESH));
        menu.slots.set(0, original); owner.set(false);
        assertFalse(session.matches(menu)); assertFalse(session.perform(DustbinUiSession.Action.REFRESH));
    }

    @Test public void nativeQuickMoveKeepsOriginalMenuTransferAndCount() {
        ChestMenu menu = menu();
        menu.getSlot(0).set(new ItemStack(Items.APPLE, 17));
        DustbinUiSession session = new DustbinUiSession(menu, false, () -> true, action -> fail());
        Object[] slots = menu.slots.toArray();
        session.bind(new View(session.regions(30, 10, 176, 222)));
        menu.quickMoveStack(null, 0);
        assertTrue(menu.getSlot(0).getItem().isEmpty());
        assertEquals(17, menu.slots.stream().skip(54).mapToInt(slot -> slot.getItem().getCount()).sum());
        assertArrayEquals(slots, menu.slots.toArray());
        assertEquals(19, menu.containerId);
    }
}
