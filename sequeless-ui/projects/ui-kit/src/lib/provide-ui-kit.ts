import { makeEnvironmentProviders, type EnvironmentProviders } from '@angular/core';
import { FIELD_RENDERERS, GRAPH_LAYOUT, TURTLE_EDITOR } from '@sequeless/core-api';
import { DEFAULT_FIELD_RENDERERS } from './dynamic-field/default-field-renderers';
import { CodeMirrorTextEditorAdapter } from './turtle-editor/codemirror-text-editor-adapter';
import { DagreGraphLayout } from './state-machine-graph/dagre-graph-layout';

/**
 * Wires core-api's three ui-kit slots (FIELD_RENDERERS, TURTLE_EDITOR, GRAPH_LAYOUT)
 * to this library's default implementations.
 */
export function provideUiKit(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: FIELD_RENDERERS, useValue: DEFAULT_FIELD_RENDERERS },
    { provide: TURTLE_EDITOR, useValue: () => new CodeMirrorTextEditorAdapter() },
    { provide: GRAPH_LAYOUT, useFactory: () => new DagreGraphLayout() },
  ]);
}
