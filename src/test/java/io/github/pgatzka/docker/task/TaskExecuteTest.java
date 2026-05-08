package io.github.pgatzka.docker.task;

import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.command.CreateVolumeCmd;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.ListNetworksCmd;
import com.github.dockerjava.api.command.ListVolumesCmd;
import com.github.dockerjava.api.command.ListVolumesResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import io.github.pgatzka.docker.dsl.DockerExtension;
import io.github.pgatzka.docker.dsl.WaitFor;
import io.github.pgatzka.docker.service.DockerService;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

/**
 * Drives the {@code @TaskAction execute()} glue methods on every Docker task. The static
 * {@code run(...)} methods are covered elsewhere; these tests exist purely to take the thin
 * {@code execute()} wrappers off 0% coverage by invoking them through a real Gradle task with a
 * mocked {@link DockerService}.
 */
class TaskExecuteTest {

    private static Project newProject() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("io.github.pgatzka.docker");
        return project;
    }

    @SuppressWarnings("unchecked")
    private static void injectMockService(Task task, DockerService service) {
        // The @ServiceReference on DockerTask.getDockerService() expects a BuildService instance
        // to be wired from Gradle's shared service registry. ProjectBuilder doesn't populate that
        // registry automatically, but the property is still a normal Property<DockerService> we
        // can set ourselves to short-circuit the lookup.
        DockerTask docker = (DockerTask) task;
        docker.getDockerService().set(service);
    }

    private static DockerService mockServiceReturning(DockerClient client) {
        DockerService service = mock(DockerService.class);
        when(service.getClient()).thenReturn(client);
        return service;
    }

    private static void runActions(Task task) {
        task.getActions().forEach(action -> action.execute(task));
    }

    @Test
    void createVolumeTaskExecuteRuns() {
        Project project = newProject();
        DockerExtension ext = (DockerExtension) project.getExtensions().getByName("docker");
        ext.getVolumes().register("data");

        DockerClient client = mock(DockerClient.class);
        ListVolumesCmd listCmd = mock(ListVolumesCmd.class);
        ListVolumesResponse listResp = mock(ListVolumesResponse.class);
        CreateVolumeCmd createCmd = mock(CreateVolumeCmd.class, RETURNS_SELF);
        when(client.listVolumesCmd()).thenReturn(listCmd);
        when(listCmd.exec()).thenReturn(listResp);
        when(listResp.getVolumes()).thenReturn(List.of());
        when(client.createVolumeCmd()).thenReturn(createCmd);

        Task task = project.getTasks().getByName("createVolumeData");
        injectMockService(task, mockServiceReturning(client));

        runActions(task);

        verify(createCmd).exec();
    }

    @Test
    void removeVolumeTaskExecuteRunsNoOpPath() {
        Project project = newProject();
        DockerExtension ext = (DockerExtension) project.getExtensions().getByName("docker");
        ext.getVolumes().register("data");

        DockerClient client = mock(DockerClient.class);
        ListVolumesCmd listCmd = mock(ListVolumesCmd.class);
        ListVolumesResponse listResp = mock(ListVolumesResponse.class);
        when(client.listVolumesCmd()).thenReturn(listCmd);
        when(listCmd.exec()).thenReturn(listResp);
        when(listResp.getVolumes()).thenReturn(List.of());

        Task task = project.getTasks().getByName("removeVolumeData");
        injectMockService(task, mockServiceReturning(client));

        runActions(task);

        verify(client, never()).removeVolumeCmd(anyString());
    }

    @Test
    void createNetworkTaskExecuteRuns() {
        Project project = newProject();
        DockerExtension ext = (DockerExtension) project.getExtensions().getByName("docker");
        ext.getNetworks().register("backend");

        DockerClient client = mock(DockerClient.class);
        ListNetworksCmd listCmd = mock(ListNetworksCmd.class);
        when(client.listNetworksCmd()).thenReturn(listCmd);
        when(listCmd.exec()).thenReturn(List.of());

        CreateNetworkCmd createCmd = mock(CreateNetworkCmd.class, RETURNS_SELF);
        CreateNetworkResponse createResp = mock(CreateNetworkResponse.class);
        when(client.createNetworkCmd()).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(createResp);

        Task task = project.getTasks().getByName("createNetworkBackend");
        injectMockService(task, mockServiceReturning(client));

        runActions(task);

        verify(createCmd).exec();
    }

    @Test
    void removeNetworkTaskExecuteRunsNoOpPath() {
        Project project = newProject();
        DockerExtension ext = (DockerExtension) project.getExtensions().getByName("docker");
        ext.getNetworks().register("backend");

        DockerClient client = mock(DockerClient.class);
        ListNetworksCmd listCmd = mock(ListNetworksCmd.class);
        when(client.listNetworksCmd()).thenReturn(listCmd);
        when(listCmd.exec()).thenReturn(List.of());

        Task task = project.getTasks().getByName("removeNetworkBackend");
        injectMockService(task, mockServiceReturning(client));

        runActions(task);

        verify(client, never()).removeNetworkCmd(anyString());
    }

    @Test
    void removeContainerTaskExecuteRunsNoOpPath() {
        Project project = newProject();
        DockerExtension ext = (DockerExtension) project.getExtensions().getByName("docker");
        ext.getContainers().register("svc", c -> c.getImage().set("hello-world:latest"));

        DockerClient client = mock(DockerClient.class);
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        when(client.inspectContainerCmd("svc")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenThrow(new NotFoundException("absent"));

        Task task = project.getTasks().getByName("removeContainerSvc");
        injectMockService(task, mockServiceReturning(client));

        runActions(task);

        verify(client, never()).removeContainerCmd(anyString());
    }

    @Test
    void stopContainerTaskExecuteRunsNoOpPath() {
        Project project = newProject();
        DockerExtension ext = (DockerExtension) project.getExtensions().getByName("docker");
        ext.getContainers().register("svc", c -> c.getImage().set("hello-world:latest"));

        DockerClient client = mock(DockerClient.class);
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        when(client.inspectContainerCmd("svc")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenThrow(new NotFoundException("absent"));

        Task task = project.getTasks().getByName("stopSvc");
        injectMockService(task, mockServiceReturning(client));

        runActions(task);

        verify(client, never()).stopContainerCmd(anyString());
    }

    @Test
    void startContainerTaskExecuteRuns() {
        Project project = newProject();
        DockerExtension ext = (DockerExtension) project.getExtensions().getByName("docker");
        ext.getContainers().register("svc", c -> {
            c.getImage().set("hello-world:latest");
            c.getWaitFor().set(WaitFor.none());
        });

        DockerClient client = mock(DockerClient.class);

        // Image present → pull skipped (PullPolicy.IF_NOT_PRESENT default)
        ListImagesCmd listImg = mock(ListImagesCmd.class);
        com.github.dockerjava.api.model.Image img = mock(com.github.dockerjava.api.model.Image.class);
        when(client.listImagesCmd()).thenReturn(listImg);
        when(listImg.exec()).thenReturn(List.of(img));
        when(img.getRepoTags()).thenReturn(new String[] {"hello-world:latest"});

        // Container does not exist
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        when(client.inspectContainerCmd("svc")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenThrow(new NotFoundException("absent"));

        // Create container returns id
        CreateContainerCmd createCmd = mock(CreateContainerCmd.class, RETURNS_SELF);
        CreateContainerResponse createResp = mock(CreateContainerResponse.class);
        when(client.createContainerCmd("hello-world:latest")).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(createResp);
        when(createResp.getId()).thenReturn("cid");

        // Start succeeds
        StartContainerCmd startCmd = mock(StartContainerCmd.class);
        when(client.startContainerCmd("svc")).thenReturn(startCmd);

        Task task = project.getTasks().getByName("startSvc");
        injectMockService(task, mockServiceReturning(client));

        runActions(task);

        verify(client).startContainerCmd("svc");
    }
}
