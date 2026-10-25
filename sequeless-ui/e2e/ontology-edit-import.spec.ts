import { test, expect, e2eEnv, type Locator, type Page } from './support/test';

// Phase 7: an edited property label is imported and shows on the ontology editor and the objects column header.
// Re-running mutates the shared ontology again. The Project class editor lists only properties declared on Project
// (property-list.ts:36), so Title (declared on Deliverable) is not on this page; Owner is, so the label edit uses Owner.
const env = e2eEnv();

// The editor's label is an input value, not text, so text filters cannot find it: locate it by that value. The
// includes match covers the label the first test renames it to on the shared ontology (see `label` there).
async function ownerEditor(page: Page): Promise<Locator> {
  await expect(page.locator('sq-property-editor').first()).toBeVisible();
  const index = await page.$$eval('sq-property-editor', (editors) =>
    editors.findIndex((e) => ((e.querySelector('[data-field="label"]') as { value?: string } | null)?.value ?? '').includes('Owner')),
  );
  return page.locator('sq-property-editor').nth(index);
}

test('an edited property label reaches the objects view after import', async ({ page }) => {
  // Unique per run: the ontology is shared and Import is disabled when nothing changed, so a repeated label hangs.
  const label = `E2E Owner ${Date.now()}`;
  await page.goto('/ontology');
  await page.locator(`[data-type="${env.objectType}"]`).click();

  const ownerLabel = (await ownerEditor(page)).locator('[data-field="label"]');
  await ownerLabel.fill(label);
  await ownerLabel.press('Tab');

  await page.locator('[data-action="import"]').click();
  await page.getByRole('dialog').locator('[data-action="import"]').click();
  await page.getByRole('dialog').locator('[data-action="close"]').click();

  await page.goto(`/objects/${env.objectType}`);
  await expect(page.getByRole('columnheader', { name: label })).toBeVisible();
});

// Phase 9.5 A6b: a 422 report routes an issue on the Owner property onto its editor and an unmatched one into the banner.
// The subject IRI is the reference seed's ex:owner (sequeless-app/src/main/resources/ontology/reference.ttl:189-193),
// which the Project class editor shows; the editor and banner wiring is UNVERIFIED until a live run.
test('a 422 report shows the matched issue on the Owner editor and the unmatched one in the banner', async ({ page }) => {
  const matched = 'Owner must not be blank';
  const unmatched = 'Problem with no editor';
  await page.route('**/api/ontology', (route) => {
    if (route.request().method() !== 'POST') {
      return route.continue();
    }
    return route.fulfill({
      status: 422,
      contentType: 'application/json',
      body: JSON.stringify({
        consistent: false,
        issues: [
          { severity: 'error', subjectIri: 'https://sequeless.dev/ns/ref#owner', message: matched },
          { severity: 'error', subjectIri: 'urn:sequeless:unmatched', message: unmatched },
        ],
      }),
    });
  });

  await page.goto('/ontology');
  await page.locator(`[data-type="${env.objectType}"]`).click();

  // Import is disabled until something changes, so stage a label edit first.
  const ownerLabel = (await ownerEditor(page)).locator('[data-field="label"]');
  await ownerLabel.fill(`E2E Owner ${Date.now()}`);
  await ownerLabel.press('Tab');

  await page.locator('[data-action="import"]').click();
  await page.getByRole('dialog').locator('[data-action="import"]').click();

  await expect((await ownerEditor(page)).locator('.sq-server-error')).toHaveText(matched);
  await expect(page.locator('sq-problem-banner')).toContainText(unmatched);
});
