package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.jar.JarFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.support.PostgresTestcontainersSupport;

/**
 * Proves the repackaged {@code -exec} jar is a complete deployable: it serves the API under
 * {@code /api} and serves the Angular build at {@code /} exactly when that build was packaged into
 * it ({@code BOOT-INF/classes/static/index.html}); otherwise {@code /} is a 404, never a half-served UI.
 *
 * <p>The jar is {@code -Dsequeless.it.jar=...} or, by default, the single {@code target/*-exec.jar}.
 * {@code -Dsequeless.it.live=false} skips the live HTTP test (the documented fallback when spawning a
 * JVM is not possible); the {@link JarFile} assertion then still runs, and
 * {@code SpaFallbackWithAssetsTest} remains the live proof of the fallback itself.
 */
class FatJarServesUiAndApiIT extends PostgresTestcontainersSupport {

    private static final String UI_ENTRY = "BOOT-INF/classes/static/index.html";
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

    private Process process;

    @AfterEach
    void stopJar() throws InterruptedException {
        if (process != null) {
            process.destroy();
            if (!process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
    }

    @Test
    void jarServesApiAndConditionallyUi() throws Exception {
        assumeTrue(
                !"false".equals(System.getProperty("sequeless.it.live")),
                "sequeless.it.live=false: live HTTP proof skipped");
        Path jar = execJar();
        int port = freePort();
        process =
                new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                "-jar",
                                jar.toString(),
                                "--server.port=" + port,
                                "--sequeless.persistence.url=" + POSTGRES.getJdbcUrl(),
                                "--sequeless.persistence.username=" + POSTGRES.getUsername(),
                                "--sequeless.persistence.password=" + POSTGRES.getPassword())
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();

        HttpClient client = HttpClient.newHttpClient();
        String base = "http://localhost:" + port;
        awaitHealthy(client, base);

        assertThat(get(client, base + "/api/whoami").statusCode()).isEqualTo(200);

        HttpResponse<String> root = get(client, base + "/");
        if (hasUi(jar)) {
            assertThat(root.statusCode()).isEqualTo(200);
            assertThat(root.headers().firstValue("Content-Type").orElse("")).startsWith("text/html");
        } else {
            assertThat(root.statusCode()).isEqualTo(404);
        }
    }

    @Test
    void jarContentMatchesUiPresence() throws IOException {
        Path jar = execJar();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            boolean hasIndex = jarFile.getEntry(UI_ENTRY) != null;
            boolean hasAssets =
                    jarFile.stream().anyMatch(e -> e.getName().startsWith("BOOT-INF/classes/static/") && !e.isDirectory());
            assertThat(hasAssets).as("static assets are packaged iff index.html is").isEqualTo(hasIndex);
        }
    }

    private static boolean hasUi(Path jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            return jarFile.getEntry(UI_ENTRY) != null;
        }
    }

    private static Path execJar() throws IOException {
        String configured = System.getProperty("sequeless.it.jar");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(Path.of("target"), "*-exec.jar")) {
            for (Path jar : jars) {
                return jar;
            }
        }
        throw new IllegalStateException("No target/*-exec.jar; set -Dsequeless.it.jar");
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void awaitHealthy(HttpClient client, String base) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            assertThat(process.isAlive()).as("jar process exited during startup").isTrue();
            try {
                HttpResponse<String> health = get(client, base + "/actuator/health");
                if (health.statusCode() == 200 && health.body().contains("\"UP\"")) {
                    return;
                }
            } catch (IOException notUpYet) {
                // connection refused until the server binds
            }
            Thread.sleep(500);
        }
        throw new AssertionError("/actuator/health not UP within " + STARTUP_TIMEOUT);
    }
}
