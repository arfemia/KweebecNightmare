# camera/ - server-driven camera override (survival, reserved)

Router for `camera/`. Built for the reserved survival "Last Light Till Dawn" top-down mode (the demo flex); not wired into the Chase MVP yet. Packet-only, so safe to call without a Store (only the `PlayerRef` acquisition is world-thread).

- **[`ServerCameraService`](ServerCameraService.java) sends `SetServerCamera` (packet 280)** via `playerRef.getPacketHandler().writeNoCache` (the same transport `CameraShakeService` uses; `writeNoCache` is thread-safe). `topdown` mirrors the native `PlayerCameraTopdownCommand` field-for-field (`Custom` view, locked, `distance 20`, `pitch -PI/2`, `LookAtPlane` on the up-normal); `reset` mirrors `PlayerCameraResetCommand` (`Custom`, unlocked, null settings). No FOV / orthographic knob exists.
- **ALWAYS `reset` on death / disconnect / round-end** or the player is stranded in the locked camera. When survival mode lands, wire `reset` into the same teardown seams the HUD uses (`RoundService.teardown`/`exit`).
