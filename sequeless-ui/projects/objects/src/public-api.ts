/*
 * Public API Surface of objects
 *
 * Re-exports every runtime symbol of the library, including the pure helpers
 * (browse columns, violation mapping, object form, known signals) and the
 * SqObjectForm component, so consumers import only from '@sequeless/objects'.
 */

export * from './lib/objects.routes';
export * from './lib/browse/browse-query-state.service';
export * from './lib/detail/object-detail.store';
export * from './lib/detail/object-detail.component';
export * from './lib/create/object-create';
export * from './lib/signals/object-signals';
export * from './lib/browse/object-browse';
export * from './lib/relationship/objects-relationship-lookup';
export * from './lib/type-list/type-list';
export * from './lib/browse/browse-columns';
export * from './lib/browse/violation-mapping';
export * from './lib/detail/object-form';
export * from './lib/signals/known-signals';
