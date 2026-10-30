/**
 * Contract under test (lib/labels.ts): `pickLabel(object, targetType)` builds the display label
 * the relationship picker (phase-5 spec, "Relationship picker") shows for a resolved target
 * object. It walks `targetType.properties` in order and returns the value of the first property
 * that is a non-hidden `AttributePropertyResponse` with `datatype: 'STRING'` whose value in
 * `object.properties` is a non-empty string. Relationship properties and non-STRING attributes
 * are never candidates, regardless of position or value. If no candidate yields a usable value,
 * it falls back to `object.id`.
 *
 * `object.properties` is keyed by the property's short *name*, not its IRI —
 * `ObjectPropertyMapper#toProperties` (sequeless-app) resolves every wire key to
 * `PropertyDefinition.name` before the map reaches the client, so `pickLabel` must look values up
 * the same way (`targetType.properties[i].name`), never by `iri`.
 */
import type {
  AttributePropertyResponse,
  BusinessObjectResponse,
  RelationshipPropertyResponse,
  TypeDetailResponse,
} from '@sequeless/core-api';
import { isAttributeProperty, isRelationshipProperty } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { pickLabel } from './labels';

const detail: TypeDetailResponse = PROJECT_TYPE_DETAIL;
const findAttribute = (name: string): AttributePropertyResponse => {
  const property = detail.properties.find((p) => p.name === name);
  if (!property || !isAttributeProperty(property)) {
    throw new Error(`fixture has no attribute property named "${name}"`);
  }
  return property;
};
const findRelationship = (name: string): RelationshipPropertyResponse => {
  const property = detail.properties.find((p) => p.name === name);
  if (!property || !isRelationshipProperty(property)) {
    throw new Error(`fixture has no relationship property named "${name}"`);
  }
  return property;
};

// status: attribute, STRING, hidden false, order 2 (first STRING after title/hasTask)
// hasTask: relationship, order 2 (sits between title and status)
// openTaskCount: attribute, INTEGER, order 3
// owner: relationship, order 6
const STATUS = findAttribute('status');
const HAS_TASK = findRelationship('hasTask');
const OPEN_TASK_COUNT = findAttribute('openTaskCount');
const OWNER = findRelationship('owner');

function makeObject(properties: Record<string, unknown>, id = 'obj-1'): BusinessObjectResponse {
  return {
    id,
    type: detail.name,
    typeIri: detail.iri,
    version: 0,
    state: null,
    properties,
    audit: {
      createdAt: '2026-09-28T10:15:30Z',
      createdBy: 'anonymous',
      updatedAt: '2026-09-28T10:15:30Z',
      updatedBy: 'anonymous',
    },
  };
}

describe('pickLabel', () => {
  it("returns the value of the target type's first non-hidden STRING attribute property", () => {
    const object = makeObject({ title: 'Launch Plan', status: 'active' });
    expect(pickLabel(object, detail)).toBe('Launch Plan');
  });

  it('falls through to the next STRING candidate when the first one is an empty string', () => {
    const object = makeObject({ title: '', status: 'active' });
    expect(pickLabel(object, detail)).toBe('active');
  });

  it('falls through to the next STRING candidate when the first one is absent', () => {
    const object = makeObject({ status: 'active' });
    expect(pickLabel(object, detail)).toBe('active');
  });

  it('falls through to the next STRING candidate when the first one is null', () => {
    const object = makeObject({ title: null, status: 'active' });
    expect(pickLabel(object, detail)).toBe('active');
  });

  it('falls through to the next STRING candidate when the first one is not a string (e.g. a multi-valued array)', () => {
    const object = makeObject({ title: ['Launch Plan', 'Alt Name'], status: 'active' });
    expect(pickLabel(object, detail)).toBe('active');
  });

  it('skips a hidden STRING property and uses the next visible STRING candidate', () => {
    const customType: TypeDetailResponse = {
      ...detail,
      properties: [
        { ...STATUS, name: 'secretCode', hidden: true },
        { ...STATUS, name: 'displayName', hidden: false },
      ],
    };
    const object = makeObject({ secretCode: 'XJ-900', displayName: 'Widget 1' });
    expect(pickLabel(object, customType)).toBe('Widget 1');
  });

  it('ignores a non-STRING attribute property even when it sorts before a STRING one', () => {
    const customType: TypeDetailResponse = {
      ...detail,
      properties: [OPEN_TASK_COUNT, STATUS],
    };
    const object = makeObject({ openTaskCount: '5', status: 'active' });
    expect(pickLabel(object, customType)).toBe('active');
  });

  it('ignores a relationship property even when it sorts before a STRING attribute', () => {
    const customType: TypeDetailResponse = {
      ...detail,
      properties: [HAS_TASK, STATUS],
    };
    const object = makeObject({ hasTask: 'task-1', status: 'active' });
    expect(pickLabel(object, customType)).toBe('active');
  });

  it('falls back to object.id when no property yields a usable value', () => {
    const object = makeObject({}, 'obj-42');
    expect(pickLabel(object, detail)).toBe('obj-42');
  });

  it('falls back to object.id when the target type has no non-hidden STRING attribute properties', () => {
    const customType: TypeDetailResponse = {
      ...detail,
      properties: [HAS_TASK, OPEN_TASK_COUNT, OWNER],
    };
    const object = makeObject({ hasTask: 'task-1', openTaskCount: 5, owner: 'person-1' }, 'obj-7');
    expect(pickLabel(object, customType)).toBe('obj-7');
  });
});
