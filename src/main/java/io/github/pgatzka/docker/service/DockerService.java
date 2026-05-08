package io.github.pgatzka.docker.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Version;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import java.io.IOException;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

public abstract class DockerService implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    private static final Logger LOG = Logging.getLogger(DockerService.class);

    private final Object clientLock = new Object();
    private final Object precheckLock = new Object();

    private DockerClient client;
    private volatile boolean prechecked;

    static String precheck(DockerClient client) {
        try {
            client.pingCmd().exec();
            Version version = client.versionCmd().exec();
            return version.getVersion();
        } catch (RuntimeException unreachable) {
            throw new GradleException(
                    "Docker daemon unreachable: " + unreachable.getMessage() + ". Is Docker running?", unreachable);
        }
    }

    public DockerClient getClient() {
        DockerClient c = ensureClient();
        ensurePrechecked(c);
        return c;
    }

    private DockerClient ensureClient() {
        // Holding `clientLock` only across cheap construction — never the daemon ping.
        synchronized (clientLock) {
            if (client == null) {
                var config =
                        DefaultDockerClientConfig.createDefaultConfigBuilder().build();
                DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                        .dockerHost(config.getDockerHost())
                        .sslConfig(config.getSSLConfig())
                        .build();
                client = DockerClientImpl.getInstance(config, httpClient);
                LOG.info("Connecting to Docker daemon at {}", config.getDockerHost());
            }
            return client;
        }
    }

    private void ensurePrechecked(DockerClient candidate) {
        if (prechecked) {
            return;
        }
        // Serialize the precheck without blocking subsequent getClient() callers on
        // the daemon round-trip; the volatile flag short-circuits cheap reads.
        synchronized (precheckLock) {
            if (prechecked) {
                return;
            }
            String version = precheck(candidate);
            LOG.info("Daemon reachable: {}", version);
            prechecked = true;
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (clientLock) {
            DockerClient toClose = client;
            // Reset state up front so a failing close() doesn't leave a stale client around.
            client = null;
            prechecked = false;
            if (toClose != null) {
                toClose.close();
            }
        }
    }
}
