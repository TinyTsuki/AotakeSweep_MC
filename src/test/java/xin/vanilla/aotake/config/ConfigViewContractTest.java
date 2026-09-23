package xin.vanilla.aotake.config;

import org.junit.Test;
import xin.vanilla.aotake.enums.EnumDustbinClientUiStyle;
import xin.vanilla.aotake.enums.EnumProgressBarType;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * 锁定 Aotake 通过 Banira 配置视图暴露给业务层的基础契约。
 */
public class ConfigViewContractTest {

    @org.junit.BeforeClass
    public static void initializeRegistryDefaults() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    public void commaExpressionsRoundTripWithoutImplicitSaves() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(CommonConfig.class);
        fixture.bind(CommonConfig.class);
        CommonConfigView.BaseView.SweepView view = CommonConfigView.get().base().sweep();
        java.util.List<String> rules = Arrays.asList(
                "tick, clazz, itemClazz -> tick >= 5", "minecraft:arrow");
        view.entityList(rules);
        assertEquals(rules, view.entityList());
        assertEquals(2, view.entityList().size());
        view.entityList().clear();
        assertEquals(rules, view.entityList());
        assertEquals(0, fixture.saves);
        fixture.holder.save();
        assertEquals(1, fixture.saves);
    }

    @Test
    public void emptyCommandPrefixRemainsEmptyAndNullFallsBack() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(CommonConfig.class);
        fixture.bind(CommonConfig.class);
        CommonConfigView.CommandView view = CommonConfigView.get().command();
        fixture.values.put("command.commandPrefix", "");
        assertEquals("", view.commandPrefix());
        fixture.values.put("command.commandPrefix", null);
        assertEquals("aotake", view.commandPrefix());
    }

    private static final String DEFAULT_COMMAND_PREFIX = "aotake";

    @Test
    public void commonConfigViewFallsBackToDefaultsWithoutHolder() {
        ConfigBaselineFixture.bind(CommonConfig.class, null);
        CommonConfigView root = CommonConfigView.get();

        assertNull(root.handle());
        assertEquals(DEFAULT_COMMAND_PREFIX, root.command().commandPrefix());
        assertEquals("language", root.command().commandLanguage());
        assertEquals("opv", root.command().commandVirtualOp());
        assertEquals("dustbin", root.command().commandDustbinOpen());
        assertEquals("chunkvault", root.command().commandChunkVault());
        assertFalse(root.concise().conciseLanguage());
        assertEquals(4, root.permission().permissionVirtualOp());
        assertEquals(0, root.permission().permissionDustbinOpen());
        assertEquals(2, root.permission().permissionDustbinOpenOther());
    }

    @Test
    public void clientConfigViewFallsBackToDefaultsWithoutHolder() {
        ConfigBaselineFixture.bind(ClientConfig.class, null);
        ClientConfigView root = ClientConfigView.get();

        assertNull(root.handle());
        assertEquals(Arrays.asList(EnumProgressBarType.LEAF), root.progressBar().progressBarDisplayNormal());
        assertEquals(Arrays.asList(EnumProgressBarType.LEAF, EnumProgressBarType.POLE, EnumProgressBarType.TEXT),
                root.progressBar().progressBarDisplayHold());
        assertFalse(root.progressBar().progressBarKeyApplyMode());
        assertEquals("50%,29", root.progressBar().pole().progressBarPolePosition());
        assertEquals("50%,8", root.progressBar().text().progressBarTextPosition());
        assertSame(EnumDustbinClientUiStyle.TEXTURED, root.dustbin().dustbinUiStyle());
    }

    @Test
    public void configViewsWriteToStableBaniraPaths() throws Exception {
        ConfigBaselineFixture common = new ConfigBaselineFixture(CommonConfig.class);
        common.bind(CommonConfig.class);
        CommonConfigView.get().command().commandPrefix("sweep");
        CommonConfigView.get().permission().permissionVirtualOp(3);
        ConfigBaselineFixture client = new ConfigBaselineFixture(ClientConfig.class);
        client.bind(ClientConfig.class);
        ClientConfigView.get().progressBar().progressBarKeyApplyMode(true);
        ClientConfigView.get().dustbin().dustbinUiStyle(EnumDustbinClientUiStyle.BANIRA_THEME);

        assertEquals("sweep", common.values.get("command.commandPrefix"));
        assertEquals(3, common.values.get("permission.permissionVirtualOp"));
        assertEquals(true, client.values.get("progressBar.progressBarKeyApplyMode"));
        assertSame(EnumDustbinClientUiStyle.BANIRA_THEME, client.values.get("dustbin.dustbinUiStyle"));
    }
}
