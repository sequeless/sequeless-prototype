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
