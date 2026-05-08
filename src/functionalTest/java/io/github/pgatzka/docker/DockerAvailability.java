package io.github.pgatzka.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

public final class DockerAvailability {

  private DockerAvailability() {
  }

  public static DockerClient client() {
    var cfg = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
    var http = new ApacheDockerHttpClient.Builder()
        .dockerHost(cfg.getDockerHost()).sslConfig(cfg.getSSLConfig()).build();
    return DockerClientImpl.getInstance(cfg, http);
  }

  public static boolean available() {
    try (DockerClient c = client()) {
      c.pingCmd().exec();
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

}
