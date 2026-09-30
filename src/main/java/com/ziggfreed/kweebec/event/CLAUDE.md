# event/

- The MMO counts rounds through `RoundEvents.fireInstanceCompleted` (zc `InstanceRoundCompletedEvent`), fired right after `fireRoundCompleted` at both `RoundService.resolve` call sites. Keep the mod id `kweebec` and the mode and preset ids stable.
- Outbound events fire from a world-thread context behind `hasListener()`.
- `KweebecDamageSystem` is the one damage observer in the filter group and never initiates damage. Identify a thrown-item hit by its custom `DamageCause` id, not by the damage source.
- `MoonbloomCollectSystem` never cancels the pickup and is inert outside a round.
