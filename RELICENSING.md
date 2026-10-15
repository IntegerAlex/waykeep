# Waykeep Relicensing

## Summary

Waykeep is a fork of [OpenTracks](https://codeberg.org/OpenTracksApp/OpenTracks).
OpenTracks is licensed under the Apache License, Version 2.0. Waykeep is
distributed under the **GNU General Public License, Version 3 (GPLv3)**.

## Legal basis

### Apache License 2.0 §4(b)

> "You may add your own copyright statement to Your modifications and may
> provide additional or different license terms and conditions for use,
> reproduction, or distribution of Your modifications, or for any such
> Derivative Works as a whole, provided Your use, reproduction, and
> distribution of the Work otherwise complies with the conditions stated in
> this License."

This grants recipients of Apache-licensed code the right to distribute
derivative works under different license terms, provided the Apache 2.0
conditions (license copy, notices, change markers) are met. Those conditions
are satisfied via `LICENSE-APACHE-2.0`, `NOTICE`, and the per-release
attribution in the app's About screen.

### Apache 2.0 → GPLv3 compatibility

The Free Software Foundation and the Apache Software Foundation both confirm
that the Apache License 2.0 is compatible with the GNU General Public
License version 3:

- FSF license list: "Apache License, Version 2.0 … is a free software
  license, compatible with version 3 of the GNU GPL."
- ASF: "Apache 2 software can therefore be included in GPLv3 projects …
  The licenses are incompatible in one direction only."

Apache-licensed code may be included in GPLv3 projects; the reverse is not
true.

### Conclusion

Waykeep, as a derivative work of OpenTracks, is distributed under GPLv3. The
upstream OpenTracks code remains available under Apache 2.0. Recipients of
Waykeep under GPLv3 may also use the upstream code under Apache 2.0 terms.

## What this means

- **Past releases** of OpenTracks (and any earlier Waykeep builds) remain
  available under Apache 2.0. Relicensing does not revoke prior grants.
- **New Waykeep releases** are under GPLv3.
- **Upstream contributions** to OpenTracks remain under Apache 2.0.
- **New contributions** to Waykeep are expected under GPLv3 (see
  `CONTRIBUTING.md`).

## Dependencies

| Component | License |
|---|---|
| OpenTracks (upstream) | Apache 2.0 |
| osmdroid | Apache 2.0 |
| AndroidX | Apache 2.0 |
| Material Components for Android | Apache 2.0 |
| Inter | SIL Open Font License 1.1 |
| JetBrains Mono | SIL Open Font License 1.1 |

OFL-licensed fonts may be bundled alongside GPL software; the font files
themselves remain under the OFL and keep their own notices.

## Risks and caveats

The §4(b) argument is textually supported but has not been tested in court.
If a court were to find that §4(b) does not cover the entire collective work,
the GPLv3 grant on upstream contributions could be challenged. For maximum
legal safety, Waykeep may seek explicit consent from OpenTracks contributors
to relicense their contributions under GPLv3.

Nothing in this document is legal advice.
