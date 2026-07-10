# Governance

`cloud-itonami-iso3166-khm` is an OSS open-business blueprint. Governance covers both code and
the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the advisor cannot directly submit a portal registration/filing or
  commit a public record.
- the Market-Entry Compliance Governor remains independent of the advisor.
- a fabricated or stale regulatory-requirement claim cannot be overridden
  by human approval alone.
- every commit, hold and approval path is auditable.
- real client/bid data stays outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit, support and
data-flow review, INCLUDING proof of a working referral relationship with
Cambodian-licensed counsel or a registered agent for whatever licensed
representation the law of Cambodia requires for public-procurement
filings.

Certified operators can lose certification for:

- bypassing governor checks
- mishandling client or bid data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
- presenting an uncited claim as a legal/tax conclusion
