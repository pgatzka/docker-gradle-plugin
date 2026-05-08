package io.github.pgatzka.docker.task.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HealthCheck;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import io.github.pgatzka.docker.dsl.Mounts;
import io.github.pgatzka.docker.dsl.PullPolicy;
import io.github.pgatzka.docker.dsl.WaitFor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StartContainerTaskTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(50);

    // -------- helpers --------

    private static InspectContainerResponse mockRunningContainer() {
        InspectContainerResponse r = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState s = mock(InspectContainerResponse.ContainerState.class);
        when(r.getState()).thenReturn(s);
        when(s.getRunning()).thenReturn(true);
        when(s.getStatus()).thenReturn("running");
        return r;
    }

    private static InspectContainerResponse mockContainer(String status, Boolean running) {
        InspectContainerResponse r = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState s = mock(InspectContainerResponse.ContainerState.class);
        when(r.getState()).thenReturn(s);
        when(s.getRunning()).thenReturn(running);
        when(s.getStatus()).thenReturn(status);
        return r;
    }

    private static InspectContainerResponse mockContainerNullState() {
        InspectContainerResponse r = mock(InspectContainerResponse.class);
        when(r.getState()).thenReturn(null);
        return r;
    }

    private static void stubImagePresent(DockerClient c, String image) {
        ListImagesCmd listImg = mock(ListImagesCmd.class, RETURNS_SELF);
        Image img = mock(Image.class);
        when(c.listImagesCmd()).thenReturn(listImg);
        when(listImg.exec()).thenReturn(List.of(img));
        when(img.getRepoTags()).thenReturn(new String[] {image});
    }

    private static void stubImageAbsent(DockerClient c) {
        ListImagesCmd listImg = mock(ListImagesCmd.class, RETURNS_SELF);
        when(c.listImagesCmd()).thenReturn(listImg);
        when(listImg.exec()).thenReturn(List.of());
    }

    /** Stub inspectImageCmd to return a config-less response (used when waitFor != Healthcheck). */
    private static void stubInspectImageStub(DockerClient c, String image) {
        InspectImageCmd insImg = mock(InspectImageCmd.class);
        InspectImageResponse insImgResp = mock(InspectImageResponse.class);
        when(c.inspectImageCmd(image)).thenReturn(insImg);
        when(insImg.exec()).thenReturn(insImgResp);
    }

    /** Container does not exist on first inspect; running on subsequent inspects. */
    private static InspectContainerCmd stubContainerAbsentThenRunning(DockerClient c, String name) {
        InspectContainerCmd insCnt = mock(InspectContainerCmd.class);
        when(c.inspectContainerCmd(name)).thenReturn(insCnt);
        InspectContainerResponse running = mockRunningContainer();
        when(insCnt.exec()).thenThrow(new NotFoundException("nope")).thenReturn(running);
        return insCnt;
    }

    /** Existing container with the given second-inspect state. */
    private static InspectContainerCmd stubExistingContainer(
            DockerClient c, String name, InspectContainerResponse secondInspect) {
        InspectContainerCmd insCnt = mock(InspectContainerCmd.class);
        when(c.inspectContainerCmd(name)).thenReturn(insCnt);
        InspectContainerResponse first = mock(InspectContainerResponse.class);
        when(first.getId()).thenReturn("existing-cid");
        when(insCnt.exec()).thenReturn(first, secondInspect);
        return insCnt;
    }

    private static CreateContainerCmd stubCreate(DockerClient c, String image, String createdId) {
        CreateContainerCmd create = mock(CreateContainerCmd.class, RETURNS_SELF);
        CreateContainerResponse createResp = mock(CreateContainerResponse.class);
        when(c.createContainerCmd(image)).thenReturn(create);
        when(create.exec()).thenReturn(createResp);
        when(createResp.getId()).thenReturn(createdId);
        return create;
    }

    private static StartContainerCmd stubStart(DockerClient c, String name) {
        StartContainerCmd start = mock(StartContainerCmd.class);
        when(c.startContainerCmd(name)).thenReturn(start);
        return start;
    }

    private static StartContainerTask.Params params(
            String image, WaitFor waitFor, PullPolicy pullPolicy, Map<Integer, Integer> ports) {
        return new StartContainerTask.Params(
                "c",
                image,
                Map.of(),
                ports,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                waitFor,
                SHORT_TIMEOUT,
                pullPolicy);
    }

    private static StartContainerTask.Params paramsFull(
            Map<String, String> env,
            Map<Integer, Integer> ports,
            List<String> networks,
            List<String> command,
            List<Mounts.VolumeMount> volumeMounts,
            List<Mounts.BindMount> bindMounts,
            WaitFor waitFor,
            PullPolicy pullPolicy) {
        return new StartContainerTask.Params(
                "c",
                "img:1",
                env,
                ports,
                networks,
                command,
                volumeMounts,
                bindMounts,
                waitFor,
                SHORT_TIMEOUT,
                pullPolicy);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> anyList() {
        return any(List.class);
    }

    // ============================================================
    // Original tests (preserved)
    // ============================================================

    @Test
    void failsWhenImageDeclaresNoHealthcheckAndStrategyIsHealthcheck() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "postgres:18-alpine");

        InspectImageCmd insImg = mock(InspectImageCmd.class);
        InspectImageResponse insImgResp = mock(InspectImageResponse.class);
        ContainerConfig cfg = mock(ContainerConfig.class);
        when(c.inspectImageCmd("postgres:18-alpine")).thenReturn(insImg);
        when(insImg.exec()).thenReturn(insImgResp);
        when(insImgResp.getConfig()).thenReturn(cfg);
        when(cfg.getHealthcheck()).thenReturn(null);

        StartContainerTask.Params p =
                params("postgres:18-alpine", WaitFor.healthcheck(), PullPolicy.IF_NOT_PRESENT, Map.of());

        assertThatThrownBy(() -> StartContainerTask.run(c, p, mock(Logger.class)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("declares no HEALTHCHECK")
                .hasMessageContaining("postgres:18-alpine");
    }

    @Test
    void createsThenStartsWhenContainerAbsent() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "hello-world:latest");
        stubInspectImageStub(c, "hello-world:latest");
        stubContainerAbsentThenRunning(c, "c");
        CreateContainerCmd create = stubCreate(c, "hello-world:latest", "cid");
        StartContainerCmd start = stubStart(c, "c");

        StartContainerTask.Params p = params("hello-world:latest", WaitFor.none(), PullPolicy.IF_NOT_PRESENT, Map.of());

        StartContainerTask.run(c, p, mock(Logger.class));

        verify(create).withName("c");
        verify(start).exec();
    }

    // ============================================================
    // pullIfNeeded
    // ============================================================

    @Test
    void pullPolicyAlwaysTriggersPullEvenWhenImagePresent() throws InterruptedException {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");

        PullImageCmd pullCmd = mock(PullImageCmd.class);
        when(c.pullImageCmd("img:1")).thenReturn(pullCmd);
        PullImageResultCallback cb = mock(PullImageResultCallback.class);
        when(pullCmd.exec(any(PullImageResultCallback.class))).thenReturn(cb);
        when(cb.awaitCompletion(anyLong(), any(TimeUnit.class))).thenReturn(true);

        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");
        stubCreate(c, "img:1", "cid");
        stubStart(c, "c");

        StartContainerTask.Params p = params("img:1", WaitFor.none(), PullPolicy.ALWAYS, Map.of());

        StartContainerTask.run(c, p, mock(Logger.class));

        verify(c).pullImageCmd("img:1");
        verify(pullCmd).exec(any(PullImageResultCallback.class));
    }

    @Test
    void pullPolicyNeverSkipsPullEvenWhenImageAbsent() {
        DockerClient c = mock(DockerClient.class);

        stubImageAbsent(c);
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");
        stubCreate(c, "img:1", "cid");
        stubStart(c, "c");

        StartContainerTask.Params p = params("img:1", WaitFor.none(), PullPolicy.NEVER, Map.of());

        StartContainerTask.run(c, p, mock(Logger.class));

        verify(c, never()).pullImageCmd(anyString());
    }

    @Test
    void pullPolicyIfNotPresentSkipsWhenImagePresent() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");
        stubCreate(c, "img:1", "cid");
        stubStart(c, "c");

        StartContainerTask.Params p = params("img:1", WaitFor.none(), PullPolicy.IF_NOT_PRESENT, Map.of());

        StartContainerTask.run(c, p, mock(Logger.class));

        verify(c, never()).pullImageCmd(anyString());
    }

    @Test
    void pullThrowsWhenAwaitCompletionTimesOut() throws InterruptedException {
        DockerClient c = mock(DockerClient.class);

        stubImageAbsent(c);
        PullImageCmd pullCmd = mock(PullImageCmd.class);
        when(c.pullImageCmd("img:1")).thenReturn(pullCmd);
        PullImageResultCallback cb = mock(PullImageResultCallback.class);
        when(pullCmd.exec(any(PullImageResultCallback.class))).thenReturn(cb);
        when(cb.awaitCompletion(anyLong(), any(TimeUnit.class))).thenReturn(false);

        StartContainerTask.Params p = params("img:1", WaitFor.none(), PullPolicy.IF_NOT_PRESENT, Map.of());

        assertThatThrownBy(() -> StartContainerTask.run(c, p, mock(Logger.class)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("did not complete within");
    }

    @Test
    void pullInterruptedRestoresInterruptFlagAndThrows() throws InterruptedException {
        DockerClient c = mock(DockerClient.class);

        stubImageAbsent(c);
        PullImageCmd pullCmd = mock(PullImageCmd.class);
        when(c.pullImageCmd("img:1")).thenReturn(pullCmd);
        PullImageResultCallback cb = mock(PullImageResultCallback.class);
        when(pullCmd.exec(any(PullImageResultCallback.class))).thenReturn(cb);
        when(cb.awaitCompletion(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("boom"));

        StartContainerTask.Params p = params("img:1", WaitFor.none(), PullPolicy.IF_NOT_PRESENT, Map.of());

        try {
            assertThatThrownBy(() -> StartContainerTask.run(c, p, mock(Logger.class)))
                    .isInstanceOf(GradleException.class)
                    .hasMessageContaining("Interrupted while pulling");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            //noinspection ResultOfMethodCallIgnored
            Thread.interrupted();
        }
    }

    // ============================================================
    // validateImageHealthcheckIfRequired
    // ============================================================

    @Test
    void waitForNoneSkipsImageInspectForHealthcheckValidation() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");
        stubCreate(c, "img:1", "cid");
        stubStart(c, "c");

        StartContainerTask.Params p = params("img:1", WaitFor.none(), PullPolicy.IF_NOT_PRESENT, Map.of());

        StartContainerTask.run(c, p, mock(Logger.class));

        verify(c, never()).inspectImageCmd(anyString());
    }

    @Test
    void waitForHealthcheckThrowsWhenImageNotFoundLocally() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");

        InspectImageCmd insImg = mock(InspectImageCmd.class);
        when(c.inspectImageCmd("img:1")).thenReturn(insImg);
        when(insImg.exec()).thenThrow(new NotFoundException("missing"));

        StartContainerTask.Params p = params("img:1", WaitFor.healthcheck(), PullPolicy.NEVER, Map.of());

        assertThatThrownBy(() -> StartContainerTask.run(c, p, mock(Logger.class)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("is not present locally and pullPolicy=");
    }

    @Test
    void waitForHealthcheckThrowsWhenHealthcheckTestIsNone() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");

        InspectImageCmd insImg = mock(InspectImageCmd.class);
        InspectImageResponse insImgResp = mock(InspectImageResponse.class);
        ContainerConfig cfg = mock(ContainerConfig.class);
        HealthCheck hc = mock(HealthCheck.class);
        when(c.inspectImageCmd("img:1")).thenReturn(insImg);
        when(insImg.exec()).thenReturn(insImgResp);
        when(insImgResp.getConfig()).thenReturn(cfg);
        when(cfg.getHealthcheck()).thenReturn(hc);
        when(hc.getTest()).thenReturn(List.of("NONE"));

        StartContainerTask.Params p = params("img:1", WaitFor.healthcheck(), PullPolicy.IF_NOT_PRESENT, Map.of());

        assertThatThrownBy(() -> StartContainerTask.run(c, p, mock(Logger.class)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("declares no HEALTHCHECK");
    }

    @Test
    void waitForHealthcheckPassesWhenHealthcheckHasRealCommand() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");

        InspectImageCmd insImg = mock(InspectImageCmd.class);
        InspectImageResponse insImgResp = mock(InspectImageResponse.class);
        ContainerConfig cfg = mock(ContainerConfig.class);
        HealthCheck hc = mock(HealthCheck.class);
        when(c.inspectImageCmd("img:1")).thenReturn(insImg);
        when(insImg.exec()).thenReturn(insImgResp);
        when(insImgResp.getConfig()).thenReturn(cfg);
        when(cfg.getHealthcheck()).thenReturn(hc);
        when(hc.getTest()).thenReturn(List.of("CMD-SHELL", "exit 0"));

        // Container absent then running. Readiness.healthcheck will poll inspectContainerCmd
        // and time out (state has no Health), throwing NotReadyException — that's fine; we
        // just need to confirm validateImageHealthcheckIfRequired *passed*.
        stubContainerAbsentThenRunning(c, "c");
        stubCreate(c, "img:1", "cid");
        stubStart(c, "c");

        StartContainerTask.Params p = params("img:1", WaitFor.healthcheck(), PullPolicy.IF_NOT_PRESENT, Map.of());

        // Healthcheck readiness polling will throw NotReadyException; that proves we got past
        // validateImageHealthcheckIfRequired into the readiness path.
        assertThatThrownBy(() -> StartContainerTask.run(c, p, mock(Logger.class)))
                .hasMessageContaining("not healthy");
    }

    @Test
    void waitForHealthcheckThrowsWhenImageConfigIsNull() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");

        InspectImageCmd insImg = mock(InspectImageCmd.class);
        InspectImageResponse insImgResp = mock(InspectImageResponse.class);
        when(c.inspectImageCmd("img:1")).thenReturn(insImg);
        when(insImg.exec()).thenReturn(insImgResp);
        when(insImgResp.getConfig()).thenReturn(null);

        StartContainerTask.Params p = params("img:1", WaitFor.healthcheck(), PullPolicy.IF_NOT_PRESENT, Map.of());

        assertThatThrownBy(() -> StartContainerTask.run(c, p, mock(Logger.class)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("declares no HEALTHCHECK");
    }

    // ============================================================
    // ensureContainerExists / startContainer / isAlreadyRunningOrUnstartable
    // ============================================================

    @Test
    void existingRunningContainerSkipsStart() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubExistingContainer(c, "c", mockRunningContainer());

        StartContainerCmd start = stubStart(c, "c");

        StartContainerTask.Params p = params("img:1", WaitFor.none(), PullPolicy.IF_NOT_PRESENT, Map.of());

        StartContainerTask.run(c, p, mock(Logger.class));

        verify(start, never()).exec();
        verify(c, never()).createContainerCmd(anyString());
    }

    @Test
    void existingPausedContainerThrowsWithRemoveInstruction() {
        existingUnstartableThrows("paused");
    }

    @Test
    void existingDeadContainerThrows() {
        existingUnstartableThrows("dead");
    }

    @Test
    void existingRemovingContainerThrows() {
        existingUnstartableThrows("removing");
    }

    private void existingUnstartableThrows(String stateName) {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubExistingContainer(c, "c", mockContainer(stateName, false));

        StartContainerTask.Params p = params("img:1", WaitFor.none(), PullPolicy.IF_NOT_PRESENT, Map.of());

        assertThatThrownBy(() -> StartContainerTask.run(c, p, mock(Logger.class)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("'" + stateName + "'")
                .hasMessageContaining("Run remove");
    }

    @Test
    void existingExitedContainerIsStarted() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubExistingContainer(c, "c", mockContainer("exited", false));

        StartContainerCmd start = stubStart(c, "c");

        StartContainerTask.Params p = params("img:1", WaitFor.none(), PullPolicy.IF_NOT_PRESENT, Map.of());

        StartContainerTask.run(c, p, mock(Logger.class));

        verify(start).exec();
    }

    @Test
    void existingContainerWithNullStateProceedsToStart() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubExistingContainer(c, "c", mockContainerNullState());

        StartContainerCmd start = stubStart(c, "c");

        StartContainerTask.Params p = params("img:1", WaitFor.none(), PullPolicy.IF_NOT_PRESENT, Map.of());

        StartContainerTask.run(c, p, mock(Logger.class));

        verify(start).exec();
    }

    @Test
    void existingContainerWithNullStatusFieldProceedsToStart() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        // running=null, status=null — exercises the "unknown" status branch
        stubExistingContainer(c, "c", mockContainer(null, null));

        StartContainerCmd start = stubStart(c, "c");

        StartContainerTask.Params p = params("img:1", WaitFor.none(), PullPolicy.IF_NOT_PRESENT, Map.of());

        StartContainerTask.run(c, p, mock(Logger.class));

        verify(start).exec();
    }

    // ============================================================
    // createContainer (env / command / ports / mounts / networks)
    // ============================================================

    @Test
    void createSkipsAllOptionalConfigWhenAllEmpty() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");

        CreateContainerCmd create = stubCreate(c, "img:1", "cid");
        stubStart(c, "c");

        StartContainerTask.Params p = paramsFull(
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                WaitFor.none(),
                PullPolicy.IF_NOT_PRESENT);

        StartContainerTask.run(c, p, mock(Logger.class));

        verify(create, never()).withEnv(anyList());
        verify(create, never()).withCmd(anyList());
        verify(create, never()).withExposedPorts(anyList());
        verify(c, never()).connectToNetworkCmd();
    }

    @Test
    void createAppliesEnvironmentAsKeyValuePairs() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");

        CreateContainerCmd create = stubCreate(c, "img:1", "cid");
        stubStart(c, "c");

        StartContainerTask.Params p = paramsFull(
                Map.of("FOO", "bar"),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                WaitFor.none(),
                PullPolicy.IF_NOT_PRESENT);

        StartContainerTask.run(c, p, mock(Logger.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> envCaptor = ArgumentCaptor.forClass(List.class);
        verify(create).withEnv(envCaptor.capture());
        assertThat(envCaptor.getValue()).containsExactly("FOO=bar");
    }

    @Test
    void createAppliesCommand() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");

        CreateContainerCmd create = stubCreate(c, "img:1", "cid");
        stubStart(c, "c");

        List<String> command = List.of("sh", "-c", "true");

        StartContainerTask.Params p = paramsFull(
                Map.of(),
                Map.of(),
                List.of(),
                command,
                List.of(),
                List.of(),
                WaitFor.none(),
                PullPolicy.IF_NOT_PRESENT);

        StartContainerTask.run(c, p, mock(Logger.class));

        verify(create).withCmd(command);
    }

    @Test
    void createAppliesExposedPortsAndUsesFreshHostConfigWhenNull() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");

        CreateContainerCmd create = stubCreate(c, "img:1", "cid");
        // First call returns null -> fresh HostConfig is created and wired via withHostConfig.
        when(create.getHostConfig()).thenReturn(null);
        stubStart(c, "c");

        StartContainerTask.Params p = paramsFull(
                Map.of(),
                Map.of(8080, 80),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                WaitFor.none(),
                PullPolicy.IF_NOT_PRESENT);

        StartContainerTask.run(c, p, mock(Logger.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExposedPort>> exposedCaptor = ArgumentCaptor.forClass(List.class);
        verify(create).withExposedPorts(exposedCaptor.capture());
        assertThat(exposedCaptor.getValue()).containsExactly(ExposedPort.tcp(80));
        verify(create).withHostConfig(any(HostConfig.class));
    }

    @Test
    void createAppliesVolumeMountsWithReadOnlyAccessMode() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");

        CreateContainerCmd create = stubCreate(c, "img:1", "cid");
        HostConfig hc = HostConfig.newHostConfig();
        when(create.getHostConfig()).thenReturn(hc);
        stubStart(c, "c");

        Mounts.VolumeMount vm = new Mounts.VolumeMount("data", "/var/lib/data", true);

        StartContainerTask.Params p = paramsFull(
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(vm),
                List.of(),
                WaitFor.none(),
                PullPolicy.IF_NOT_PRESENT);

        StartContainerTask.run(c, p, mock(Logger.class));

        Bind[] binds = hc.getBinds();
        assertThat(binds).hasSize(1);
        assertThat(binds[0].getPath()).isEqualTo("data");
        assertThat(binds[0].getVolume().getPath()).isEqualTo("/var/lib/data");
        assertThat(binds[0].getAccessMode()).isEqualTo(AccessMode.ro);
    }

    @Test
    void createAppliesBindMountsWithReadWriteAccessMode() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");

        CreateContainerCmd create = stubCreate(c, "img:1", "cid");
        HostConfig hc = HostConfig.newHostConfig();
        when(create.getHostConfig()).thenReturn(hc);
        stubStart(c, "c");

        Mounts.BindMount bm = new Mounts.BindMount("/host/path", "/container/path", false);

        StartContainerTask.Params p = paramsFull(
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(bm),
                WaitFor.none(),
                PullPolicy.IF_NOT_PRESENT);

        StartContainerTask.run(c, p, mock(Logger.class));

        Bind[] binds = hc.getBinds();
        assertThat(binds).hasSize(1);
        assertThat(binds[0].getPath()).isEqualTo("/host/path");
        assertThat(binds[0].getVolume().getPath()).isEqualTo("/container/path");
        assertThat(binds[0].getAccessMode()).isEqualTo(AccessMode.rw);
    }

    @Test
    void createCombinesVolumeAndBindMountsInSingleWithBinds() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");

        CreateContainerCmd create = stubCreate(c, "img:1", "cid");
        HostConfig hc = HostConfig.newHostConfig();
        when(create.getHostConfig()).thenReturn(hc);
        stubStart(c, "c");

        Mounts.VolumeMount vm = new Mounts.VolumeMount("data", "/data", false);
        Mounts.BindMount bm = new Mounts.BindMount("/host", "/host-target", false);

        StartContainerTask.Params p = paramsFull(
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(vm),
                List.of(bm),
                WaitFor.none(),
                PullPolicy.IF_NOT_PRESENT);

        StartContainerTask.run(c, p, mock(Logger.class));

        Bind[] binds = hc.getBinds();
        assertThat(binds).hasSize(2);
    }

    @Test
    void singleNetworkUsesNetworkModeAndDoesNotCallConnectToNetwork() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");

        CreateContainerCmd create = stubCreate(c, "img:1", "cid");
        HostConfig hc = HostConfig.newHostConfig();
        when(create.getHostConfig()).thenReturn(hc);
        stubStart(c, "c");

        StartContainerTask.Params p = paramsFull(
                Map.of(),
                Map.of(),
                List.of("net1"),
                List.of(),
                List.of(),
                List.of(),
                WaitFor.none(),
                PullPolicy.IF_NOT_PRESENT);

        StartContainerTask.run(c, p, mock(Logger.class));

        assertThat(hc.getNetworkMode()).isEqualTo("net1");
        verify(c, never()).connectToNetworkCmd();
    }

    @Test
    void multipleNetworksUseFirstAsNetworkModeAndConnectRest() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");

        CreateContainerCmd create = stubCreate(c, "img:1", "cid");
        HostConfig hc = HostConfig.newHostConfig();
        when(create.getHostConfig()).thenReturn(hc);
        stubStart(c, "c");

        ConnectToNetworkCmd connect = mock(ConnectToNetworkCmd.class, RETURNS_SELF);
        when(c.connectToNetworkCmd()).thenReturn(connect);

        StartContainerTask.Params p = paramsFull(
                Map.of(),
                Map.of(),
                List.of("net1", "net2", "net3"),
                List.of(),
                List.of(),
                List.of(),
                WaitFor.none(),
                PullPolicy.IF_NOT_PRESENT);

        StartContainerTask.run(c, p, mock(Logger.class));

        assertThat(hc.getNetworkMode()).isEqualTo("net1");
        verify(c, times(2)).connectToNetworkCmd();
        verify(connect).withNetworkId("net2");
        verify(connect).withNetworkId("net3");
        verify(connect, times(2)).exec();
    }

    // ============================================================
    // waitForReadiness (switch on WaitFor)
    // ============================================================

    @Test
    void waitForNoneCompletesWithoutReadinessPolling() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        InspectContainerCmd insCnt = stubContainerAbsentThenRunning(c, "c");
        stubCreate(c, "img:1", "cid");
        stubStart(c, "c");

        StartContainerTask.Params p = params("img:1", WaitFor.none(), PullPolicy.IF_NOT_PRESENT, Map.of());

        StartContainerTask.run(c, p, mock(Logger.class));

        // No log streaming, and only the single post-start running-status check.
        verify(c, never()).logContainerCmd(anyString());
        verify(insCnt, times(1)).exec();
    }

    @Test
    void waitForLogLineInvokesLogContainerCmd() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");
        stubCreate(c, "img:1", "cid");
        stubStart(c, "c");

        // logContainerCmd returns null so the LogLine arm NPEs/throws fast — this is enough
        // to confirm the LogLine branch was entered.
        when(c.logContainerCmd("c")).thenReturn(null);

        StartContainerTask.Params p = params("img:1", WaitFor.logLine("ready"), PullPolicy.IF_NOT_PRESENT, Map.of());

        assertThatThrownBy(() -> StartContainerTask.run(c, p, mock(Logger.class)))
                .isInstanceOf(Throwable.class);
        verify(c).logContainerCmd("c");
    }

    @Test
    void waitForTcpPortAttemptsConnectionWhenPortIsMapped() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");
        stubCreate(c, "img:1", "cid");
        stubStart(c, "c");

        // Use a high arbitrary host port that is almost certainly closed; Readiness.tcpPort
        // will time out and throw NotReadyException, proving the TcpPort arm executed and
        // that daemonHost/findHostPortMappedTo resolved correctly.
        StartContainerTask.Params p =
                params("img:1", WaitFor.tcpPort(80), PullPolicy.IF_NOT_PRESENT, Map.of(54329, 80));

        assertThatThrownBy(() -> StartContainerTask.run(c, p, mock(Logger.class)))
                .hasMessageContaining("not reachable");
    }

    @Test
    void waitForTcpPortThrowsWhenNoHostPortMapsToContainerPort() {
        DockerClient c = mock(DockerClient.class);

        stubImagePresent(c, "img:1");
        stubInspectImageStub(c, "img:1");
        stubContainerAbsentThenRunning(c, "c");
        stubCreate(c, "img:1", "cid");
        stubStart(c, "c");

        // Map host 8080 -> container 81; we'll wait for 80, which is unmapped.
        StartContainerTask.Params p = params("img:1", WaitFor.tcpPort(80), PullPolicy.IF_NOT_PRESENT, Map.of(8080, 81));

        assertThatThrownBy(() -> StartContainerTask.run(c, p, mock(Logger.class)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("no host port is mapped to container port")
                .hasMessageContaining("80");
    }
}
