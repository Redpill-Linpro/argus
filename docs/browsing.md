# Browsing and subscribing

## The tree

The left pane shows `broker → addresses → queues`. Addresses show their routing types
(`ANYCAST`, `MULTICAST`). **Selecting an address expands it automatically** and loads its
queues with message count and consumer count (Core mode); in OpenWire mode queue statistics
are `n/a`.

**Refresh addresses** re-queries the broker while keeping your expansion state, the loaded
queues and the current selection intact.

**Add address** opens a small dialog where you type an address (and optionally a queue) to
add to the tree manually — useful when listing is not permitted for your user or when you
already know the destination name. Manually added entries are marked `[MANUAL]`, survive
**Refresh addresses**, and an optional queue entry is directly browsable even when queue
listing is denied. These entries are display-only tree nodes; nothing is created on the
broker until you actually send/subscribe/browse.

## Browsing a queue

1. Select a queue in the tree (its label shows `name [routingType] (messages msg, consumers cons)`).
2. In the **Subscribe** tab: set *Max* (limit of messages to fetch; default 200) and
   optionally a JMS message selector (e.g. `priority > 4`).
3. Double-click the queue. Messages appear in the table (never consumed —
   browsing is read-only).

## Subscribing to a multicast address

Addresses with routing type `MULTICAST` (topics) have no browsable history of their own —
messages go straight to the bound subscriptions. To see them live:

1. Select the multicast address (or one of its queues) in the tree — only `MULTICAST`
   targets enable the **Subscribe** button.
2. Optionally enter a JMS selector.
3. Press **Subscribe** (or double-click the address). Incoming messages appear in the
   message table as they arrive; the label above shows the address with a *(live)* marker.
4. The table is capped at the value of *Max* — older rows roll off as new ones arrive.
5. Press **Unsubscribe** to stop. The subscription is non-durable: its broker-side queue
   exists only while it is open and is deleted when you stop (nothing persists, no manual
   cleanup).

Browsing and subscribing share one view: starting a subscription replaces the previous
contents (and vice versa, a queue browse replaces a subscription).

## Auto-refresh

The **Auto-refresh** toggle refreshes the message list of a *browsed* queue every 3 seconds
while it is enabled; it activates after you browse a queue and can be switched off/on at
any time. In Core mode Argus polls only the queue's `messageCount` and re-fetches the
messages when it changed; with a selector (or in OpenWire mode, where the broker exposes
no message count) it re-browses each tick. If a refresh fails (e.g. permissions dropped),
auto-refresh turns itself off and shows the error in the status bar. It is suspended while
a subscription is open — subscriptions are already live.

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
- *Subscribe failed* on an address means your user lacks `createNonDurableQueue` or
  `consume` on that address; see [permissions.md](permissions.md).
