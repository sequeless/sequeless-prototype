import { ChangeDetectionStrategy, Component } from '@angular/core';

import { SqImportBar } from '../import-result/import-bar';
import { SqRawTurtleTab } from './raw-turtle-tab';

/** The "Raw Turtle" route: the whole ontology as text, with the same undo / import bar as the type editor. */
@Component({
  selector: 'sq-raw-turtle-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SqImportBar, SqRawTurtleTab],
  template: `
    <sq-import-bar />
    <sq-raw-turtle-tab />
  `,
})
export class SqRawTurtlePage {}
