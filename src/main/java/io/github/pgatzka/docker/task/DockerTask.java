package io.github.pgatzka.docker.task;

import io.github.pgatzka.docker.service.DockerService;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Docker daemon side effects must always run")
public abstract class DockerTask extends DefaultTask {

    public DockerTask() {
        setGroup("docker");
        getOutputs().upToDateWhen(t -> false);
    }

    @ServiceReference("docker")
    public abstract Property<DockerService> getDockerService();
}
