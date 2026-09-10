# Browsing

## The tree

The left pane shows `broker → addresses → queues`. Addresses show their routing types
(`ANYCAST`, `MULTICAST`). **Selecting an address expands it automatically** and loads its
queues with message count and consumer count (Core mode); in OpenWire mode queue statistics
are `n/a`.

**Refresh addresses** re-queries the broker while keeping your expansion state, the loaded
queues and the current selection intact.

## Browsing a queue

1. Select a queue in the tree (its label shows `name [routingType] (messages msg, consumers cons)`).
2. In the **Browse** tab: set *Max* (limit of messages to fetch; default 200) and
   optionally a JMS message selector (e.g. `priority > 4`).
3. Press **Browse**. Messages appear in the table (never consumed — browsing is read-only).

## Message details

Selecting a row shows below the table:

- **Headers**: message ID, correlation ID, timestamp, expiration, priority,
  delivery count, redelivered flag, type
- **Properties**: all user properties with their values
- **Body**:
  - `text` messages as-is (pretty JSON if you use your editor of choice)
  - `map` messages as `{key=value, ...}`
  - `bytes` messages as UTF-8 when printable, otherwise a hex dump (first 64 KB)
  - `stream` messages as a list of elements
  - `object` messages are shown as `<serialized object>` (deserialization is disabled
    for safety)

## Errors

- *Access denied* on a queue means your user lacks the `browse` permission for it —
  the queue stays visible but cannot be read; see [permissions.md](permissions.md).
