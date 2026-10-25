import { test, expect, e2eEnv } from './support/test';

// Phase 7: an edited property label is imported and shows on the ontology editor and the objects column header.
// Re-running mutates the shared ontology again; the Title row locator below is UNVERIFIED against a live run.
const env = e2eEnv();

test('an edited property label reaches the objects view after import', async ({ page }) => {
  const label = 'E2E Title';
  await page.goto('/ontology');
  await page.locator(`[data-type="${env.objectType}"]`).click();

  const titleLabel = page.locator('sq-property-editor').filter({ hasText: 'Title' }).locator('[data-field="label"]');
  await titleLabel.fill(label);
  await titleLabel.press('Tab');

  await page.locator('[data-action="import"]').click();
  await page.getByRole('dialog').locator('[data-action="import"]').click();
  await page.getByRole('dialog').locator('[data-action="close"]').click();

  await page.goto(`/objects/${env.objectType}`);
  await expect(page.getByRole('columnheader', { name: label })).toBeVisible();
});

// Phase 9.5 A6b: a 422 report routes an issue on the Title property onto its editor and an unmatched one into the banner.
// The subject IRI is the reference seed's ex:title (sequeless-app/src/main/resources/ontology/reference.ttl); UNVERIFIED until a live run.
test('a 422 report shows the matched issue on the Title editor and the unmatched one in the banner', async ({ page }) => {
  const matched = 'Title must not be blank';
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
          { severity: 'error', subjectIri: 'https://sequeless.dev/ns/ref#title', message: matched },
          { severity: 'error', subjectIri: 'urn:sequeless:unmatched', message: unmatched },
        ],
      }),
    });
  });

  await page.goto('/ontology');
  await page.locator(`[data-type="${env.objectType}"]`).click();

  await page.locator('[data-action="import"]').click();
  await page.getByRole('dialog').locator('[data-action="import"]').click();

  const titleEditor = page.locator('sq-property-editor').filter({ hasText: 'Title' });
  await expect(titleEditor.locator('.sq-server-error')).toHaveText(matched);
  await expect(page.locator('sq-problem-banner')).toContainText(unmatched);
});
