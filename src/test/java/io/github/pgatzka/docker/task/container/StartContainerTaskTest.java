package io.github.pgatzka.docker.task.container;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.Image;
import io.github.pgatzka.docker.dsl.PullPolicy;
import io.github.pgatzka.docker.dsl.WaitFor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.junit.jupiter.api.Test;

class StartContainerTaskTest {

    private static InspectContainerResponse mockRunningContainer() {
        InspectContainerResponse r = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState s = mock(InspectContainerResponse.ContainerState.class);
        when(r.getState()).thenReturn(s);
        when(s.getRunning()).thenReturn(true);
        when(s.getStatus()).thenReturn("running");
        return r;
    }

    @Test
    void failsWhenImageDeclaresNoHealthcheckAndStrategyIsHealthcheck() {
        DockerClient c = mock(DockerClient.class);

        // Pull skipped (IF_NOT_PRESENT, image present)
        ListImagesCmd listImg = mock(ListImagesCmd.class, RETURNS_SELF);
        Image img = mock(Image.class);
        when(c.listImagesCmd()).thenReturn(listImg);
        when(listImg.exec()).thenReturn(List.of(img));
        when(img.getRepoTags()).thenReturn(new String[] {"postgres:18-alpine"});

        // Image inspect: no healthcheck
        InspectImageCmd insImg = mock(InspectImageCmd.class);
        InspectImageResponse insImgResp = mock(InspectImageResponse.class);
        ContainerConfig cfg = mock(ContainerConfig.class);
        when(c.inspectImageCmd("postgres:18-alpine")).thenReturn(insImg);
        when(insImg.exec()).thenReturn(insImgResp);
        when(insImgResp.getConfig()).thenReturn(cfg);
        when(cfg.getHealthcheck()).thenReturn(null);

        StartContainerTask.Params p = new StartContainerTask.Params(
                "c",
                "postgres:18-alpine",
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                WaitFor.healthcheck(),
                Duration.ofSeconds(10),
                PullPolicy.IF_NOT_PRESENT);

        assertThatThrownBy(() -> StartContainerTask.run(c, p, mock(Logger.class)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("declares no HEALTHCHECK")
                .hasMessageContaining("postgres:18-alpine");
    }

    @Test
    void createsThenStartsWhenContainerAbsent() {
        DockerClient c = mock(DockerClient.class);

        // Image present
        ListImagesCmd listImg = mock(ListImagesCmd.class, RETURNS_SELF);
        Image img = mock(Image.class);
        when(c.listImagesCmd()).thenReturn(listImg);
        when(listImg.exec()).thenReturn(List.of(img));
        when(img.getRepoTags()).thenReturn(new String[] {"hello-world:latest"});

        // Image inspect: healthcheck not required because waitFor=none
        // (still fetched for logging — return a stub)
        InspectImageCmd insImg = mock(InspectImageCmd.class);
        InspectImageResponse insImgResp = mock(InspectImageResponse.class);
        when(c.inspectImageCmd("hello-world:latest")).thenReturn(insImg);
        when(insImg.exec()).thenReturn(insImgResp);

        // Container does not exist -> first inspect throws NotFound
        InspectContainerCmd insCnt = mock(InspectContainerCmd.class);
        when(c.inspectContainerCmd("c")).thenReturn(insCnt);
        when(insCnt.exec())
                .thenThrow(new NotFoundException("nope"))
                // After create+start: state running
                .thenReturn(mockRunningContainer());

        // Create
        CreateContainerCmd create = mock(CreateContainerCmd.class, RETURNS_SELF);
        CreateContainerResponse createResp = mock(CreateContainerResponse.class);
        when(c.createContainerCmd("hello-world:latest")).thenReturn(create);
        when(create.exec()).thenReturn(createResp);
        when(createResp.getId()).thenReturn("cid");

        // Start
        StartContainerCmd start = mock(StartContainerCmd.class);
        when(c.startContainerCmd("c")).thenReturn(start);

        StartContainerTask.Params p = new StartContainerTask.Params(
                "c",
                "hello-world:latest",
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                WaitFor.none(),
                Duration.ofSeconds(10),
                PullPolicy.IF_NOT_PRESENT);

        StartContainerTask.run(c, p, mock(Logger.class));

        verify(create).withName("c");
        verify(start).exec();
    }
}
