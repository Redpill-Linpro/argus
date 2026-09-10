# AGENTS.md

## What

Argus is a JavaFX desktop client for **ActiveMQ Artemis** (`com.redpill_linpro:argus`).
It connects over the **Core** protocol (default) or **OpenWire**, lists addresses/queues,
browses messages and sends messages. It never uses JMX or the Artemis REST/console.

## Why

- Broker access should use the same protocol-native primitives as regular applications,
  so it works wherever JMX is disabled, remote or not reachable.
- Management-based listing is done with Artemis management *messages* (request/reply on
  the `activemq.management` address), never JMX.
- Protocol choice is explicit per connection profile (no auto-fallback) so users get
  predictable behaviour and errors. OpenWire mode targets Artemis only (not ActiveMQ
  "Classic"); its listing relies on destination advisories and degrades to manual entry.
- Access errors are surfaced, never hidden: a user without `browse` sees an explicit
  access-denied message for that queue.

## How

- Build/test: `mvn verify` (unit `*Test` + integration `*IT` with embedded Artemis) — run app: `mvn javafx:run`.
- Package installers: `scripts/package.sh` (deb/dmg) / `scripts/package.ps1` (msi); CI matrix in
  `.github/workflows/build.yml`.
- Library pins: `artemis-jakarta-client` + `artemis-core-client` (Core), `activemq-client`
  6.x (OpenWire protocol), JavaFX, Jackson. Versions live in `pom.xml` properties.
- All broker I/O runs on a background executor (`util/BrokerExecutor`); UI updates must be
  marshalled with `Platform.runLater`. Never call `BrokerClient` methods on the FX thread.
- `BrokerClient` is the single seam between UI and protocols; new broker features are added
  to the interface, not to controllers.
- Artemis attribute/operation names in `CoreBrokerClient` must match
  `AddressControl`/`QueueControl`/`ActiveMQServerControl` method names (attribute = getter
  name, e.g. `messageCount`; operations like `getAddressNames`).
- Queue browsing uses JMS `QueueBrowser` with FQQN (`address::queue`) in Core mode; a JMS
  queue name maps to the anycast address of the same name — queue/address must match unless
  you send to the FQQN form.
- Do not add dependencies for things already present; keep JSON persistence in
  `config/ProfileStore` (`~/.argus/profiles.json`).
- User documentation lives in `README.md` + `docs/`; update the relevant doc when changing
  behaviour. `docs/development.md` covers build/package details.

More detail: `docs/development.md`, `docs/permissions.md`.
