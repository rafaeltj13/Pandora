# Pandora

Keep this starter small. Add modules when a real feature needs them.

- `apps/android`: native Kotlin/Compose app; UI calls a repository through a
  ViewModel. Keep networking and parsing out of composables.
- `apps/server`: Rust process startup, configuration, logging, and shutdown.
- `packages/api`: reusable API routes and response types. Inject runtime
  dependencies here when persistence or authentication is added.
- `packages/contracts`: generated OpenAPI, owned by the Rust API. Run
  `make contracts` after an API change; never edit generated JSON by hand.
- Use Cargo and the committed Gradle wrapper. Root Make commands coordinate
  them. Future web code belongs in `apps/web` and consumes the HTTP contract.
- HTTP exceptions belong only in Android debug resources. Releases require
  an explicit HTTPS API URL and separate signing configuration.
- Run `make check` for implementation changes and `make test-device` for
  changes to screen behavior. Do not claim phone verification without a device.
- Do not commit toolchain downloads, SDKs, local config, APKs, or credentials.
