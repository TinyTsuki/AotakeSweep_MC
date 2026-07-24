package xin.vanilla.aotake.dependency;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** 开发期同版本 Banira 发布后必须立即刷新依赖。 */
public class BaniraDependencyRefreshContractTest {
    @Test
    public void baniraDependencyIsChangingWithoutCache() throws Exception {
        String build = new String(Files.readAllBytes(Paths.get("build.gradle")), StandardCharsets.UTF_8);

        assertTrue(build.contains("changing = true"));
        assertTrue(build.contains("cacheChangingModulesFor 0, 'seconds'"));
    }
}
