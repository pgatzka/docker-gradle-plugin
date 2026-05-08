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

    static String precheck(DockerClient c) {
        try {
            c.pingCmd().exec();
            Version v = c.versionCmd().exec();
            return v.getVersion();
        } catch (RuntimeException ex) {
            throw new GradleException("Docker daemon unreachable: " + ex.getMessage() + ". Is Docker running?", ex);
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
                var cfg = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
                DockerHttpClient http = new ApacheDockerHttpClient.Builder()
                        .dockerHost(cfg.getDockerHost())
                        .sslConfig(cfg.getSSLConfig())
                        .build();
                client = DockerClientImpl.getInstance(cfg, http);
                LOG.info("Connecting to Docker daemon at {}", cfg.getDockerHost());
            }
            return client;
        }
    }

    private void ensurePrechecked(DockerClient c) {
        if (prechecked) {
            return;
        }
        // Serialize the precheck without blocking subsequent getClient() callers on
        // the daemon round-trip; the volatile flag short-circuits cheap reads.
        synchronized (precheckLock) {
            if (prechecked) {
                return;
            }
            String version = precheck(c);
            LOG.info("Daemon reachable: {}", version);
            prechecked = true;
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (clientLock) {
            DockerClient c = client;
            // Reset state up front so a failing close() doesn't leave a stale client around.
            client = null;
            prechecked = false;
            if (c != null) {
                c.close();
            }
        }
    }
}
