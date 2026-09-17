# Permissions

Argus needs nothing but the permissions a normal broker user has. It never uses JMX.

## What each feature requires (Artemis security settings)

| Feature | Permission needed |
|---|---|
| Connect | valid login (username/password or certificate, per broker auth) |
| Send message | `send` on the destination address |
| Browse queue | `browse` on the queue's address (`createNonDurableQueue`/`consume` are **not** required) |
| Subscribe to multicast address | `createNonDurableQueue` + `consume` on the address |
| List addresses/queues | `send` + `manage` on the `activemq.management` address; the reply queue is a plain temporary queue (`argus-reply-…`) needing only the regular temporary-queue permissions (`createNonDurableQueue`, `deleteNonDurableQueue`, `consume`) any JMS tool requires |

Subscriptions are non-durable: the broker-side queue exists only while the subscription
is open and is deleted when it is stopped, so no `createDurableQueue`/`deleteDurableQueue`
permissions are needed.

## If listing is denied

Argus shows an **information box** at connect and on the first listing instead of an
error: *"You do not have permission to list queues on this broker"* — surfacing the
broker's message in the detail text. Broker errors such as `AMQ229123 ...
permission='CREATE_NON_DURABLE_QUEUE' ...` name the missing permission directly.

The management request itself needs `send` + `manage` on the **exact address**
`activemq.management`. The reply is collected on a plain temporary queue
(`argus-reply-<uuid>`) on its own neutral address, so no queue-creation permissions are
required on the management address — the same catch-all permissions that let tools like
JMSToolBox create JMS temporary queues apply. Note that Artemis applies the **most
specific** matching security-setting only (grants are never merged): if a specific
`activemq.management.#` block exists, it replaces the `#` catch-all for that family and
must contain the grants its users need.

Options:

1. Ask the broker admin to add the management permissions for your role:
   ```xml
   <security-setting match="activemq.management">
       <permission type="send" roles="monitoring"/>
       <permission type="manage" roles="monitoring"/>
   </security-setting>
   ```
   (Note: `manage` is a powerful permission; operators usually grant it to a monitoring
   role. Argus holds a **single reply queue per connection** (`argus-reply-<uuid>`); it
   needs only normal temporary-queue rights: `createNonDurableQueue` to create it and
   `consume` to read replies. Deleting it on disconnect is best-effort — a denied cleanup
   is ignored and the queue is removed automatically when Argus closes its broker
   session.)
2. Or just use the manual path: toolbar **Add address** lets you type an address and an
   optional queue to browse, without listing anything from the broker; you can also type
   the destination into the *Send* tab — browsing itself only needs `browse`.

## If a specific queue cannot be browsed

Queues remain visible in the tree (listing returns everything), but browsing a queue
without `browse` permission reports *Access denied* instead of hiding data.

## Related broker-side config

- OpenWire destination advisories (listing in OpenWire mode) require
  `supportAdvisory=true` on the acceptor.
- OpenWire listing also needs `createNonDurableQueue` + `consume` on
  `ActiveMQ.Advisory.#`: the client subscribes to queue, topic, temp-queue and temp-topic
  advisories when the connection starts, so denying e.g. `ActiveMQ.Advisory.TempQueue`
  fails the connect itself (`AMQ229123`/`AMQ229032` naming that address); Argus reports
  this in the information box.
- The management address is `activemq.management` by default.
