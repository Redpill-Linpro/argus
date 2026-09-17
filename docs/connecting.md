# Connecting

When Argus starts it shows the connection dialog.

## Fields

| Field | Meaning |
|---|---|
| Profile name | A label for saving/reusing the connection |
| Protocol | **Core** (recommended, full features) or **OpenWire** |
| Host / Port | Broker address; Artemis default port is `61616` |
| Username / Password | Broker credentials |
| Use TLS | Enables TLS (`sslEnabled=true` for Core, `ssl://` scheme for OpenWire) |
| Truststore / Truststore password | Optional client-side truststore shown when *Use TLS* is checked; used to verify the broker's certificate. Pick a file with *Browse...* (`.p12`, `.pfx`, `.pkcs12`, `.keystore`, `.jks`) or type a path manually |
| Keystore / Keystore password | Optional client keystore shown when *Use TLS* is checked; used for mutual TLS client certificates. Same file picker |
| Save password | Store the password in `~/.argus/profiles.json` (off = it is never persisted) |

Profiles are stored per user in `~/.argus/profiles.json`. Pick a saved profile from the
name dropdown to reload its values.

## Protocol choice (explicit, no auto-fallback)

- **Core**: full functionality — address/queue listing via Artemis *management messages*,
  browsing and sending via JMS over the core protocol. Listing needs the management
  permissions on `activemq.management` (see `docs/permissions.md`); when they are missing,
  Argus shows an information box and falls back to manual queue entry.
- **OpenWire**: speaks OpenWire to Artemis. Browsing and sending work like any OpenWire
  client. Listing depends on destination advisories: the broker acceptor must have
  `supportAdvisory=true`, and previously existing destinations are only visible after
  an advisory is emitted. If listing comes up empty, type names manually — Argus will
  still browse/send them. If the advisory subscriptions themselves are denied, the
  connect fails with an authorization error naming `ActiveMQ.Advisory.TempQueue` and
  Argus shows an information box instead of the raw error.

Choose Core unless you specifically want to exercise the OpenWire protocol the same way
your applications do.

**Exit** (or closing the dialog) quits Argus. After connecting, **Disconnect** returns to
this login screen so you can reconnect with a different profile or protocol.

## TLS

- Core: `tcp://host:port?sslEnabled=true` is used when *Use TLS* is checked.
- OpenWire: the `ssl://host:port` transport is used.
- When *Use TLS* is checked, you can point Argus at a truststore (to verify the broker
  certificate) and/or a keystore (for mutual TLS client certificates). Both accept
  PKCS12 (default) or JKS files. If left blank, the JVM default truststore/keystore
  is used.
- Store passwords are treated like the broker password: persisted only when
  *Save password* is checked.
