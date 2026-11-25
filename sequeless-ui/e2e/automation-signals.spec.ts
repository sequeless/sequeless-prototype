import { test, expect, e2eEnv } from './support/test';
import type { APIRequestContext } from './support/test';

// A signal is applied in its request: the 202 comes once the transition is applied. The UI keeps showing the old
// state until the delayed refetch (OBJECTS_SIGNAL_REFETCH_DELAY_MS, 1500 ms), then `reopen` is no longer offered
// because the object is Active. reopen is offered only from Closed (known-signals.ts:11), so the fixture walks a
// Project through activate and close first.
// Verified against a live run: the owner is a bare Person id, and title is a list on the wire (a scalar is a 400).
const env = e2eEnv();

async function closedProject(request: APIRequestContext): Promise<string> {
  const person = await request.post(`/api/objects/Person`, { data: { properties: { name: 'E2E Owner' } } });
  const owner = (await person.json()).id;
  const project = await request.post(`/api/objects/${env.signalType}`, {
    data: { properties: { title: [`signal-${Date.now()}`], owner } },
  });
  const id = (await project.json()).id;

  for (const transition of ['activate', 'close']) {
    const current = await (await request.get(`/api/objects/${env.signalType}/${id}`)).json();
    const res = await request.post(`/api/objects/${env.signalType}/${id}/transitions/${transition}`, {
      data: { expectedVersion: current.version },
    });
    expect(res.ok()).toBeTruthy();
  }
  return id;
}

test('a dispatched signal changes the object only after the delayed refetch', async ({ page, request }) => {
  const id = await closedProject(request);

  await page.goto(`/objects/${env.signalType}/${id}`);
  const reopen = page.locator(`[data-signal="${env.signalName}"]`);
  await expect(reopen).toBeVisible();

  await reopen.click();
  await expect(page.getByRole('status')).toContainText('dispatched');
  await expect(reopen).toBeVisible();

  await expect(reopen).toBeHidden({ timeout: 5000 });
});

test('a rejected signal shows the problem banner', async ({ page, request }) => {
  const id = await closedProject(request);

  await page.goto(`/objects/${env.signalType}/${id}`);
  const reopen = page.locator(`[data-signal="${env.signalName}"]`);
  await expect(reopen).toBeVisible();

  // Reopen through the API, so the page still offers reopen from Closed while the object is Active.
  const url = `/api/objects/${env.signalType}/${id}/signals/${env.signalName}`;
  const reopened = await request.post(url, { data: {} });
  expect(reopened.status()).toBe(202);

  await reopen.click();
  await expect(page.locator('div[role="alert"].sq-problem-banner')).toBeVisible();
});
