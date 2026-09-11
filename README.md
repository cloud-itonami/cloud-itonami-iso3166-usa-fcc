# cloud-itonami-iso3166-usa-fcc

Open ISO 3166 **agency-level** Blueprint for **USA-FCC**: Federal Communications Commission
(parent country: **USA**).

This leaf designs a forkable OSS business for an independent operator
navigating **Federal Communications Commission**-specific licensing, equipment-authorization
and universal-service (USF / E-Rate) compliance, composing with the country coordinator
`cloud-itonami-iso3166-usa`.

## What this is NOT

- **Not Federal Communications Commission.** Commercial compliance navigation only.
- **Not legal advice.** Cite official sources; route licensed work to counsel.

## The finding this leaf is built on

**The FCC is not an agency you sell to under agency-specific rules. It is an agency
that grants you permission to operate, and a designator whose output other people's
rules consume.** 3 checked negatives, re-confirmed against the live eCFR API on
every gate run rather than asserted here in prose:

| Checked negative | Why it matters |
|---|---|
| No chapter of **48 CFR** is the FCC's, and the Commission is not named anywhere in the Federal Acquisition Regulations System | EPA has chapter 15, VA chapter 8, the NRC chapter 20, the NSF chapter 25. A firm selling *to the FCC as an agency* is on the bare FAR, with no agency supplement to learn. |
| The word **`procurement` does not occur** in any node label in 47 CFR chapter I | What does occur, in dozens of node labels, is `competitive bidding` — and it never means government purchasing. Note that `acquisition` occurs once, at 47 CFR 1.763, where it means one carrier acquiring another's lines: a keyword search finds a hit and it is the wrong hit. |
| **47 CFR part 21 no longer exists** | Still cited in older microwave filings. Contrast part 94, which survives as an explicit `Part 94 [Reserved]` placeholder — *absent* and *reserved* are different states, and only a structural check tells them apart. Live rules: part 101. |

Consequences a client pays to be told early:

- **`competitive bidding` means three different transactions with three different
  payers.** 47 CFR part 1 subpart Q is a spectrum auction (the bidder pays the
  United States); subpart AA is a universal-service reverse auction (the government
  pays the bidder); 54.503 is an E-Rate applicant's own solicitation. Mapping any of
  them onto FAR part 15 inverts who pays whom.
- **E-Rate money is federal; E-Rate procurement is not.** 47 CFR 54.503(b), quoted
  byte-exactly in the catalog, says the FCC's bid requirements `apply in addition to
  state and local competitive bid requirements and are not intended to preempt` them.
  The FAR does not apply, and no FCC purchasing rule applies either, because there is
  none. Deals are won on a school district's purchasing code. 54.503(a) separately
  bars the vendor from preparing the applicant's Form 470 or evaluating bids — the
  ordinary pre-sales motion of helping a customer write requirements is itself a
  violation here.
- **The FCC does not, in the ordinary case, certify equipment.** 47 CFR 2.907(a)
  defines Certification as an authorization `approved by the Commission or issued by
  a Telecommunication Certification Body (TCB)`; in practice a private, ISO/IEC
  17065-accredited body issues it. Under Supplier's Declaration of Conformity (2.906)
  no third party grants anything at all. `FCC-approved` usually describes a process
  the Commission never saw.
- **Two supply-chain lists, two owners, two triggers.** The FCC maintains the Covered
  List (47 CFR 1.50002), which governs equipment authorization and FCC fund spending.
  Federal *procurement* is barred by FAR 4.2102, whose definitions at 4.2101 never
  refer to the Commission's list. Clearing one says nothing about the other.
- **Two exclusion regimes.** 47 CFR 54.8 debarment is scoped to the universal-service
  mechanisms; FAR subpart 9.4 and 2 CFR part 180 are government-wide. A firm can be
  clear in SAM and debarred at the FCC, or the reverse.
- **A USAC denial is not an interpretation of the rules.** 47 CFR 54.702(c): `The
  Administrator may not make policy, interpret unclear provisions of the statute or
  rules, or interpret the intent of Congress.` Where an answer turned on what a rule
  *means*, that is a reviewable defect — route via part 54 subpart I.

> **Open tension, recorded rather than smoothed over.** `blueprint.edn` still names
> this leaf a *Telecom-Procurement Compliance Service*. The catalog above shows that
> framing is at best partial: the procurement surface here is the ordinary FAR, and
> the FCC-specific surface is licensing, equipment authorization and universal
> service. The blueprint name is left unchanged pending an owner decision, because
> fleet consumers key on it.

## Catalog

`src/statute/facts.cljk` — 44 verified regulatory anchors across 47 CFR (FCC) and
48 CFR (FAR), plus 3 checked negatives, each of which also names a further verified
anchor to read instead: 47 headings in all. Every one records the byte-exact
`label_description` returned by the eCFR versioner structure API; 8 entries also
pin a byte-exact span of live **section text**, because a heading survives the repeal
of the sentence underneath it.

Entries are grouped by which FCC role they belong to (`:statute/hat`) — conflating
these is the failure the catalog exists to prevent:

| hat | meaning |
|---|---|
| `:licensor` | the FCC granting a firm authority to operate |
| `:authorizer` | equipment authorization, largely delegated to private TCBs |
| `:fund` | universal service: FCC writes the rules, USAC administers, the applicant procures |
| `:designator` | FCC output that other people's regimes consume |
| `:far-baseline` | title 48: what actually governs selling to the U.S. government |

```clojure
(require '[statute.facts :as f])
(f/by-hat :fund)          ; universal-service anchors
(f/by-topic :supply-chain)
(f/quoted-entries)        ; the entries that pin live section text
(println (f/summary))
```

## Verification

```bash
kbb -M:test                        ; offline invariants (no network)
kbb -M:lint                        ; clj-kondo
kbb --backend sci tools/verify_citations.cljk        ; live gate — re-fetches the eCFR APIs
```

The live gate exits **0** verified / **1** drifted / **2** could-not-answer. Those are
three outcomes on purpose: *nothing was checked* and *nothing was wrong* must not share
an exit code. It refuses to report a pass when the catalog fell below its floors, when a
title has no declared endpoint, when a wildcard resolves ambiguously, or when an
absence's **control pattern** stops matching — a label scan of an empty or wrong tree
finds nothing, which is indistinguishable from `confirmed still absent` unless something
must also be found.

Do not `curl` a `:statute/url` and treat HTTP 200 as confirmation. Automated clients
fetching `www.ecfr.gov` can receive a `Federal Register :: Request Access` interstitial
with status 200 — a status check there would report success while proving nothing. The
gate verifies through the documented machine APIs and records both addresses.

## Official surface

- https://www.fcc.gov/

## Capability layer

Resolves via `kotoba-lang/iso3166` (`USA-FCC`, parent `USA`).

## License

AGPL-3.0-or-later.
