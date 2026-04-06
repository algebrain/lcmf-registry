# lcmf-registry

Thin read-provider registry for [`LCMF`](https://github.com/algebrain/lcmf-docs).

The library gives:

- provider registration
- required provider declarations
- requirement validation
- fail-fast startup assertion

Public API:

- `make-registry`
- `register-provider!`
- `resolve-provider`
- `require-provider`
- `declare-requirements!`
- `validate-requirements`
- `assert-requirements!`
