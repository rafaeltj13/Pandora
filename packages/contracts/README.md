# Pandora API contract

`openapi.json` is generated from Rust types and route annotations in
`packages/api`. Run `make contracts` after changing the API. `make check`
rejects a stale contract.

The server serves the same document at `/openapi.json`. Android currently uses
a small typed HTTP client; its JVM tests check the response model against this
schema. A future web client can generate TypeScript types or
a client from this document without depending on the Rust implementation.

Do not edit the generated JSON by hand. Keep client compatibility in mind when
changing existing `/api/v1` response fields.
