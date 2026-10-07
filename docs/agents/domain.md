# Domain Docs: Layout and Consumer Rules

## Layout

**Single-context:** This repo has one `GLOSSARY.md` and one `docs/adr/` directory, both at the repo root.

### Locations

- **Glossary**: `GLOSSARY.md` (root)
- **Architecture Decision Records**: `docs/adr/` (root)

## Consumer rules

Skills and agents reading domain docs follow these rules:

1. **Glossary first**: Always read `GLOSSARY.md` before an ADR, since terms are defined there.
2. **One glossary**: There is one shared glossary for the whole repo.
3. **ADR structure**: Each ADR is a Markdown file in `docs/adr/` with a kebab-case name (e.g., `0001-event-sourcing.md`). Use the [ADR template](https://adr.github.io/madr/) or your own format, but include a **Status** field (Proposed, Accepted, Deprecated, Superseded).
4. **Linking**: ADRs may reference each other; use relative links (e.g., `[0002](0002-caching-strategy.md)`).

## For humans

Edit `GLOSSARY.md` to define domain terms and concepts. Create ADRs in `docs/adr/` to record decisions about architecture, tech choices, and design trade-offs. The skills will read these to understand your project's language and reasoning.
