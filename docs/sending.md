# Sending messages

The **Send** tab composes and sends a message.

1. **Destination**: type the name, or select a queue in the tree first (the field is
   pre-filled). Choose *Queue* (anycast) or *Topic* (multicast).
   - If the destination already exists, Argus resolves its routing type and **locks the
     Queue/Topic choice** to the correct one (enabled again for unknown/new destinations).
   - Fully-qualified queue names (`address::queue`) resolve as *Queue*.
2. **Body**: choose *Text* or *Bytes (UTF-8)* and write the payload in the editor.
3. **Properties**, one per line, `key = value`. Values are auto-typed: `true`/`false`
   as boolean, integers, decimals, anything else as string. Lines starting with `#`
   are ignored.
4. Press **Send**. (Note: browsing queues is triggered by double-clicking them in the
   tree — the Browse tab has no button of its own.)

## Notes

- Messages are always sent as *non-persistent, default priority, no TTL*. If you need
  persistent messages, send from your application — Argus is for inspection and testing.
- Sending requires the `send` permission on the destination address; failures are
  reported verbatim from the broker.
