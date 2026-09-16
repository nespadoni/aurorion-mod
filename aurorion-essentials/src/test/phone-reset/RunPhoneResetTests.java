import java.io.IOException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Run from aurorion-mod: java aurorion-essentials/src/test/phone-reset/RunPhoneResetTests.java */
class RunPhoneResetTests {
    public static void main(String[] args) throws Exception {
        Path module = Path.of("aurorion-essentials");
        Path output = Files.createTempDirectory("aurorion-phone-tests-");
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var files = compiler.getStandardFileManager(null, null, null)) {
            List<JavaFileObject> sources = new ArrayList<>();
            files.getJavaFileObjectsFromPaths(List.of(
                    module.resolve("src/main/java/com/aurorion/essentials/compat/MattupolisPhoneCompat.java"),
                    module.resolve("src/test/java/com/aurorion/essentials/compat/MattupolisPhoneCompatDataTest.java")))
                    .forEach(sources::add);
            sources.add(source("net.minecraft.world.level.storage.LevelResource", """
                    package net.minecraft.world.level.storage;
                    public class LevelResource { public static final LevelResource ROOT = new LevelResource(); }
                    """));
            sources.add(source("net.minecraft.server.MinecraftServer", """
                    package net.minecraft.server;
                    import java.nio.file.Path;
                    import net.minecraft.world.level.storage.LevelResource;
                    public class MinecraftServer {
                        public Path getWorldPath(LevelResource resource) { throw new AssertionError("Minecraft must not run"); }
                    }
                    """));
            sources.add(source("net.minecraft.server.level.ServerPlayer", """
                    package net.minecraft.server.level;
                    import java.util.UUID;
                    import net.minecraft.server.MinecraftServer;
                    public class ServerPlayer {
                        public MinecraftServer getServer() { throw new AssertionError(); }
                        public UUID getUUID() { throw new AssertionError(); }
                        public Profile getGameProfile() { throw new AssertionError(); }
                        public record Profile(String name) { public String getName() { return name; } }
                    }
                    """));
            sources.add(source("com.aurorion.essentials.AurorionEssentials", """
                    package com.aurorion.essentials;
                    public class AurorionEssentials {
                        public static final Log LOGGER = new Log();
                        public static class Log { public void warn(String message, Object... args) {} }
                    }
                    """));
            sources.add(source("com.aurorion.core.character.CharacterResetEvent", """
                    package com.aurorion.core.character;
                    import java.util.UUID;
                    import net.minecraft.server.MinecraftServer;
                    public class CharacterResetEvent {
                        public MinecraftServer server() { throw new AssertionError("Minecraft must not run"); }
                        public UUID account() { throw new AssertionError(); }
                        public UUID previousCharacterId() { throw new AssertionError(); }
                        public Pending transaction() { throw new AssertionError(); }
                        public record Pending(String accountName) {}
                    }
                    """));
            if (!compiler.getTask(null, files, null,
                    List.of("--release", "21", "-d", output.toString()), null, sources).call()) {
                throw new AssertionError("Phone reset tests did not compile");
            }
            try (var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()})) {
                loader.loadClass("com.aurorion.essentials.compat.MattupolisPhoneCompatDataTest")
                        .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            }
        } finally {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static JavaFileObject source(String name, String code) {
        return new SimpleJavaFileObject(URI.create("string:///" + name.replace('.', '/') + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return code; }
        };
    }
}
