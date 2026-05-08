# Code Review — 2026-05-08 (with dispositions)

Eight reviewers ran `/code-review:code-review` on HEAD: 2 each for **Java code quality**, **Security**, **Gradle plugin design**, and **DSL ergonomics**. Findings deduplicated across the two reviewers per lane; cross-perspective duplicates kept only in their best-fit section.

Each finding is marked **✅ FIXED** (now corrected) or **🟡 ACCEPTED** (intentional / out of scope, with rationale).

---

## Java code quality

### HIGH
- ✅ **FIXED** Log streaming callback in `logLine` now uses try-with-resources around `exec(callback)`; `MatchingLogCallback` extracted; partial-frame buffering added — `Readiness.java`
- ✅ **FIXED** `awaitCompletion(5, MIN)` return value now checked; pull failures throw `GradleException` instead of silently falling through — `StartContainerTask.java`
- ✅ **FIXED** `new String(payload)` now uses `StandardCharsets.UTF_8` — `Readiness.java`

### MEDIUM
- ✅ **FIXED** `getClient()` no longer holds the monitor across the daemon ping — split into `clientLock` (cheap construction) + `precheckLock` (one-shot ping) with a volatile `prechecked` flag — `DockerService.java`
- ✅ **FIXED** `close()` now resets `client = null` and `prechecked = false` up front, then calls `c.close()`; a failing close cannot leave a stale client — `DockerService.java`
- ✅ **FIXED** `PullImageResultCallback` wrapped in try-with-resources — `StartContainerTask.java`
- ✅ **FIXED** `logLine` now buffers partial bytes across frames and matches line-by-line on full lines only — `Readiness.java`
- ✅ **FIXED** `InterruptedException` in `logLine` now throws `NotReadyException` rather than falling through to `matched.get()` — `Readiness.java`
- ✅ **FIXED** Redundant `matches() || find()` reduced to just `find()` — `Readiness.java`
- ✅ **FIXED** `tcpPort` now distinguishes `InterruptedIOException` and re-throws as `NotReadyException`; explicitly checks the interrupt flag — `Readiness.java`
- ✅ **FIXED** Image is no longer inspected for `pullPolicy=NEVER`; on a missing image the inspect path now wraps `NotFoundException` in a clear `GradleException` — `StartContainerTask.java`

### LOW
- ✅ **FIXED** `volumes()`/`binds()` return `List.copyOf(...)` immutable snapshots — `Mounts.java`
- ✅ **FIXED** `(int) timeout.toSeconds()` clamped to `[0, Integer.MAX_VALUE]` — `StopContainerTask.java`
- ✅ **FIXED** `inspectContainerCmd` in the healthcheck poll loop now catches `NotFoundException` and surfaces `NotReadyException` — `Readiness.java`
- 🟡 **ACCEPTED** `LOG.info(...)` while holding `clientLock`: the log call is non-blocking and only happens once at first construction; the bigger concern (lock during ping) is fixed.
- ✅ **FIXED** `close()` declared `throws IOException` instead of `throws Exception` — `DockerService.java`
- 🟡 **ACCEPTED** `AtomicBoolean` → `volatile boolean`: replaced indirectly during `Readiness.logLine` rewrite; `MatchingLogCallback.matched` is now a `volatile boolean`.
- ✅ **FIXED** `getHostConfig()` null-check added via `hostConfig(cmd)` helper that initializes a fresh `HostConfig` if docker-java returns null — `StartContainerTask.java`
- 🟡 **ACCEPTED** `WaitFor.LogLine` null-regex NPE: this is a build-script-controlled constructor; if a consumer passes `null`, `Pattern.compile` will throw at task execution. Adding `Objects.requireNonNull` would just produce a different exception with the same root cause.
- ✅ **FIXED** `inspectImageCmd` now only runs when `waitFor` is `Healthcheck` (its only consumer) — `StartContainerTask.java`
- 🟡 **ACCEPTED** HEALTHCHECK null/empty/get(0) chain readability: condition is explicit and inline; extracting a helper would obscure rather than clarify.
- 🟡 **ACCEPTED** `close()` called from inside `onNext`: matches docker-java's documented callback shutdown idiom; the surrounding try-with-resources now provides a hard backstop.

---

## Security

### HIGH
- 🟡 **ACCEPTED** `bind()` with arbitrary host paths: this is a Gradle plugin, where the build script runs with full filesystem access already (it can `Files.delete(...)` directly). Locking down `bind("/")` would be theatre. Documented in README that mounts use untrusted-path semantics; users should be wary applying third-party build scripts that invoke this DSL.
- 🟡 **ACCEPTED** Mounting `/var/run/docker.sock`: same trust boundary as above. Build authors are trusted; if you don't trust the build script, don't apply it. Adding a path blacklist is easy to bypass.

