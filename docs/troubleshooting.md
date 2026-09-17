# Troubleshooting

Errors (broker, permission, connection) are shown as modal dialog boxes and mirrored in
the status bar at the bottom. Usage hints (e.g. selecting a queue before browsing) stay
in the status bar only.

## Connection

| Symptom | Cause / fix |
|---|---|
| `Connection failed: ... Connection refused` | Wrong host/port, or broker down. Check the acceptor port (usually 61616). |
| Authentication error mentioning user/roles | Wrong credentials, or the user is not known to the security manager. |
| SSL handshake errors | Broker uses TLS but *Use TLS* is unchecked, or vice versa. For custom CAs, set a truststore on the connection screen — it is shown when *Use TLS* is checked. |
| Connect works, listing fails with authorization error (e.g. `AMQ229123 ... CREATE_NON_DURABLE_QUEUE ...`) | User lacks the management permissions: `send` + `manage` on `activemq.management` (queue creation on that address is not needed; the reply queue is a plain temporary queue on its own address) — see [permissions.md](permissions.md). Known queues remain browsable via toolbar **Add address** (enter address + queue manually). |
| Connect itself fails with authorization error naming `ActiveMQ.Advisory.TempQueue`/`TempTopic` (same `AMQ229123`-style message) | OpenWire clients subscribe to destination advisories at connect; the user lacks `createNonDurableQueue`/`consume` on `ActiveMQ.Advisory.#` — grant those, fix the acceptor (`supportAdvisory=true`), or use Core with the management permissions an admin grants. |

## Listing

| Symptom | Cause / fix |
|---|---|
| OpenWire mode shows no addresses | Destination advisories: set `supportAdvisory=true` on the OpenWire acceptor, or press **Refresh addresses** after destinations change; otherwise enter names manually. |
| Queue statistics show `n/a` | Expected in OpenWire mode (no JMX-free way to read them). Use a Core connection. |
| Wrong tree after topology changes | Press **Refresh addresses**. |

## Browsing / subscribing / sending

| Symptom | Cause / fix |
|---|---|
| `Access denied` on Browse | Missing `browse` permission on that queue's address. |
| `Subscribe failed` on an address | Missing `createNonDurableQueue` or `consume` permission on that address — see [permissions.md](permissions.md). |
| Selector errors | JMS selector syntax; quote strings: `color = 'red' AND priority > 3`. |
| Send fails with unknown queue | Correct spelling; broker must accept the address (auto-create rules may block it). Subscribe likewise needs the address to exist unless auto-create is on. |
| Object message bodies show as `<serialized object>` | Intentional: deserializing arbitrary classes is unsafe. |

## Persistent problems

Run Argus from a terminal (`java -jar` / `mvn javafx:run`) to see stack traces on stdout,
and include them in a bug report together with broker version (`brokerInfo` is shown in
the toolbar after connect).
