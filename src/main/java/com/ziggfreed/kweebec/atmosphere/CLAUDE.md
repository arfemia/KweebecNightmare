# atmosphere/

- Validate every asset id before use (`getIndex == Integer.MIN_VALUE` means missing); an unknown weather blanks the sky.
- Ambience comes from the grove biome's `Environment` (`Env_Void`); change it in the biome JSON, not Java.
