---
name: definition-of-done
description: Verify Sports implementation work before declaring it complete, including affected checks, builds, documentation, and cross-platform stream parity.
---

# Definition of Done

Apply this gate after implementation and before reporting the work complete. Scope it
to the files and behavior changed; user instructions remain authoritative.

## Complete the change

1. Inspect the final diff and account for every changed file. Remove debugging,
   unrelated edits, generated files, captured provider data, credentials, and tokens.
   Run `git diff --check`.
2. Run the existing formatting, lint, type-check, test, and build commands for every
   affected workspace. Use the commands named by its `AGENTS.md`, README, or package
   scripts rather than duplicating their internals. A check command counts only for
   the tasks it actually runs. Prefer `npm run test:ai` for compact test-only feedback;
   still run the applicable full check commands before completion.
3. Compile an affected native shell after syncing generated web assets when its guide
   requires that step. A web bundle alone does not prove native shell compilation.
4. Update the authoritative current-state documentation when behavior, architecture,
   setup, configuration, player support, or contributor procedure changed. Keep
   platform-specific details in that platform and cross-platform knowledge in `docs/`.
5. Re-read the user request and verify each requested outcome against the resulting
   behavior. Report failed, skipped, unavailable, and device-only verification
   separately from checks that passed.

Documentation-only changes require link and command verification, not application
builds. Reversible content or artwork edits need checks proportional to their actual
impact.

## Stream-support gate

Apply this additional gate to listing, discovery, traversal, decoder, HTTP, header,
cookie, stream-selection, renewal, recovery, or playback changes.

1. Read [`docs/player.md`](../../../docs/player.md) and identify the exact channel and
   stream selection being changed.
2. Add playlist extraction shapes to
   [`contracts/streams/playlist-cases.json`](../../../contracts/streams/playlist-cases.json)
   so both engines execute the exact same input and expectation. For traversal,
   selection, or request behavior, add equivalent sanitized regressions in both cores
   that prove exact selection, request order, required headers, and expected result.
   Keep captured HTML, signed URLs, cookies, and real tokens outside the repository.
3. Exercise the same input and expectation in the shared TypeScript core and Android
   Kotlin core wherever the behavior is duplicated. Confirm the regression fails
   before the fix and passes afterward. A platform-specific exception needs concrete
   runtime evidence and must be called out in the completion report.
4. Run both JavaScript and Android check suites, even when the initial failure was seen
   on only one platform:

   ```sh
   npm run check
   make -C android check
   ```

5. When the provider is reachable, verify the exact selection against fresh data as
   described in `docs/player.md`. Distinguish successful resolution and media probing
   from sustained native playback.

## Deployment

Building is part of this gate. Installing, launching, publishing, signing, notarizing,
and deploying are separate actions. Perform them only when the task explicitly calls
for them; their absence does not make an implementation incomplete.

The work is done when every applicable item above has evidence, all required checks
pass, and any remaining external or device verification is stated precisely.
