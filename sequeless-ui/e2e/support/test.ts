import type { Page } from '@playwright/test';

// The only file allowed to import the e2e runner. Journeys import from here so the runner can be swapped in one place.
export { test, expect } from '@playwright/test';

export interface E2eEnv {
  baseUrl: string;
  objectType: string;
  signalType: string;
  signalName: string;
}

export function e2eEnv(env: NodeJS.ProcessEnv = process.env): E2eEnv {
  return {
    baseUrl: env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    objectType: env['E2E_OBJECT_TYPE'] ?? 'Project',
    signalType: env['E2E_SIGNAL_TYPE'] ?? 'Project',
    signalName: env['E2E_SIGNAL_NAME'] ?? 'reopen',
  };
}

export async function openSecondTab(page: Page): Promise<Page> {
  return page.context().newPage();
}
