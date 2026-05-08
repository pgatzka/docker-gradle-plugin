package io.github.pgatzka.docker.task;

import io.github.pgatzka.docker.service.DockerService;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "Docker daemon side effects must always run; tasks have no inputs/outputs to track")
public abstract class DockerTask extends DefaultTask {

    public DockerTask() {
        setGroup("docker");
    }

    @ServiceReference("io.github.pgatzka.docker.DockerService")
    public abstract Property<DockerService> getDockerService();
}
