# H5 Step 4 - Live Lease/Restart Gate

## Verdict

- **Baseline e62b2f9: FAIL** - restart left the same task `ACTIVE` with no reason and no accepted completion.
- **H5 build `0.1.0+8e2eed6`: PASS** - restart converted the ACTIVE row to `PENDING` attempt 2 (`host-restart`), then worker reconnect claimed attempt 3 and produced exactly one accepted completion.
- Overall gate status: **PASS on the H5 build**, with the e62b2f9 baseline preserved as the failing before-state.

## Evidence

- Attempt 1 (baseline): `attempt1-baseline/AUDIT.md`
- Attempt 2 (H5 build): `attempt2-h5/AUDIT.md`
- Baseline task: `64e1fd2f-0c39-4976-ad2f-96ed65890880`
- H5 task: `2b7aa4f5-4f32-43df-b163-0fc13a63c60d`

## What this proves live

- A restarted H5 host does not restore an ACTIVE row.
- The recovered row is PENDING with attempt 2 and explicit `host-restart` reason in the queue.
- The reconnected worker claims a fresh attempt (3), and completion is accepted once.
- No duplicate row, no second live completion, no stranded ACTIVE row.

## What remains open

- Panel/log lease visibility: panel JSON does not expose attempt, statusReason, lastOwner, or lease state; the queue is the current source of truth for those fields.
- Full old/new protocol compatibility matrix (beyond the tested legacy token fallback).
- The completed row's progress counter remains `0/2` / `collected: 0` even though the terminal transition and both completion logs are present; this is a pre-existing progress-reporting gap, not a duplicate completion.
- The successful gate used `0.1.0+8e2eed6`, not e62b2f9: the installed e62b2f9 baseline jar does not contain the H5 recovery/lease methods. The baseline failure is evidence, not a claimed e62b2f9 pass.

## Sanitization

Committed logs have panel session-token query values replaced with `<redacted>` after the hosts were stopped. Exact unredacted originals are in the ignored local `.raw/` tree; original hashes are recorded per attempt.
