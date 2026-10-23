import { inject, Injectable, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { OntologyClient } from '@sequeless/core-api';
import type { ApiError, OntologyIssue } from '@sequeless/core-api';
import { firstValueFrom } from 'rxjs';

import { openDiffPreview } from '../diff-preview/diff-preview-dialog';
import { ONTOLOGY_DIFF_SOURCE } from '../diff-preview/ontology-diff';
import { ControlRegistry } from '../issues/control-registry';
import { OntologySession } from '../session/ontology-session';

export type ImportResult = 'cancelled' | 'accepted' | 'rejected' | 'blocked';

function issue(message: string): OntologyIssue {
  return { severity: 'error', subjectIri: null, message };
}

/**
 * Review-and-import: guard (parse before send) -> diff -> preview -> POST. A 200 rebases the session;
 * a 422 is recorded on the session and routed onto the registered form controls. When no editor is
 * open the registry is empty, so every issue stays in `problems`.
 */
@Injectable()
export class ImportFlow {
  private readonly session = inject(OntologySession);
  private readonly client = inject(OntologyClient);
  private readonly registry = inject(ControlRegistry);
  private readonly diffSource = inject(ONTOLOGY_DIFF_SOURCE);
  private readonly dialog = inject(MatDialog);

  private readonly _problems = signal<ApiError | readonly OntologyIssue[] | null>(null);
  /** What the banner should show: a request failure, or the issues no form control claimed. */
  readonly problems = this._problems.asReadonly();

  async run(): Promise<ImportResult> {
    this._problems.set(null);
    const prepared = await this.session.prepareImport();
    if (!prepared.ok) {
      this._problems.set([issue(prepared.error)]);
      return 'blocked';
    }

    const base = this.session.baseDocument();
    const working = this.session.store();
    if (!base || !working) {
      this._problems.set([issue('The ontology is not loaded')]);
      return 'blocked';
    }

    try {
      const diff = await this.diffSource.diff(base, working);
      if (!(await firstValueFrom(openDiffPreview(this.dialog, diff)))) {
        return 'cancelled';
      }
    } catch (error) {
      this._problems.set([issue(error instanceof Error ? error.message : String(error))]);
      return 'blocked';
    }

    try {
      const outcome = await firstValueFrom(this.client.import(prepared.text));
      if (outcome.status === 'accepted') {
        this.session.importSucceeded(outcome.response);
        return 'accepted';
      }
      this.session.setRejection(outcome.report);
      const { matchedFirst, unmatched } = this.registry.routeIssues(outcome.report.issues);
      if (matchedFirst) {
        this.registry.reveal(matchedFirst);
      }
      this._problems.set(unmatched);
      return 'rejected';
    } catch (error) {
      this._problems.set(error as ApiError);
      return 'blocked';
    }
  }
}
