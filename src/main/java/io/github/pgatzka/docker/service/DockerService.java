package io.github.pgatzka.docker.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Version;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

public abstract class DockerService implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    private static final Logger LOG = Logging.getLogger(DockerService.class);

    private DockerClient client;

    private boolean prechecked;

    static String precheck(DockerClient c) {
        try {
            c.pingCmd().exec();
            Version v = c.versionCmd().exec();
            return v.getVersion();
        } catch (RuntimeException ex) {
            throw new GradleException("Docker daemon unreachable: " + ex.getMessage() + ". Is Docker running?", ex);
        }
    }

    public synchronized DockerClient getClient() {
        if (client == null) {
            var cfg = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            DockerHttpClient http = new ApacheDockerHttpClient.Builder()
                    .dockerHost(cfg.getDockerHost())
                    .sslConfig(cfg.getSSLConfig())
                    .build();
            client = DockerClientImpl.getInstance(cfg, http);
            LOG.info("Connecting to Docker daemon at {}", cfg.getDockerHost());
        }
        if (!prechecked) {
            String version = precheck(client);
            LOG.info("Daemon reachable: {}", version);
            prechecked = true;
        }
        return client;
    }

    @Override
    public synchronized void close() throws Exception {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
