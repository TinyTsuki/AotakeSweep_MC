package xin.vanilla.aotake.command;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommandRefreshContractTest {

    @Test
    public void refreshesOwnedRootsAfterCommandConfigSave() throws Exception {
        String bootstrap = source("src/main/java/xin/vanilla/aotake/AotakeSweep.java");
        String command = source("src/main/java/xin/vanilla/aotake/command/AotakeCommand.java");

        assertTrue(bootstrap.contains("BaniraConfigs.onSaved(CommonConfig.class"));
        assertTrue(bootstrap.contains("path.startsWith(\"command.\") || path.startsWith(\"concise.\")"));
        assertTrue(command.contains("BrigadierCommandTree.removeRoot(dispatcher"));
        assertTrue(command.contains("OWNED_ROOTS.put(rootName, dispatcher.register(builder))"));
        assertTrue(command.contains("server.getCommands().sendCommands(player)"));
    }

    @Test
    public void chunkVaultUsesSingleAuthorizedOpenCommand() throws Exception {
        String source = source("src/main/java/xin/vanilla/aotake/command/impl/ChunkVaultCommand.java");

        assertTrue(source.contains("Commands.literal(\"open\")"));
        assertFalse(source.contains("Commands.literal(\"view\")"));
        assertTrue(source.contains("if (!canOpen(player, vaultId))"));
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
