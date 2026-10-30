/**
 * Contract under test (lib/vocabulary.ts): `SQ` must carry exactly the non-reserved `sq:` terms
 * documented in docs/architecture/sq-vocabulary.md, each equal to `SQ_NS + localName`, with no
 * invented terms and no terms the doc marks reserved for a later phase (currently `sq:permission`,
 * Phase 8). `OWL`/`RDF`/`RDFS`/`XSD` must carry the standard namespace IRIs for the terms this
 * library actually needs (per the edit-delta table in docs/specs/phase-4-rdf.md).
 * `DATATYPE_TO_XSD` must cover all eleven `Datatype` values with the exact `xsd:` IRIs
 * `org.sequeless.spi.meta.Datatype` binds them to, and `xsdToDatatype` must invert that mapping,
 * falling back to `'STRING'` for any IRI outside the closed set.
 */
import { DATATYPE_TO_XSD, OWL, RDF, RDFS, SQ, SQ_NS, XSD, xsdToDatatype } from './vocabulary';
import type { Datatype } from './model';

// Every non-reserved sq: local name documented in docs/architecture/sq-vocabulary.md, across all
// phases (1, 4, 5, 6) plus the "two terms added beyond the brief" section. Built explicitly from
// the doc rather than from SQ itself, so this test can catch both missing and invented terms.
const DOCUMENTED_SQ_TERMS = [
  // Phase 1
  'label',
  'displayOrder',
  'displayGroup',
  'hidden',
  'facet',
  'indexed',
  'searchable',
  'readOnly',
  'displayLabel',
  'abstract',
  // Phase 4 — derived properties
  'derivedBy',
  'function',
  'over',
  'via',
  'of',
  'filter',
  'property',
  'operator',
  'value',
  'pluginName',
  // Phase 4 — structural classes
  'Rollup',
  'Plugin',
  'Criterion',
  // Phase 4 — sq:function individuals
  'count',
  'sum',
  'min',
  'max',
  'avg',
  // Phase 4 — sq:operator individuals
  'eq',
  'ne',
  'in',
  'lt',
  'lte',
  'gt',
  'gte',
  'contains',
  'startsWith',
  'isNull',
  'notNull',
  // Phase 5 — state machines and automation
  'appliesTo',
  'initialState',
  'state',
  'transition',
  'name',
  'from',
  'to',
  'trigger',
  'guard',
  'guardMessage',
  'action',
  'expression',
  'type',
  'properties',
  'url',
  'method',
  'body',
  'message',
  // Phase 5 — structural classes
  'StateMachine',
  'State',
  'Transition',
  'SetProperty',
  'CreateObject',
  'PropertyAssignment',
  'Webhook',
  'Log',
  // Phase 6 — event-driven triggers and materialisation
  'watch',
  'after',
  'signalName',
  'materialised',
  // Phase 6 — sq:trigger individuals
  'UserAction',
  'OnChange',
  'Timer',
  'ExternalSignal',
] as const;

// Declared and scoped by the doc but not implemented yet; using them must fail loudly server-side,
// so this library must not expose them as if they were usable.
const RESERVED_SQ_TERMS = ['permission'] as const;

// org.sequeless.spi.meta.Datatype's xsdIri() for each of its 11 constants.
const DATATYPE_XSD_IRIS: Readonly<Record<Datatype, string>> = {
  STRING: 'http://www.w3.org/2001/XMLSchema#string',
  BOOLEAN: 'http://www.w3.org/2001/XMLSchema#boolean',
  INTEGER: 'http://www.w3.org/2001/XMLSchema#integer',
  LONG: 'http://www.w3.org/2001/XMLSchema#long',
  DECIMAL: 'http://www.w3.org/2001/XMLSchema#decimal',
  DOUBLE: 'http://www.w3.org/2001/XMLSchema#double',
  DATE: 'http://www.w3.org/2001/XMLSchema#date',
  TIME: 'http://www.w3.org/2001/XMLSchema#time',
  DATE_TIME: 'http://www.w3.org/2001/XMLSchema#dateTime',
  DURATION: 'http://www.w3.org/2001/XMLSchema#duration',
  ANY_URI: 'http://www.w3.org/2001/XMLSchema#anyURI',
};

describe('SQ_NS', () => {
  it('is the sq: namespace IRI from docs/architecture/sq-vocabulary.md', () => {
    expect(SQ_NS).toBe('https://sequeless.dev/ns/meta#');
  });
});

describe('SQ', () => {
  it('maps every documented non-reserved sq: term to SQ_NS + its local name', () => {
    for (const term of DOCUMENTED_SQ_TERMS) {
      expect((SQ as Record<string, string>)[term]).toBe(`${SQ_NS}${term}`);
    }
  });

  it('exposes exactly the documented terms and nothing invented', () => {
    expect(Object.keys(SQ).sort()).toEqual([...DOCUMENTED_SQ_TERMS].sort());
  });

  it('does not expose terms the doc marks reserved for a later phase', () => {
    for (const term of RESERVED_SQ_TERMS) {
      expect((SQ as Record<string, string | undefined>)[term]).toBeUndefined();
    }
  });
});

