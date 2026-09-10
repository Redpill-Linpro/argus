# Troubleshooting

Errors (broker, permission, connection) are shown as modal dialog boxes and mirrored in
the status bar at the bottom. Usage hints (e.g. selecting a queue before browsing) stay
in the status bar only.

## Connection

| Symptom | Cause / fix |
|---|---|
| `Connection failed: ... Connection refused` | Wrong host/port, or broker down. Check the acceptor port (usually 61616). |
| Authentication error mentioning user/roles | Wrong credentials, or the user is not known to the security manager. |
| SSL handshake errors | Broker uses TLS but *Use SSL* is unchecked, certificates untrusted, or vice versa. For custom CAs, import into the Java truststore used to launch Argus. |
| Connect works, listing fails with authorization error | User lacks `manage` on `activemq.management` — see [permissions.md](permissions.md). Browsing a known queue name still works. |

## Listing

| Symptom | Cause / fix |
|---|---|
| OpenWire mode shows no addresses | Destination advisories: set `supportAdvisory=true` on the OpenWire acceptor, or press **Refresh addresses** after destinations change; otherwise enter names manually. |
| Queue statistics show `n/a` | Expected in OpenWire mode (no JMX-free way to read them). Use a Core connection. |
| Wrong tree after topology changes | Press **Refresh addresses**. |

## Browsing / sending

| Symptom | Cause / fix |
|---|---|
| `Access denied` on Browse | Missing `browse` permission on that queue's address. |
| Selector errors | JMS selector syntax; quote strings: `color = 'red' AND priority > 3`. |
| Send fails with unknown queue | Correct spelling; broker must accept the address (auto-create rules may block it). |
| Object message bodies show as `<serialized object>` | Intentional: deserializing arbitrary classes is unsafe. |

## Persistent problems

Run Argus from a terminal (`java -jar` / `mvn javafx:run`) to see stack traces on stdout,
and include them in a bug report together with broker version (`brokerInfo` is shown in
the toolbar after connect).
