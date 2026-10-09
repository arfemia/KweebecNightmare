# interaction/

Local index of the registered block and item `RootInteraction` handlers; vanilla shapes are under `shared-source/release/HytaleAssets/Server/Item/{RootInteractions,Interactions}/`.

- Override `simulateFirstRun` to only finish the prediction; otherwise the predicted client pass double-runs `firstRun`'s inventory take and block mutation.
- Every exit path of `firstRun` sets `ctx.getState().state` inside a `try/catch(Throwable)`; a fall-through hangs the client.
- `firstRun` runs on the instance world thread, the round tick's thread, so shrine state is mutated directly with no hop.
- `shrineForBlock` is the creating lookup and `shrineAt` the read-only peek; discovery registers before the Moonbloom gate.
