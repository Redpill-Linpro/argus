# Development

## Prerequisites

- Java 21+ (the build targets release 21 via `maven.compiler.release`)
- Maven 3.9+

## Build, test, run

```bash
mvn verify          # unit tests + integration tests (failsafe) + jar
mvn javafx:run      # launch the application
mvn -DskipTests package  # jar only
```

### Tests

- **Unit tests** (`*Test`, surefire): model, profile persistence, property coercion.
- **Integration tests** (`*IT`, failsafe, part of `mvn verify`): start an **embedded Artemis
  broker** (`it/EmbeddedArtemisSupport`) with JAAS properties login (users `argus`/`restricted`)
  and exercise both protocol clients for real: `CoreBrokerClientIT` (management listing,
  browse + selector + max, browse-denied, listing-denied) and `OpenWireBrokerClientIT`
  (advisory-based listing, browse + selector).
- `UiSmokeIT` drives the real FXML UI end-to-end (connect, tree, browse, send, screenshot to
  `target/ui-smoke.png` against an embedded broker). It needs an X display; without one it is
  skipped. Headless run (with Docker):

  ```bash
  docker run --rm --network host -v "$PWD":/work -v argus-m2:/root/.m2 -w /work \
    maven:3.9-eclipse-temurin-21 bash -c \
    "apt-get update -qq && apt-get install -y -qq xvfb libgtk-3-0 libgl1 libfontconfig1 fonts-dejavu-core \
     >/dev/null 2>&1; (Xvfb :99 -screen 0 1280x1024x24 &>/dev/null &); sleep 2; \
     DISPLAY=:99 mvn -B -Dsurefire.skip=true -Dit.test=UiSmokeIT verify"
  ```

- `BrokerConnectProbe` is a standalone main for diagnosing connectivity/auth
  (`java -cp ... BrokerConnectProbe host port user pass`).
- Run only the ITs: `mvn -Dsurefire.skip=true -Dit.test=CoreBrokerClientIT verify`.

## Architecture in one paragraph

`ui/` (JavaFX controllers + FXML) drives everything through the `broker/BrokerClient`
interface. `broker/CoreBrokerClient` talks to Artemis over the core protocol: listing
via management *messages* on `activemq.management` (`ManagementHelper` + `ClientRequestor`,
attribute/operation names mirroring `AddressControl`/`QueueControl`), browsing/sending via
the Artemis JMS client (QueueBrowser, FQQN `address::queue`). `broker/OpenWireBrokerClient`
talks OpenWire using `activemq-client` (6.x, jakarta): listing via `DestinationSource`
advisories (best effort), browsing/sending via JMS. Model types are immutable records.
Connection profiles persist as JSON via Jackson in `config/ProfileStore`.

## Invariants (see AGENTS.md)

- No JMX anywhere; Core management messages only for listing.
- All broker I/O on `util/BrokerExecutor`; update UI via `Platform.runLater`.
- `BrokerClient` is the only seam between UI and broker libraries.
- Permission failures surface as `BrokerException.isAccessDenied()` with actionable messages.

## Destination semantics (Artemis)

A JMS queue name maps to the **anycast address with the same name**. For queues whose
name differs from their address, only the full queue-queue name (FQQN) form
`address::queue` is reachable. The Send tab therefore pre-fills `address::queue` for such
queues `address::queue`; otherwise it uses the queue name.

## Packaging (jpackage)

`Launcher` is a non-`Application` wrapper around `ArgusApp` so the app runs from the
classpath inside a jpackage image (classic non-modular JavaFX pattern).

Local build (macOS/Linux):

```bash
./scripts/package.sh              # deb on Linux, dmg on macOS
./scripts/package.sh rpm|app-image
```

Windows (PowerShell, WiX required for msi/exe):

```powershell
./scripts/package.ps1 -Type msi
```

Both stage runtime jars under `target/jpackage-input`, then invoke `jpackage` and drop the
installer artefact(s) into `target/jpackage/`. The installer ships a private JRE — no Java
installation is required on the target machine.

## CI

`.github/workflows/build.yml` runs `mvn verify` and produces installers in a matrix
(ubuntu→deb, macos→dmg, windows→msi); installers are uploaded as build artifacts.