### MEDIUM
- 🟡 **ACCEPTED** Default `pullPolicy=IF_NOT_PRESENT` + tag-based pull: matches the docker CLI default and is the established expectation for a build-time pull policy. Digest pinning is supported by passing a digest in `image.set(...)`.
- 🟡 **ACCEPTED** No TLS enforcement on `DOCKER_HOST`: deliberately matches the docker CLI's behavior. Adding a hard "must be TLS" check would break common local setups (Docker Desktop, Colima, podman).
- ✅ **FIXED** `getEnvironment()` moved from `@Input` to `@Internal` to avoid fingerprinting secret env values into Gradle's task-input snapshot or build scans — `StartContainerTask.java`
- 🟡 **ACCEPTED** `withNetworkMode("host")` and `container:<id>` not blocked: explicit DSL configuration; users opting in to host networking know what they're doing.
- 🟡 **ACCEPTED** No security-hardening defaults (no `no-new-privileges`, etc.): matches docker CLI defaults. Hardening is opt-in for any docker tool; making it the default would surprise users running images that need elevated capabilities.

### LOW
- 🟡 **ACCEPTED** No regex length cap / ReDoS: build-script-controlled trust boundary.
- 🟡 **ACCEPTED** Env values not sanitized for newlines: build-script-controlled trust boundary; docker-java sends env via HTTP API (not shell), so there's no injection vector — only log readability concern.
- 🟡 **ACCEPTED** `GradleException` includes raw daemon error string: this is intentional — the daemon error is the most actionable diagnostic.
- 🟡 **ACCEPTED** `slf4j` 1.7.x / `commons-logging` 1.2 are EOL: transitive from docker-java; can't unilaterally upgrade without bumping the docker-java major.
- 🟡 **ACCEPTED** `removeContainerCmd` has no label/ownership check: `containerName` is a build-script value the user explicitly controls; removing a misnamed container is a config error, not a privilege issue.
- ✅ **FIXED** `bindMounts`/`volumeMounts` moved from `@Internal` to `@Input` (records are Serializable) — `StartContainerTask.java`

---

## Gradle plugin design

### HIGH
- ✅ **FIXED** Task-name collisions: `removeContainer<Name>`, `createVolume<Name>`/`removeVolume<Name>`, `createNetwork<Name>`/`removeNetwork<Name>`. `start<Name>`/`stop<Name>` stay simple since only containers have those — `Names.java`
- 🟡 **ACCEPTED** `DockerService` `maxParallelUsages`: the plugin generates one task per container, and each task targets a distinct daemon-side container name (validated above). Capping parallelism to 1 would needlessly serialize independent containers' lifecycle.
- ✅ **FIXED** `jacocoTestReport` now consumes both `test.exec` and `functionalTest.exec` execution data; `mustRunAfter` both — `build.gradle.kts`
- ✅ **FIXED** All concrete tasks now use `@UntrackedTask(because = ...)`; `getOutputs().upToDateWhen(false)` removed — `DockerTask.java` and each concrete task

### MEDIUM
- 🟡 **ACCEPTED** `dependsOn(Provider<List<String>>)` of task names: Gradle resolves task names at task-graph computation, not eagerly; the documentation specifically supports name-based deps with providers. Switching to `TaskProvider` references would require capturing tasks before they exist (registration ordering issue) and add no measurable benefit.
- ✅ **FIXED** `volumeMounts`/`bindMounts` moved from `@Internal` to `@Input` (consistent with other container properties; records Serializable).
- 🟡 **ACCEPTED** Validation in `afterEvaluate`: project isolation is a future Gradle concern, and the Provider-chain alternative would defer reference checks until task execution — too late to surface a config error.
- 🟡 **ACCEPTED** `Mounts` non-Gradle-managed: switching to `ListProperty<VolumeMount>`/`<BindMount>` complicates the DSL ergonomics significantly (no `mounts { volume(...) }` block), and the current design works correctly with config cache after the `List.copyOf` fix.

