import { InjectionToken } from '@angular/core';

/** A mounted text diff; `update` replaces both sides, `dispose` tears it down. */
export interface TextDiffView {
  update(base: string, working: string): void;
  dispose(): void;
}

export type TextDiffFactory = (host: HTMLElement) => TextDiffView;

/** Minimal slice of `@codemirror/merge` this module consumes. */
interface MergeModules {
  MergeView: new (config: {
    a: { doc: string; extensions: unknown[] };
    b: { doc: string; extensions: unknown[] };
    parent: HTMLElement;
  }) => { destroy(): void };
  editable: unknown[];
}

/**
 * The default view: a read-only `@codemirror/merge` MergeView. CodeMirror is loaded on first use via
 * dynamic `import()` so it stays out of the main bundle.
 */
export function codeMirrorTextDiffView(host: HTMLElement): TextDiffView {
  let modules: MergeModules | undefined;
  let view: { destroy(): void } | undefined;
  let pending: [string, string] | undefined;
  let disposed = false;

  const render = () => {
    if (!modules || !pending || disposed) {
      return;
    }
    view?.destroy();
    const [base, working] = pending;
    view = new modules.MergeView({
      a: { doc: base, extensions: modules.editable },
      b: { doc: working, extensions: modules.editable },
      parent: host,
    });
  };

  void Promise.all([import('@codemirror/merge'), import('@codemirror/view'), import('@codemirror/state')]).then(
    ([merge, cmView, cmState]) => {
      modules = {
        MergeView: merge.MergeView as unknown as MergeModules['MergeView'],
        editable: [cmView.EditorView.editable.of(false), cmState.EditorState.readOnly.of(true)],
      };
      render();
    },
  );

  return {
    update(base, working) {
      pending = [base, working];
      render();
    },
    dispose() {
      disposed = true;
      view?.destroy();
      view = undefined;
    },
  };
}

export const TEXT_DIFF_VIEW = new InjectionToken<TextDiffFactory>('Ontology text diff view', {
  providedIn: 'root',
  factory: () => codeMirrorTextDiffView,
});
