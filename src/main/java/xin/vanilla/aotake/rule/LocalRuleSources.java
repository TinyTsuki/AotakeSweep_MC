package xin.vanilla.aotake.rule;

import xin.vanilla.banira.api.script.ScriptSource;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

final class LocalRuleSources {
    private static final String DIRECTORY = "scripts";
    private static final String LEGACY_DIRECTORY = "rules/local";
    private static final String PACKAGE = "xin.vanilla.banira.generated.";
    private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,79}");

    private LocalRuleSources() {
    }

    static Map<String, byte[]> read(RuleFileStore store, int remainingCount, long remainingBytes) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        remainingBytes = readDirectory(store, DIRECTORY, files, remainingCount, remainingBytes);
        readDirectory(store, LEGACY_DIRECTORY, files, remainingCount, remainingBytes);
        return files;
    }

    private static long readDirectory(RuleFileStore store, String relative, Map<String, byte[]> files,
                                      int remainingCount, long remainingBytes) throws IOException {
        Path directory = store.path(relative);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return remainingBytes;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "*.java")) {
            for (Path path : entries) {
                String file = path.getFileName().toString();
                if (!file.endsWith(".java") || files.containsKey(file)) continue;
                if (files.size() >= remainingCount) throw new IOException("Too many local entity rule scripts");
                String className = file.substring(0, file.length() - 5);
                if (!CLASS_NAME.matcher(className).matches())
                    throw new IOException("Invalid local rule filename: " + file);
                byte[] bytes = store.read(relative + "/" + file, RuleFileStore.MAX_SOURCE_BYTES);
                remainingBytes -= bytes.length;
                if (remainingBytes < 0) throw new IOException("Rule catalog exceeds size limit");
                files.put(file, bytes);
            }
        }
        return remainingBytes;
    }

    static void add(Map<String, RuleSource> sources, Map<String, byte[]> files) throws IOException {
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String file = entry.getKey();
            String className = file.substring(0, file.length() - 5);
            String id = className.toLowerCase(Locale.ROOT);
            if (id.startsWith("builtin_") || id.startsWith("legacy_") || sources.containsKey(id))
                throw new IOException("Duplicate or reserved local rule ID: " + id);
            String java = RuleFileStore.utf8(entry.getValue());
            if (java.startsWith("\uFEFF")) java = java.substring(1);
            sources.put(id, new RuleSource(new ScriptSource(id, PACKAGE + className, file, java),
                    RuleBindingPlan.defaults(), RuleFileStore.hash(entry.getValue()), Collections.emptyList()));
        }
    }
}