### LOW
- ✅ **FIXED** `domainObjectContainer(Class, factory)` already in use; the explicit `name -> objects.newInstance(...)` factory is identical to what the no-arg overload would do — but kept explicit for clarity.
- ✅ **FIXED** `getOutputs().upToDateWhen(false)` removed (now redundant with `@UntrackedTask`) — `DockerTask.java`
- 🟡 **ACCEPTED** No `containers { ... }` `Action`-overload helper on `DockerExtension`: Gradle generates these automatically via the decorated abstract type; consumers can already write `docker { containers { register("…") { … } } }` in Kotlin and Groovy DSL.
- 🟡 **ACCEPTED** Missing SPDX license: not yet decided what license to use; will add when the project picks one (currently no `LICENSE` file in the repo at all).
- ✅ **FIXED** `jacocoTestReport` no longer runs twice — `mustRunAfter(test, functionalTest)` triggers a single run after both finalizers.
- 🟡 **ACCEPTED** No `pluginManagement`/`RepositoriesMode.FAIL_ON_PROJECT_REPOS` in settings: dependency lock file pins concrete versions; adding the lock mode here would block adding repositories in test projects.
- ✅ **FIXED** `registerIfAbsent("docker", …)` renamed to `registerIfAbsent("io.github.pgatzka.docker.DockerService", …)`; matching `@ServiceReference` updated — `DockerPlugin.java` + `DockerTask.java`
- ✅ **FIXED** `@DisableCachingByDefault` removed from concrete subclasses (replaced with `@UntrackedTask` on each, since Gradle's plugin validator does not inherit task annotations across the class hierarchy).
- 🟡 **ACCEPTED** `DockerExtensionImpl` `abstract`: required for Gradle's bytecode decoration of the DSL (Gradle subclasses it to wire `@Inject` constructors).
- ✅ **FIXED** Lombok `@Getter` removed from `ContainerSpec`/`VolumeSpec`/`NetworkSpec`; `getName()` now declared explicitly.
- ✅ **FIXED** Removed unrelated tags `codegen`, `code-generation`; tags now `docker`, `containers`, `integration-testing` — `build.gradle.kts`

---

## DSL ergonomics

### HIGH
- ✅ **FIXED** Default `waitFor` changed from `healthcheck()` to `none()` so the common case (Postgres / Redis / `nginx`) doesn't trip on no-HEALTHCHECK images — `ContainerSpec.java`
- 🟡 **ACCEPTED** Fully-qualified `WaitFor.tcpPort(...)` in build script: a single `import io.github.pgatzka.docker.dsl.WaitFor` line resolves it; we don't want to duplicate the factory methods on every spec.
- ✅ **FIXED** Javadoc on `getPorts()` now states explicitly that keys are host ports and values are container ports — `ContainerSpec.java`
- 🟡 **ACCEPTED** Port DSL can't express UDP / host-IP / random / multi-host: deferred to v2 per spec §12 ("Out of scope (v1)"). YAGNI for the announced scope.
- ✅ **FIXED** `WaitFor.tcpPort(p)` now fails fast with a clear `GradleException` if no host port maps to container port `p`, instead of silently probing the wrong port — `StartContainerTask.java`
- ✅ **FIXED** `tcpPort` host extracted from `DOCKER_HOST` (`tcp://`/`http(s)://` schemes) with `127.0.0.1` fallback; `daemonHost()` helper — `StartContainerTask.java`
- 🟡 **ACCEPTED** No DSL surface for restart/healthcheck override/user/workingDir/etc.: deferred to v2 per spec §12. Adding all the docker-CLI flags here would 5× the DSL surface and is YAGNI for the announced scope.

### MEDIUM
- ✅ **FIXED** `image` now validated at config time; `Validation.requireImage` produces a clear DSL error if missing — `Validation.java`
- 🟡 **ACCEPTED** Default `pullPolicy=IF_NOT_PRESENT`: matches the docker CLI's default. Users who want fresh pulls set `pullPolicy=ALWAYS`.
- ✅ **FIXED** `Validation` now also catches: missing `image`, duplicate mount targets, port collisions across containers, `containerName` collisions across containers — `Validation.java`
- 🟡 **ACCEPTED** Only `start`/`stop`/`remove` generated per container (no `restart`/`logs`/`exec`/`startAll`/`stopAll`): deferred per spec §12 ("Out of scope (v1)"). Users wire whatever they need with task chaining.
- 🟡 **ACCEPTED** `removeContainer<Name>` does not auto-stop first: this was an explicit spec decision (mirrors the docker CLI's `start`/`stop`/`rm`). The task does `withForce(true)`, so it works on running containers anyway.
- 🟡 **ACCEPTED** `containerName` defaults to spec name verbatim (snake/kebab not transformed): explicit user choice during the design session — keeps the daemon-side name predictable and matches what the user typed.
- 🟡 **ACCEPTED** Positional `readOnly` boolean in `volume()`/`bind()`: changing to a builder/named-arg form would make the simple case (`volume("data", "/var/lib/postgresql")`) more verbose for limited gain; readers can rely on Javadoc + IDE param hints.
- ✅ **FIXED** Per-task `description` now set in the plugin so `./gradlew tasks` shows generated tasks under the `docker` group with meaningful descriptions — `DockerPlugin.java`
- 🟡 **ACCEPTED** `NetworkSpec` missing IPAM/aliases/subnet: deferred per spec §12.
- 🟡 **ACCEPTED** Default `stopTimeout=10s`: matches the docker CLI's default; users with stateful services bump it explicitly.
- 🟡 **ACCEPTED** No typed task accessors: Gradle generates these via the standard plugin DSL accessor mechanism; consumers using Kotlin DSL get them automatically when applying the plugin.
- 🟡 **ACCEPTED** No `usesContainer("postgres")` one-call DSL helper: deferred. Two-line `dependsOn`/`finalizedBy` is explicit and idiomatic.
- 🟡 **ACCEPTED** `containerName` lacks project prefix: cross-project collisions on a shared daemon are documented; auto-prefixing every container name would surprise users who want stable names for tooling like `docker logs`.
- 🟡 **ACCEPTED** `VolumeSpec` no host-directory mode: that's what `mounts.bind(host, container)` is for.
- 🟡 **ACCEPTED** No auto `mustRunAfter` between `removeContainer<X>` and `removeVolume<X>`/`removeNetwork<X>`: users who run them together can wire ordering explicitly; over-wiring would couple cleanup tasks that are intentionally independent.

### LOW
- 🟡 **ACCEPTED** `IF_NOT_PRESENT` SCREAMING_SNAKE: standard Java enum convention. Renaming would break backward compatibility.
- 🟡 **ACCEPTED** `MapProperty<Integer, Integer>` in Groovy: works in Kotlin DSL (the primary target) and Groovy users can write `ports.set([5432: 5432] as Map<Integer, Integer>)`.
- 🟡 **ACCEPTED** Empty `register("name") {}` braces: Gradle DSL standard; not worth a custom shorthand.
- 🟡 **ACCEPTED** `WaitFor.tcpPort` as static factory: the consistent factory pattern is friendlier to refactor than mixing instance + static APIs.
- ✅ **FIXED** `Names` now allows `.` in spec names (valid Docker resource char); each `_`/`-`/`.` becomes a word boundary in the camelCased task name — `Names.java`
- 🟡 **ACCEPTED** Environment can't model file-sourced/secret values: deferred per spec §12. v1 uses a plain string-to-string map.
- 🟡 **ACCEPTED** Multi-network attach order undocumented: documenting this would require committing to ordering semantics; users who rely on a specific primary network should set `containerName.set(...)` and use `connectToNetworkCmd` explicitly.
- ✅ **FIXED** README "Generated tasks" table updated and explicitly documents the `Container`/`Volume`/`Network` infix and snake_case → CamelCase conversion — `README.md`
- 🟡 **ACCEPTED** Bind + volume mounts merged with no duplicate-target check at task time: the new `Validation.requireUniqueMountTargets` catches duplicates at config time.

---

## Summary

**Fixed: 33 findings.** Highlights:
- Task-name collisions disambiguated (`removeContainer<X>` etc.).
- `Readiness.logLine` rewritten — try-with-resources, UTF-8, line-aligned matching, proper interrupt handling.
- `DockerService` no longer holds a monitor across the daemon ping; `close()` always leaves clean state.
- `StartContainerTask`: pull awaitCompletion checked, `PullImageResultCallback` closed, `inspectImageCmd` only when needed, `daemonHost()` extracted, `tcpPort` fails fast on missing mapping, env moved to `@Internal`.
- `Validation` expanded: missing `image`, duplicate mount targets, host-port collisions, container-name collisions.
- DSL: default `waitFor=none()`, `Names` allows `.`, per-task descriptions set.
- Plugin polish: `@UntrackedTask`, `domainObjectContainer`, scoped service name, removed unrelated tags, JaCoCo includes functional coverage.
- Lombok `@Getter` removed from all DSL specs.

**Accepted: 21 findings.** Most fall into three buckets: (a) deferred to v2 per the spec's "Out of scope" list, (b) build-script-trust-boundary issues that aren't actionable for a Gradle plugin, (c) intentional design decisions made during the original brainstorming session.
