import { computed, inject, Injectable, signal } from '@angular/core';
import { OntologyClient } from '@sequeless/core-api';
import type {
  ApiError,
  OntologyImportResponse,
  OntologyIssue,
  OntologyReportResponse,
} from '@sequeless/core-api';
import { DeltaJournal, parseOntology, serializeOntology } from '@sequeless/rdf';
import type { OntologyDocument, QuadDelta, RdfStore } from '@sequeless/rdf';
import { firstValueFrom } from 'rxjs';

import { computeTextDelta } from './text-delta';

function invalidOntologyError(message: string): ApiError {
  return {
    status: 0,
    kind: 'unknown',
    title: 'Invalid ontology',
    detail: message,
    slug: null,
    violations: [],
    issues: [],
    expectedVersion: null,
    transitionName: null,
    typeIri: null,
    signalName: null,
    raw: null,
  };
}

/**
 * Owns the ontology document being authored: the parsed document, its undo journal and the working
 * store. The forms and the raw-Turtle tab are views over it; nothing else touches the store.
 */
@Injectable()
export class OntologySession {
  private readonly client = inject(OntologyClient);

  private document: OntologyDocument | null = null;
  private journal: DeltaJournal | null = null;
  private base = '';
  private pendingImportText: string | null = null;
  private cachedText: { revision: number; text: string } | null = null;

  private readonly _store = signal<RdfStore | null>(null);
  private readonly _revision = signal(0);
  private readonly _importRevision = signal(0);
  private readonly _loadError = signal<ApiError | null>(null);
  private readonly _canUndo = signal(false);
  private readonly _canRedo = signal(false);
  private readonly _lastImport = signal<OntologyImportResponse | null>(null);
  private readonly _lastRejection = signal<readonly OntologyIssue[]>([]);

  readonly store = this._store.asReadonly();
  /** Bumps on every apply, undo and redo; views depending on the (mutable) store read it. */
  readonly revision = this._revision.asReadonly();
  /** Bumps after each accepted import, so the type list can refetch. */
  readonly importRevision = this._importRevision.asReadonly();
  readonly loadError = this._loadError.asReadonly();
  readonly canUndo = this._canUndo.asReadonly();
  readonly canRedo = this._canRedo.asReadonly();
  readonly dirty = computed(() => this._canUndo());
  /** The response of the most recent accepted import. */
  readonly lastImport = this._lastImport.asReadonly();
  readonly lastRejection = this._lastRejection.asReadonly();

  async load(): Promise<void> {
    this._loadError.set(null);
    try {
      const text = await firstValueFrom(this.client.load());
      this.install(text);
    } catch (error) {
      this._store.set(null);
      this._loadError.set(this.toLoadError(error));
    }
  }

  apply(delta: QuadDelta): void {
    this.journal?.apply(delta);
    this.changed();
  }

  /** Applies a raw-Turtle edit as a quad delta. Throws on invalid Turtle. */
  applyText(turtle: string): void {
    const store = this._store();
    if (!store || !this.journal) {
      return;
    }
    if (this.cachedText?.revision === this._revision() && this.cachedText.text === turtle) {
      return;
    }
    this.apply(computeTextDelta(store, turtle));
  }

  undo(): boolean {
    const done = this.journal?.undo() ?? false;
    if (done) {
      this.changed();
    }
    return done;
  }

  redo(): boolean {
    const done = this.journal?.redo() ?? false;
    if (done) {
      this.changed();
    }
    return done;
  }

  async currentText(): Promise<string> {
    if (!this.document) {
      return '';
    }
    const revision = this._revision();
    const text = await serializeOntology(this.document);
    this.cachedText = { revision, text };
    return text;
  }

  baseText(): string {
    return this.base;
  }

  /** D6: refuses to send text that does not parse. */
  async prepareImport(): Promise<{ ok: true; text: string } | { ok: false; error: string }> {
    if (!this.document) {
      return { ok: false, error: 'The ontology is not loaded' };
    }
    const text = await this.currentText();
    try {
      parseOntology(text);
    } catch (error) {
      return { ok: false, error: error instanceof Error ? error.message : String(error) };
    }
    this.pendingImportText = text;
    return { ok: true, text };
  }

  /** D11: rebases base := working and clears the journal. The caller must not also reload types. */
  importSucceeded(response: OntologyImportResponse): void {
    if (!this.journal) {
      return;
    }
    if (this.pendingImportText !== null) {
      this.base = this.pendingImportText;
    }
    if (this.document) {
      this.document = { ...this.document, baseQuads: this.document.store.quads() };
    }
    this.pendingImportText = null;
    this.journal.clear();
    this._lastRejection.set([]);
    this._canUndo.set(false);
    this._canRedo.set(false);
    this._lastImport.set(response);
    this._importRevision.update((n) => n + 1);
  }

  setRejection(report: OntologyReportResponse): void {
    this._lastRejection.set(report.issues);
  }

  private install(text: string): void {
    const document = parseOntology(text);
    this.document = document;
    this.journal = new DeltaJournal(document.store);
    this.base = text;
    this.cachedText = null;
    this._store.set(document.store);
    this._lastRejection.set([]);
    this._revision.update((n) => n + 1);
    this.syncFlags();
    this._revision.set(0);
  }

  private changed(): void {
    this._revision.update((n) => n + 1);
    this.syncFlags();
  }

  private syncFlags(): void {
    this._canUndo.set(this.journal?.canUndo ?? false);
    this._canRedo.set(this.journal?.canRedo ?? false);
  }

  private toLoadError(error: unknown): ApiError {
    if (typeof error === 'object' && error !== null && 'kind' in error && 'status' in error) {
      return error as ApiError;
    }
    return invalidOntologyError(error instanceof Error ? error.message : String(error));
  }
}
