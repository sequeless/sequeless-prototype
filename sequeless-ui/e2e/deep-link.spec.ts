import { test, expect, e2eEnv } from './support/test';

// Deep links survive a reload (Phase 6 [OBJ-QSTATE]; Phase 7 ontology routes).
const env = e2eEnv();

test('the ontology view survives a reload', async ({ page }) => {
  await page.goto('/ontology');
  const toolbar = page.locator('.sq-ontology-toolbar');
  await expect(toolbar).toBeVisible();

  await page.reload();

  await expect(page).toHaveURL(/\/ontology$/);
  await expect(toolbar).toBeVisible();
});

test('the browse query survives a reload', async ({ page }) => {
  await page.goto(`/objects/${env.objectType}?page=1`);
  const heading = page.locator('h2');
  await expect(heading).toContainText(env.objectType);

  await page.reload();

  await expect(page).toHaveURL(/page=1/);
  await expect(heading).toContainText(env.objectType);
});
