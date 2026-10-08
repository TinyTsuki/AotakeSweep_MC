package xin.vanilla.aotake.config;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class ConfigurationAccessorContractTest {
    @Test
    public void declarationsDoNotExposeRootBeanAccessors() {
        verifyDeclaration(CommonConfig.class);
        verifyDeclaration(ClientConfig.class);
    }

    @Test
    public void sourcesContainNoDisabledAccessors() throws Exception {
        for (String name : new String[]{"CommonConfig", "ClientConfig"}) {
            String source = new String(Files.readAllBytes(Paths.get(
                    "src/main/java/xin/vanilla/aotake/config/" + name + ".java")), StandardCharsets.UTF_8);
            assertFalse(name + " still contains redundant disabled accessors",
                    source.matches("(?s).*AccessLevel\\s*\\.\\s*NONE.*"));
        }
    }

    private static void verifyDeclaration(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            assertTrue("Configuration declaration exposes a detached accessor: " + method,
                    !Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers()));
        }

    }
}
