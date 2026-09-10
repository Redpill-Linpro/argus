# Installation

Argus is a desktop application for Windows, macOS and Linux.

## Requirements

- A broker account on ActiveMQ Artemis with (at minimum) `browse` permission for the
  queues you want to read and `send` permission for the addresses you want to write to.
  Listing addresses additionally requires `manage` permission on `activemq.management`
  — see [permissions.md](permissions.md).
- Network access to the broker's core/OpenWire port (typically `61616`, plus TLS variant).

## Run from source (any platform)

Requires Java 21+ and Maven:

```bash
mvn javafx:run
```

## Native installers

Installers are built with `jpackage`:

- **Windows**: `.\scripts\package.ps1 -Type msi` (WiX required when building locally)
- **macOS**: `./scripts/package.sh dmg`
- **Linux**: `./scripts/package.sh deb` (or `rpm`, or `app-image` for a portable folder)

Push builds produce all three installers automatically via GitHub Actions
(`.github/workflows/build.yml`, artifacts `argus-deb`, `argus-dmg`, `argus-msi`).
The installers bundle a private JRE — no Java installation is needed on the target machine.
