import { test, expect, e2eEnv, openSecondTab, type Page } from './support/test';

// Phase 6 [OBJ-DETAIL]: a stale save surfaces the conflict dialog, and reapply saves exactly once more.
// Title is a list field in the General group, which renders collapsed with only an Add button: expand the panel,
// add a row if none is shown, then fill the row (verified against a live run, Phase 9.5 R2).
const env = e2eEnv();

async function fillTitle(page: Page, value: string): Promise<void> {
  const header = page.locator('mat-expansion-panel-header', { hasText: 'General' });
  if ((await header.getAttribute('aria-expanded')) !== 'true') {
    await header.click();
  }
  const input = page.locator('input[data-field="title"]').first();
  if ((await input.count()) === 0) {
    await page.locator('mat-expansion-panel sq-dynamic-field').first().locator('[data-action="add"]').click();
  }
  await input.fill(value);
}

test('a stale save shows the conflict dialog and reapply saves once', async ({ page }) => {
  const title = `stale-${Date.now()}`;
  await page.goto(`/objects/${env.objectType}/new`);
  await fillTitle(page, title);
  await page.getByRole('button', { name: 'Create' }).click();
  await expect(page).toHaveURL(new RegExp(`/objects/${env.objectType}/(?!new$)[^/]+$`));
  const objectPath = new URL(page.url()).pathname;

  const tabB = await openSecondTab(page);
  await page.goto(objectPath);
  await tabB.goto(objectPath);

  const puts: string[] = [];
  page.context().on('request', (req) => {
    if (req.method() === 'PUT' && req.url().includes(objectPath)) puts.push(req.url());
  });

  await page.getByRole('button', { name: 'Edit' }).click();
  await tabB.getByRole('button', { name: 'Edit' }).click();

  await fillTitle(tabB, `${title}-B`);
  await tabB.getByRole('button', { name: 'Save' }).click();
  await expect.poll(() => puts.length).toBe(1);

  await fillTitle(page, `${title}-A`);
  const staleSave = page.waitForResponse((r) => r.request().method() === 'PUT' && r.status() === 409);
  await page.getByRole('button', { name: 'Save' }).click();
  await staleSave;

  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('This record changed on the server')).toBeVisible();
  await dialog.locator('[data-action="reapply"]').click();
  await expect(dialog).toBeHidden();

  await expect.poll(() => puts.length).toBe(3);
});
