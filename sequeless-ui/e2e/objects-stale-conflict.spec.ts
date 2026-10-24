import { test, expect, e2eEnv, openSecondTab } from './support/test';

// Phase 6 [OBJ-DETAIL]: a stale save surfaces the conflict dialog, and reapply saves exactly once more.
const env = e2eEnv();

test('a stale save shows the conflict dialog and reapply saves once', async ({ page }) => {
  const title = `stale-${Date.now()}`;
  await page.goto(`/objects/${env.objectType}/new`);
  await page.locator('[data-field="title"] input, input[data-field="title"]').first().fill(title);
  await page.getByRole('button', { name: 'Create' }).click();
  await expect(page).toHaveURL(new RegExp(`/objects/${env.objectType}/[^/]+$`));
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

  await tabB.locator('[data-field="title"] input, input[data-field="title"]').first().fill(`${title}-B`);
  await tabB.getByRole('button', { name: 'Save' }).click();
  await expect.poll(() => puts.length).toBe(1);

  await page.locator('[data-field="title"] input, input[data-field="title"]').first().fill(`${title}-A`);
  const staleSave = page.waitForResponse((r) => r.request().method() === 'PUT' && r.status() === 409);
  await page.getByRole('button', { name: 'Save' }).click();
  await staleSave;

  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('This record changed on the server')).toBeVisible();
  await dialog.locator('[data-action="reapply"]').click();
  await expect(dialog).toBeHidden();

  await expect.poll(() => puts.length).toBe(3);
});
