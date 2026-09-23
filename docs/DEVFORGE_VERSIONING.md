# DevForge API, Package and Event Versioning

## Platform API

The public API uses MAJOR.MINOR.

- Major changes can break existing packages.
- Minor changes add backward-compatible capabilities.
- Internal bug fixes do not change the public API version.

Current stable API: 1.0.

Marketplace production packages target a stable API major line. A package using ^1.0 accepts compatible DevForge API 1.x releases under the compatibility policy.

## Package versions

Packages use SemVer MAJOR.MINOR.PATCH.

- PATCH: fixes and security corrections.
- MINOR: backward-compatible capabilities.
- MAJOR: incompatible package behavior.

Published package versions are immutable.

## Event schemas

Every stable event has a stable name plus integer schema version.

Removing a field, changing its type, or changing its meaning requires a new event schema version.

## Deprecation

Stable APIs follow:

stable -> deprecated -> migration guidance -> removal in next API major

Experimental APIs are not production marketplace dependencies until promoted to stable.
