# Where to publish artifacts

Research for [#18](https://github.com/sequeless/sequeless-prototype/issues/18) ("Where to publish artifacts"), a child of map issue [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (CQRS repo decomposition).

## 1. The question

The CQRS decomposition splits the prototype into separate repos — `sequeless-filter`, `sequeless-ontology`, a planned `sequeless-query`, and presumably future kernel/platform repos — all under the `sequeless` GitHub org, publishing Java artifacts under groupId `org.sequeless`. These repos need to depend on each other's Maven artifacts:

- in CI (each repo's pipeline needs to resolve the others' jars without manual `mvn install`), and
- locally (a developer working across repos needs the same resolution to just work).

Today neither repo publishes anywhere: both use `.releaserc.json` with `@semantic-release/commit-analyzer`, `@semantic-release/release-notes-generator`, `@semantic-release/changelog`, `@semantic-release/exec` (used only to run `./mvnw versions:set`), `@semantic-release/github`, and `@semantic-release/git`. Neither `pom.xml` has a `distributionManagement` block, and `release.yml` never runs `mvn deploy`. Picking a registry means deciding what to bolt onto that existing pipeline.

## 2. Comparison

### Consumer auth needs (can a public artifact be fetched anonymously?)

| Registry | Anonymous read? | Source |
|---|---|---|
| GitHub Packages (Maven) | **No** — a token is required even for public packages | GitHub's own docs: "You need an access token to publish, install, and delete private, internal, and public packages." And for cross-repo installs in Actions: "A personal access token (classic) with at least `read:packages` scope to install packages associated with other private repositories (`GITHUB_TOKEN` can be used if the repository is granted read access to the package)." — [docs.github.com: Working with the Apache Maven registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry), "Authenticating to GitHub Packages" section |
| Maven Central (Central Portal) | **Yes** — Central is a public, unauthenticated read repository by design; this is the whole point of Central and is implicit in every `mvnrepository.com`/`central.sonatype.com` artifact page and the public `https://repo.maven.apache.org/maven2/` tree. No login or token is needed to resolve a release artifact. | [central.sonatype.org/publish](https://central.sonatype.org/publish/publish-guide/) (general publishing guide; Central's raison d'être is anonymous public resolution — not separately asserted as a single sentence in the docs, so treat this as "well-established, not independently footnoted" rather than unverifiable) |
| JitPack | **Yes**, for public GitHub repos — no token needed to consume | [docs.jitpack.io](https://docs.jitpack.io/): consumers reference `com.github.<User>:<Repo>:<tag>` directly; private repos are the only case needing a token ([docs.jitpack.io/private](https://docs.jitpack.io/private)) |

This confirms the issue's framing exactly: GitHub Packages' Maven registry is the one candidate where even a public, open-source artifact requires a PAT (or an org-granted `GITHUB_TOKEN`) just to read — a real friction point for any future external consumer, and extra setup (a PAT stored as a secret, `settings.xml` with credentials) for internal CI too.

### Namespace verification for `org.sequeless`

Only Maven Central requires this; GitHub Packages and JitPack have no groupId namespace-ownership concept (GitHub Packages scopes by repo/org, JitPack mints the groupId from the GitHub username/org, e.g. `com.github.sequeless`, so `org.sequeless` wouldn't even be the resulting coordinate there — see §3).

- Process: create a Central Portal account, then under "View Namespaces" → "Add Namespace," request the namespace. It starts **Unverified**. [central.sonatype.org/register/namespace](https://central.sonatype.org/register/namespace/)
- Domain-based namespaces (reverse-domain groupIds like `org.sequeless`) are verified by DNS: "You can prove domain ownership by adding a DNS TXT record with a value set to the Verification Key we assigned to your namespace request." The check is against the exact domain, so `org.sequeless` verifies against `sequeless.org` specifically. [central.sonatype.org/register/namespace](https://central.sonatype.org/register/namespace/)
- Turnaround: "If you have set up your DNS TXT record correctly, it should only take a few minutes for us to verify your namespace." If it drags past a few hours, the docs direct you to email `central-support@sonatype.com`. [central.sonatype.org/register/namespace](https://central.sonatype.org/register/namespace/)
- Since the org controls `sequeless.org`, this is a one-time, low-effort step (add a TXT record via whatever DNS provider hosts the domain) — not a blocker in the normal case, but it is an external dependency with a (small) failure mode, which is why a fallback matters (§4).

### SNAPSHOT support

| Registry | SNAPSHOT support | Source |
|---|---|---|
| GitHub Packages (Maven) | Yes — Maven registry supports `-SNAPSHOT` versions like any Maven repo (standard `mvn deploy` with a SNAPSHOT version works against the GitHub Packages Maven endpoint); no retention-limit documentation found for this specifically, flagged below | [docs.github.com: Apache Maven registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry) |
| Maven Central (Central Portal) | Yes, but **not** via the main release repository — Central runs a **separate** snapshots repository, `https://central.sonatype.com/repository/maven-snapshots/`, that consumers must add explicitly. "Because publishing `-SNAPSHOT` releases does not perform validation on the component(s), a `mvn deploy`-compatible API was chosen for maximum ecosystem compatibility," and critically, **SNAPSHOTs expire**: "`-SNAPSHOT` releases are cleaned up after a period of time (currently 90 days)." As with legacy OSSRH, "no validation is performed" on snapshots. [central.sonatype.org/publish/publish-portal-snapshots](https://central.sonatype.org/publish/publish-portal-snapshots/) | 
| JitPack | Yes, and more flexibly — no explicit "SNAPSHOT" deploy step needed at all: request `branch-SNAPSHOT` (e.g. `main-SNAPSHOT`) and JitPack builds the latest commit on that branch on demand; a specific commit hash also works as a version. Gradle/Maven consumers need `--refresh-dependencies` or `resolutionStrategy.cacheChangingModulesFor 0` to avoid stale caching. [docs.jitpack.io](https://docs.jitpack.io/) | 

This is the sharpest gotcha the issue called out: the old assumption "Central = OSSRH behavior" is wrong under the Central Portal. SNAPSHOTs exist but live in a different repo URL and self-delete after 90 days — fine for active development, but it's a second endpoint to configure (`distributionManagement` + a second `<repository>` in consumer `pom.xml`/`settings.xml`), not the same single endpoint as releases.

### Signing requirements (GPG/PGP)

| Registry | Signing required? | Source |
|---|---|---|
| GitHub Packages (Maven) | No GPG signing requirement documented for Maven registry artifacts | [docs.github.com: Apache Maven registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry) (no signing section present) |
| Maven Central (Central Portal) | **Yes, for releases** — "All files deployed need to be signed with GPG/PGP and a `.asc` file containing the signature" for each file; checksum files and `.asc` files have independent requirements (`.asc` files don't need their own checksums). Snapshots are explicitly exempt from validation (so, no signing requirement there). [central.sonatype.org/publish/requirements](https://central.sonatype.org/publish/requirements/) |
| JitPack | No signing requirement documented | [docs.jitpack.io](https://docs.jitpack.io/) |

Central also requires POM metadata beyond coordinates — `name`, `description`, `url`, license, and developer info (name/email/org), plus SCM connection info (the SCM URL itself need not be public). [central.sonatype.org/publish/requirements](https://central.sonatype.org/publish/requirements/)

GPG signing means generating/storing a PGP key pair as CI secrets (private key + passphrase) and invoking `maven-gpg-plugin` during `mvn deploy` — real, one-time setup cost, but well-trodden (`maven-gpg-plugin` + `nexus-staging`/Central Publishing Maven plugin is the standard recipe).

### semantic-release integration

- No dedicated, actively-maintained `@semantic-release/maven` plugin exists in the mainstream semantic-release ecosystem for registry publishing. Searches turn up smaller/narrower community plugins (e.g. `@terrestris/maven-semantic-release`, `@conveyal/maven-semantic-release`) rather than one canonical, broadly-adopted plugin.
- [`@terrestris/maven-semantic-release`](https://github.com/terrestris/maven-semantic-release) is a real semantic-release plugin: it bumps `pom.xml` version and runs `mvn deploy` (or `jib:build`), with options like `mavenTarget`, `processAllModules`, `updateSnapshotVersion`, `settingsPath`. Its maintenance cadence/last-release date could not be confirmed from the fetched page (flagged below) — treat it as a possible alternative to evaluate, not a given.
- The realistic, lowest-risk path — and the one the issue itself anticipates — is what's already in this project's pipeline: `@semantic-release/exec`'s `publishCmd` (and/or `prepareCmd`) invoking `mvn deploy -P<profile> --settings .mvn/ci-settings.xml` directly, after `@semantic-release/exec`'s existing `prepareCmd` sets the version via `./mvnw versions:set`. `@semantic-release/exec` is designed exactly for this: run arbitrary shell commands at `verifyConditions`/`prepare`/`publish` lifecycle steps. [github.com/semantic-release/exec](https://github.com/semantic-release/exec)
- This choice is registry-agnostic: the same `mvn deploy` mechanism works whether the target is GitHub Packages, Central Portal, or (with a build plugin, not `deploy`) JitPack. JitPack specifically does **not** want you to push to it — it builds directly from the GitHub repo's tags/commits, so there's no `mvn deploy` step for JitPack at all; "publishing" there means just cutting the semantic-release GitHub tag (`@semantic-release/github` already does this) and letting JitPack build on first request.

### CI setup effort

| Registry | Secrets needed | `settings.xml` / config | GitHub Actions auth |
|---|---|---|---|
| GitHub Packages | None extra if same-repo publish (`GITHUB_TOKEN` suffices to publish); a PAT with `read:packages` as an org/repo secret for every *consumer* repo that installs cross-repo | `settings.xml` server block pointing at `https://maven.pkg.github.com/sequeless/<repo>` with the token | `GITHUB_TOKEN` works for publishing to the *same* repo's package; cross-repo reads need a PAT unless the package is explicitly granted read access to the consuming repo. [docs.github.com: Apache Maven registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry) |
| Maven Central (Central Portal) | Central Portal publishing token (username/password-style token pair) + GPG private key + passphrase, all as repo/org secrets | `settings.xml` server block for the Central Portal publishing endpoint, plus `maven-gpg-plugin` config; snapshots need a *second* `<repository>`/`<distributionManagement>` entry for the snapshots URL | No special GitHub Actions integration; it's generic Maven + secrets |
| JitPack | None | None — no publish step; consumers add JitPack's repo URL (`https://jitpack.io`) to their own `pom.xml`/`settings.xml` | None — publishing is replaced by "push a tag" |

GitHub Packages and Central both need genuine one-time CI plumbing (secrets + settings.xml + a deploy profile); JitPack needs essentially none on the publishing side, pushing all the complexity to a documented but slightly unusual consumer-side repository URL.

### Cost

- **GitHub Packages**: "GitHub Packages usage is free for public packages. In addition, data transferred in from any source is free." [docs.github.com: About billing for GitHub Packages](https://docs.github.com/en/billing/managing-billing-for-your-products/about-billing-for-github-packages) No catch found for public repos specifically.
- **Maven Central**: Free for open-source publishing (no fee is charged by Sonatype for Central Portal publishing); this is standard knowledge in the ecosystem but the specific "free" sentence was not independently located on a fetched page in this pass — flagged below.
- **JitPack**: Free for public GitHub repositories; paid tiers exist for private repos / higher build priority, per [jitpack.io](https://jitpack.io/) pricing page (not independently re-verified in this pass — flagged below).

### Immutability / deletion rules

| Registry | Can a version be deleted/overwritten? | Source |
|---|---|---|
| GitHub Packages | A whole public package, or a specific public version, **can** be deleted, but only if it has **5,000 or fewer downloads**: "An entire public package, if there's not more than 5000 downloads of any version of the package" / "A specific version of a public package, if the package version doesn't have more than 5,000 downloads." Deleted packages are recoverable for 30 days ("You restore the package within 30 days of its deletion," provided "the same package namespace is still available and not used for a new package"). Deletion needs admin access to the repo (repo-scoped) or to the package (org/user-scoped). GitHub's own docs warn "you may break projects that depend on your package." [docs.github.com: Deleting and restoring a package](https://docs.github.com/en/packages/learn-github-packages/deleting-and-restoring-a-package) |
| Maven Central | Could not independently confirm exact wording in this pass — the FAQ page referenced by Sonatype's own requirements doc ("Can I change, modify, delete, remove, or update a component on Central?") returned 404 when fetched directly. Ecosystem consensus (and the reason Central is trusted for builds) is that **published releases are permanent and immutable** — Sonatype does not allow overwriting an already-published release coordinate, and deletion is only possible in narrow cases via a support ticket. **Flagged: not verified against a live primary source in this pass; treat as directionally correct but re-check before relying on it.** |
| JitPack | "Public repository artifacts on JitPack are immutable after 7 days of publishing... Within the first 7 days they can be re-built to fix any release issues... Even then we recommend creating a patch release instead." JitPack also keeps serving artifacts after the source repo is deleted. [docs.jitpack.io/faq](https://docs.jitpack.io/faq/) |

## 3. Recommendation

**Publish releases to Maven Central via the Sonatype Central Portal, using the existing `@semantic-release/exec` step to run `mvn deploy` with a GPG-signing profile; keep SNAPSHOTs flowing to Central's separate snapshots repository for inter-repo CI/local dev during active development.**

Reasoning, tied to this project's specifics:

- **Consumer auth is the deciding factor.** The whole point of splitting into `sequeless-filter` / `sequeless-ontology` / `sequeless-query` is that each repo's CI and any developer's local Maven build can resolve the others without manual steps. GitHub Packages fails this immediately — every consumer, including this org's own CI jobs reading a *different* repo's package, needs a PAT with `read:packages` ([docs.github.com](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)), which means provisioning and rotating a cross-repo secret in every consuming workflow and every contributor's `~/.m2/settings.xml`. Central and JitPack need nothing to read.
- **The org already owns `sequeless.org`**, so Central's namespace verification for `org.sequeless` is a single DNS TXT record and a few minutes' wait in the normal case ([central.sonatype.org/register/namespace](https://central.sonatype.org/register/namespace/)) — not meaningfully harder than any other one-time setup task, and it buys a permanent, anonymously-resolvable, industry-standard home for the groupId the project has already chosen.
- **SNAPSHOT support is real on Central**, just via a second repository URL and a 90-day expiry ([central.sonatype.org/publish/publish-portal-snapshots](https://central.sonatype.org/publish/publish-portal-snapshots/)) — adequate for "repo A depends on repo B's in-progress work in CI," which is the active-development use case named in the issue. Add the snapshots repo to each consumer's `pom.xml`/`settings.xml` alongside the normal Central resolution (which is enabled by default and needs no explicit opt-in for releases).
- **GPG signing and the richer POM metadata Central requires are one-time setup costs**, not recurring ones: generate an org-level key pair, store the private key + passphrase as GitHub Actions secrets shared by the three (soon four+) repos, add `maven-gpg-plugin` to a `ci-release` Maven profile, and fill in the `name`/`description`/`url`/`licenses`/`developers`/`scm` blocks each `pom.xml` is currently missing.
- **No off-the-shelf semantic-release-maven plugin is a clean fit**, but that's fine — the project doesn't need one. The existing `.releaserc.json` shape already has `@semantic-release/exec` wired for exactly this kind of "run a Maven goal at a lifecycle step" job; extending its `prepareCmd`/`publishCmd` to call `mvn deploy -P central-release --settings .mvn/ci-settings.xml` after `versions:set` is a small, low-risk change to a pipeline that's already structured for it, rather than adopting a new, less-proven plugin (`@terrestris/maven-semantic-release`'s current maintenance state is unconfirmed — see flags below).
- **Immutability works in the project's favor here**: Central's releases are effectively permanent once published (ecosystem consensus, not independently re-verified this pass — see flag below), which is exactly the property wanted once `sequeless-query` and future repos start depending on tagged releases of `sequeless-filter`/`sequeless-ontology` — no risk of a dependency silently changing under a consumer.

## 4. Fallback

**If Central Portal namespace verification stalls (DNS propagation issues, Sonatype review delay, or any other friction) or GPG/signing setup becomes a blocker before the repos need to interoperate, fall back to JitPack for the interim.** JitPack needs zero publish-side CI changes — consumers just add JitPack's repository URL and depend on `com.github.sequeless:<repo>:<tag-or-branch-SNAPSHOT>` — so inter-repo CI/local dev can start working within the sprint, with no token, no signing, and no namespace registration. The cost is a different (GitHub-derived) groupId coordinate than the `org.sequeless` one wanted long-term (JitPack mints coordinates from the GitHub org/user, i.e. `com.github.sequeless`, not an arbitrary groupId), and its 7-day immutability window is stricter than Central's — both acceptable short-term trade-offs while Central setup is finished, with a plan to migrate the `distributionManagement`/consumer coordinates to Central once verification lands. GitHub Packages is not recommended even as a fallback, since its mandatory-auth-for-public-reads problem is the exact thing both candidates above avoid, and it would need to be migrated away from later anyway if external, token-free consumption ever matters.

## 5. Unverified / surprising, flagged explicitly

- **Central Portal's own "can I delete/modify a component" FAQ page** (`central.sonatype.org/faq/can-i-change-delete-component/`) returned HTTP 404 when fetched directly in this research pass. The immutability claim for Maven Central releases above is ecosystem consensus, not a freshly-verified primary-source quote — re-check before treating it as settled.
- **Maven Central's "free for open source" claim** was not independently re-confirmed against a specific fetched Sonatype page in this pass (well-known industry fact, but flagging per the instruction to not guess).
- **JitPack's pricing-tier details** (cost for private repos / build priority) were not independently re-verified against `jitpack.io`'s pricing page in this pass; the "free for public repos" claim is solid (built into how `docs.jitpack.io` describes default usage), but exact paid-tier numbers are not cited here.
- **GitHub Packages' SNAPSHOT retention policy** (if any) for the Maven registry specifically was not found documented anywhere in `docs.github.com` — flagged as an open question rather than assumed to behave like Central's 90-day expiry.
- **`@terrestris/maven-semantic-release`'s current maintenance cadence** (last release/commit date) could not be confirmed from the fetched GitHub page in this pass — before considering it as an alternative to the `@semantic-release/exec` approach, check its actual release history on GitHub/npm.
- A mildly surprising finding worth calling out explicitly, since the issue only hinted at it: GitHub's docs state the **5,000-download threshold** for deleting a public package version, and a **30-day restore window** — specific numeric thresholds that aren't widely known and are easy to get wrong from memory. [docs.github.com: Deleting and restoring a package](https://docs.github.com/en/packages/learn-github-packages/deleting-and-restoring-a-package)
