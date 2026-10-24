import { test, expect, e2eEnv } from './support/test';

// Phase 8: a signal dispatches asynchronously. The 202 message shows at once, the object is unchanged in the
// UI until the delayed refetch (OBJECTS_SIGNAL_REFETCH_DELAY_MS, 1500 ms), then `reopen` is no longer offered
// because the object is Active. reopen is offered only from Closed (known-signals.ts:11), so the fixture
// walks a Project through activate and close first.
// UNVERIFIED against a live run: the owner relationship value format and the transition response shape.
const env = e2eEnv();

test('a dispatched signal changes the object only after the delayed refetch', async ({ page, request }) => {
  const person = await request.post(`/api/objects/Person`, { data: { properties: { name: 'E2E Owner' } } });
  const owner = (await person.json()).id;
  const project = await request.post(`/api/objects/${env.signalType}`, {
    data: { properties: { title: `signal-${Date.now()}`, owner } },
  });
  const id = (await project.json()).id;

  for (const transition of ['activate', 'close']) {
    const current = await (await request.get(`/api/objects/${env.signalType}/${id}`)).json();
    const res = await request.post(`/api/objects/${env.signalType}/${id}/transitions/${transition}`, {
      data: { expectedVersion: current.version },
    });
    expect(res.ok()).toBeTruthy();
  }

  await page.goto(`/objects/${env.signalType}/${id}`);
  const reopen = page.locator(`[data-signal="${env.signalName}"]`);
  await expect(reopen).toBeVisible();

  await reopen.click();
  await expect(page.getByRole('status')).toContainText('dispatched');
  await expect(reopen).toBeVisible();

  await expect(reopen).toBeHidden({ timeout: 5000 });
});