describe('OWL', () => {
  const expected: Record<string, string> = {
    Class: 'http://www.w3.org/2002/07/owl#Class',
    DatatypeProperty: 'http://www.w3.org/2002/07/owl#DatatypeProperty',
    ObjectProperty: 'http://www.w3.org/2002/07/owl#ObjectProperty',
    Restriction: 'http://www.w3.org/2002/07/owl#Restriction',
    onProperty: 'http://www.w3.org/2002/07/owl#onProperty',
    cardinality: 'http://www.w3.org/2002/07/owl#cardinality',
    minCardinality: 'http://www.w3.org/2002/07/owl#minCardinality',
    maxCardinality: 'http://www.w3.org/2002/07/owl#maxCardinality',
    inverseOf: 'http://www.w3.org/2002/07/owl#inverseOf',
    TransitiveProperty: 'http://www.w3.org/2002/07/owl#TransitiveProperty',
  };

  it('maps every owl: term needed (per the edit-delta table) to the standard IRI', () => {
    for (const [term, iri] of Object.entries(expected)) {
      expect((OWL as Record<string, string>)[term]).toBe(iri);
    }
  });

  it('maps every term it does expose into the standard owl: namespace', () => {
    for (const [term, iri] of Object.entries(OWL as Record<string, string>)) {
      expect(iri).toBe(`http://www.w3.org/2002/07/owl#${term}`);
    }
  });
});

describe('RDF', () => {
  it('asserts owl:DatatypeProperty/owl:ObjectProperty via the standard rdf:type IRI', () => {
    expect((RDF as Record<string, string>)['type']).toBe(
      'http://www.w3.org/1999/02/22-rdf-syntax-ns#type',
    );
  });

  it('maps every term it does expose into the standard rdf: namespace', () => {
    for (const [term, iri] of Object.entries(RDF as Record<string, string>)) {
      expect(iri).toBe(`http://www.w3.org/1999/02/22-rdf-syntax-ns#${term}`);
    }
  });
});

describe('RDFS', () => {
  const expected: Record<string, string> = {
    domain: 'http://www.w3.org/2000/01/rdf-schema#domain',
    range: 'http://www.w3.org/2000/01/rdf-schema#range',
    subClassOf: 'http://www.w3.org/2000/01/rdf-schema#subClassOf',
    label: 'http://www.w3.org/2000/01/rdf-schema#label',
  };

  it('maps domain/range/subClassOf and the sq:label rdfs:label fallback to the IRI', () => {
    for (const [term, iri] of Object.entries(expected)) {
      expect((RDFS as Record<string, string>)[term]).toBe(iri);
    }
  });

  it('maps every term it does expose into the standard rdfs: namespace', () => {
    for (const [term, iri] of Object.entries(RDFS as Record<string, string>)) {
      expect(iri).toBe(`http://www.w3.org/2000/01/rdf-schema#${term}`);
    }
  });
});

describe('XSD', () => {
  it('maps nonNegativeInteger, needed for cardinality literals, to the standard xsd: IRI', () => {
    expect((XSD as Record<string, string>)['nonNegativeInteger']).toBe(
      'http://www.w3.org/2001/XMLSchema#nonNegativeInteger',
    );
  });

  it('maps every xsd: datatype term used by DATATYPE_TO_XSD to the standard xsd: IRI', () => {
    for (const iri of Object.values(DATATYPE_XSD_IRIS)) {
      const localName = iri.slice('http://www.w3.org/2001/XMLSchema#'.length);
      expect((XSD as Record<string, string>)[localName]).toBe(iri);
    }
  });

  it('maps every term it does expose into the standard xsd: namespace', () => {
    for (const [term, iri] of Object.entries(XSD as Record<string, string>)) {
      expect(iri).toBe(`http://www.w3.org/2001/XMLSchema#${term}`);
    }
  });
});

describe('DATATYPE_TO_XSD', () => {
  it('covers all eleven Datatype values with the exact xsd IRI from Datatype.xsdIri()', () => {
    for (const [datatype, iri] of Object.entries(DATATYPE_XSD_IRIS)) {
      expect(DATATYPE_TO_XSD[datatype as Datatype]).toBe(iri);
    }
  });

  it('defines no datatype beyond the eleven documented values', () => {
    expect(Object.keys(DATATYPE_TO_XSD).sort()).toEqual(Object.keys(DATATYPE_XSD_IRIS).sort());
  });
});

describe('xsdToDatatype', () => {
  it('inverts DATATYPE_TO_XSD for every one of the eleven known xsd: IRIs', () => {
    for (const [datatype, iri] of Object.entries(DATATYPE_XSD_IRIS)) {
      expect(xsdToDatatype(iri)).toBe(datatype as Datatype);
    }
  });

  it('falls back to STRING for an xsd: IRI outside the closed set', () => {
    expect(xsdToDatatype('http://www.w3.org/2001/XMLSchema#base64Binary')).toBe('STRING');
  });

  it('falls back to STRING for a completely unrecognised IRI', () => {
    expect(xsdToDatatype('https://example.test/not-a-datatype')).toBe('STRING');
  });
});
