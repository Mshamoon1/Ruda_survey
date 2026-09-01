# PARCEL DERIVATION RULES (PHASE 3 — AUTHORITATIVE)

**Module:** `backend/surveys/services/parcel_derivation.py`
**Basis:** doc 02 §C3 (approved strategy) + §G open questions; Phase 1 audit facts.

---

## 1. Canonical Parcel Code format

```
parcel_code = RUDA-P14-R{first_source_row:05d}      e.g. RUDA-P14-R00005
```

| Requirement | How it is met |
|---|---|
| deterministic / reproducible | pure function of the group's first physical Excel row |
| unique | groups partition source rows 5–14876; anchors cannot collide |
| stable across re-imports | same file ⇒ identical grouping ⇒ identical codes (proven by tests) |
| traceable | the code *is* the provenance pointer to the first Excel row |
| independent of DB IDs | no autoincrement/UUID anywhere in the code |
| not raw NID | NID kept separately as `parcels.source_nid` |

## 2. Grouping algorithm (strict source order, rows 5 → 14876)

State: current block = {NID, owner_key, village_key, rows…}

1. **Hard boundary** — row's non-null `NID` ≠ block NID ⇒ close block, start new one.
2. **Continuation** — row with NULL NID attaches to the current block
   (Phase 1 proved every NULL-NID run follows an NID'd row: 0 orphans).
   Owner/village drift inside a continuation produces WARNINGS only
   (`CONTINUATION_OWNER_DRIFT`, `VILLAGE_DRIFT`) — never a split.
3. **Owner split** — within a block sharing one NID, a change of the normalised
   owner key closes the block and starts a new parcel (doc 02 §C3.3:
   "polluted NID blocks are split per owner-group"). BOTH resulting parcels are
   classified `AMBIGUOUS` and carry `SPLIT_FROM_MULTI_OWNER_NID` warnings.
4. **Village drift** inside a same-NID block warns but does NOT split (not a
   documented split signal).
5. Unidentified owners (`Not Identified`, `Owner not Identified`, `(Locked)`,
   `Not interested`, …) get owner_key = None and NEVER drive a split.

## 3. Owner-key normalisation (grouping ONLY — never stored)

lowercase · punctuation→space · collapse whitespace · strip leading honorifics
(`mr, mrs, ms, dr, haji`). This unifies trivial variants (`Mr.Arshad Ali` ≡
`arshad ali`) while remaining conservative: any other difference SPLITS.
The system therefore risks cosmetic over-splitting but can never merge two
distinct parcels — matching the brief's prime directive.

## 4. Ambiguity policy

* Multi-owner NIDs are split as above and reported individually.
* No case is silently merged. No parcel_code is ever invented outside this rule.
* If a future input violates a precondition (e.g. duplicate source-row anchor,
  orphan NULL-NID first row), the importer fails that import safely rather
  than guessing.

## 5. Observed outcome on the real master (batch #2)

| Metric | Value |
|---|---|
| Candidate parcels derived | **4,654** |
| Distinct non-null NIDs feeding them | 4,536 |
| Parcels created from multi-owner splits | 98 flagged AMBIGUOUS |
| NIDs whose blocks were split | 32 distinct NIDs |
| Largest split family | e.g. `RUDA-P14-R00046/R00047`, `R01333/R01334`, … |
| Continuation NULL-NID rows absorbed | 2,282 across their parent parcels |

## 6. Open items carried to the data owner (unchanged from doc 02 §G)

1. Confirm WB2 `Survey Sheet Parcel ID` format once real filled sheets exist —
   if field IDs must REPLACE these canonical codes, mapping is trivial via
   `source_nid` + row anchors.
2. Ratify the NID-606-style split policy applied here (32 NIDs / 98 parcels).
3. Chainage unit label mismatch (values look like metres/RD) — stored as
   `chainage_m` per doc 02 Part D note.
