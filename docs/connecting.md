# Connecting

When Argus starts it shows the connection dialog.

## Fields

| Field | Meaning |
|---|---|
| Profile name | A label for saving/reusing the connection |
| Protocol | **Core** (recommended, full features) or **OpenWire** |
| Host / Port | Broker address; Artemis default port is `61616` |
| Username / Password | Broker credentials |
| Use SSL | Enables TLS (`sslEnabled=true` for Core, `ssl://` scheme for OpenWire) |
| Save password | Store the password in `~/.argus/profiles.json` (off = it is never persisted) |

Profiles are stored per user in `~/.argus/profiles.json`. Pick a saved profile from the
name dropdown to reload its values.

## Protocol choice (explicit, no auto-fallback)

- **Core**: full functionality — address/queue listing via Artemis *management messages*,
  browsing and sending via JMS over the core protocol. Listing needs `manage` permission;
  without it Argus falls back to manual queue entry.
- **OpenWire**: speaks OpenWire to Artemis. Browsing and sending work like any OpenWire
  client. Listing depends on destination advisories: the broker acceptor must have
  `supportAdvisory=true`, and previously existing destinations are only visible after
  an advisory is emitted. If listing comes up empty, type names manually — Argus will
  still browse/send them.

Choose Core unless you specifically want to exercise the OpenWire protocol the same way
your applications do.

## TLS

- Core: `tcp://host:port?sslEnabled=true` is used when *Use SSL* is checked.
- OpenWire: the `ssl://host:port` transport is used.
- Custom truststores/keystores are not yet configurable in the UI; use the JVM
  default truststore or add system properties in a launcher script as a stopgap.
