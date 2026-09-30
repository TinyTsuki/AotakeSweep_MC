package xin.vanilla.aotake.rule;

import com.google.gson.*;
import xin.vanilla.banira.api.BaniraDataPaths;
import xin.vanilla.banira.common.util.JsonUtils;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Bounded local files beneath the owning mod's directory. No arbitrary paths from clients. */
public final class RuleFileStore {
    public static final int MAX_SOURCE_BYTES = 256 * 1024, MAX_CONFIG_BYTES = 8 * 1024 * 1024;
    static final int MAX_METADATA_BYTES = 1024 * 1024, MAX_SCRIPTS = 128;
    static final String MANIFEST = "rules/manifest.json", JOURNAL = "rules/migration.json";
    private final Path root;

    public RuleFileStore(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public static RuleFileStore local() { return new RuleFileStore(BaniraDataPaths.configPath().resolve("aotake_sweep")); }
    public Path root() { return root; }
    public RuleCatalog loadCatalog() throws IOException { return RuleCatalog.load(this); }

    Path path(String relative) throws IOException {
        if (relative == null || relative.isEmpty() || relative.indexOf('\\') >= 0 || relative.indexOf(':') >= 0
                || relative.startsWith("/") || Arrays.asList(relative.split("/", -1)).stream().anyMatch(s -> s.isEmpty() || s.equals(".") || s.equals(".."))) {
            throw new IllegalArgumentException("Invalid local rule path: " + relative);
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Path outside rule directory");
        checkAncestors(target);
        return target;
    }
    private static void checkAncestors(Path target) throws IOException {
        Path current = target.getRoot();
        for (Path part : target) {
            current = current.resolve(part);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) continue;
            BasicFileAttributes attributes = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther() || !current.toRealPath().equals(current.toAbsolutePath().normalize())) {
                throw new IOException("Linked rule paths are not allowed: " + current);
            }
        }
    }
    public byte[] read(String relative, int maximum) throws IOException {
        if (maximum < 0 || maximum > MAX_CONFIG_BYTES) throw new IllegalArgumentException("Invalid read limit");
        Path target = path(relative);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing rule file: " + relative);
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (channel.size() > maximum) throw new IOException("Rule file too large: " + relative);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (channel.read(buffer) != -1) {
                buffer.flip(); if (bytes.size() + buffer.remaining() > maximum) throw new IOException("Rule file grew beyond limit: " + relative);
                bytes.write(buffer.array(), 0, buffer.remaining()); buffer.clear();
            }
            return bytes.toByteArray();
        }
    }
    byte[] optional(String relative, int maximum) throws IOException {
        return Files.exists(path(relative), LinkOption.NOFOLLOW_LINKS) ? read(relative, maximum) : null;
    }
    public void writeNew(String relative, byte[] bytes) throws IOException {
        byte[] existing = optional(relative, MAX_CONFIG_BYTES);
        if (existing != null) {
            if (!Arrays.equals(existing, bytes)) throw new IOException("Existing rule file differs: " + relative);
            return;
        }
        write(relative, null, bytes, false);
    }
    public void replace(String relative, byte[] expected, byte[] bytes) throws IOException { write(relative, expected, bytes, true); }
    private void write(String relative, byte[] expected, byte[] bytes, boolean replace) throws IOException {
        if (bytes.length > MAX_CONFIG_BYTES) throw new IOException("Rule file exceeds write limit");
        Path target = path(relative);
        Files.createDirectories(target.getParent()); checkAncestors(target);
        Path temporary = Files.createTempFile(target.getParent(), ".rule-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            if (!Arrays.equals(expected, optional(relative, MAX_CONFIG_BYTES))) throw new IOException("Rule file changed: " + relative);
            checkAncestors(target);
            if (replace) Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, target); // No REPLACE_EXISTING: a concurrently created file must survive.
            if (!Arrays.equals(bytes, read(relative, MAX_CONFIG_BYTES))) throw new IOException("Rule write verification failed: " + relative);
        } finally { Files.deleteIfExists(temporary); }
    }
    static byte[] jsonBytes(JsonElement json) { return JsonUtils.PRETTY_GSON.toJson(json).getBytes(StandardCharsets.UTF_8); }
    static JsonObject object(byte[] bytes) throws IOException {
        try {
            JsonElement parsed = JsonUtils.GSON.fromJson(utf8(bytes), JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) throw new IllegalArgumentException("Expected JSON object");
            return parsed.getAsJsonObject();
        } catch (RuntimeException error) { throw new IOException("Invalid rule metadata", error); }
    }
    static String utf8(byte[] bytes) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    static String hash(byte[] bytes) {
        try {
            StringBuilder result = new StringBuilder(64);
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format(Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    static void requireVersion(JsonObject json) throws IOException {
        if (!json.has("schemaVersion") || json.get("schemaVersion").getAsInt() != 1
                || !json.has("contractVersion") || !RuleSource.CONTRACT_VERSION.equals(json.get("contractVersion").getAsString())) {
            throw new IOException("Unsupported rule metadata version");
        }
    }
    static JsonObject versioned() {
        JsonObject json = new JsonObject(); json.addProperty("schemaVersion", 1); json.addProperty("contractVersion", RuleSource.CONTRACT_VERSION); return json;
    }
}
