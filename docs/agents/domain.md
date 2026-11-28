# Domain Docs: Layout and Consumer Rules

## Layout

**Single-context:** This repo has one `GLOSSARY.md` at the repo root and one decision-record file, `docs/architecture/02-decision-records.md`.

### Locations

- **Glossary**: `GLOSSARY.md` (root)
- **Architecture Decision Records**: one file, `docs/architecture/02-decision-records.md`

## Consumer rules

Skills and agents reading domain docs follow these rules:

1. **Glossary first**: Always read `GLOSSARY.md` before an ADR, since terms are defined there.
2. **One glossary**: There is one shared glossary for the whole repo.
3. **Decision format**: Each decision is a `### DR-NN <title>` section in that file, numbered in sequence, in the format stated at its top.
4. **Linking**: Cite a decision by its number (e.g. DR-21).

## For humans

Edit `GLOSSARY.md` to define domain terms and concepts. Add a DR-NN section to `docs/architecture/02-decision-records.md` to record decisions about architecture, tech choices, and design trade-offs. The skills will read these to understand your project's language and reasoning.
