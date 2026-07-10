# Security Policy

This project handles public-sector market-entry and procurement-
compliance workflows. Treat vulnerabilities as potentially high impact
even when the demo data is synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real client, business-registration or bid data exposure
- authorization bypass
- Market-Entry Compliance Governor bypass
- audit-ledger tampering
- a path that lets a filing/registration submit without human sign-off

## Reporting

Use GitHub private vulnerability reporting when available for the
repository. If that is unavailable, contact the repository maintainers
through the cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on public data, governor enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real client/bid data outside this repository.
- Run governor tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
