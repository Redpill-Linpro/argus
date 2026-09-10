# Permissions

Argus needs nothing but the permissions a normal broker user has. It never uses JMX.

## What each feature requires (Artemis security settings)

| Feature | Permission needed |
|---|---|
| Connect | valid login (username/password or certificate, per broker auth) |
| Send message | `send` on the destination address |
| Browse queue | `browse` on the queue's address (`createNonDurableQueue`/`consume` are **not** required) |
| List addresses/queues | `manage` permission on the `activemq.management` address |

## If listing is denied

Argus shows: *"Failed to list addresses: ... Access denied ... needs 'manage' permission
on activemq.management; you can still browse by entering a queue name manually."*

Options:

1. Ask the broker admin to add `manage` on `activemq.management` for your role:
   ```xml
   <security-setting match="activemq.management">
       <permission type="manage" roles="monitoring"/>
   </security-setting>
   ```
   (Note: `manage` is a powerful permission; operators usually grant it to a monitoring role.)
2. Or just use the manual path: type the queue name in *Send* destination, or a known
   queue name via selector/browse — browsing itself only needs `browse`.

## If a specific queue cannot be browsed

Queues remain visible in the tree (listing returns everything), but browsing a queue
without `browse` permission reports *Access denied* instead of hiding data.

## Related broker-side config

- OpenWire destination advisories (listing in OpenWire mode) require
  `supportAdvisory=true` on the acceptor.
- The management address is `activemq.management` by default.
