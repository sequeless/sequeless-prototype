/** A small ontology shared by the ontology project's specs. */
export const EX = 'http://ex.org/';
export const TASK = `${EX}Task`;
export const TITLE = `${EX}title`;
export const SCORE = `${EX}score`;
export const TOTAL = `${EX}total`;
export const LIFECYCLE = `${EX}TaskLifecycle`;

export const FIXTURE_TTL = `@prefix ex: <${EX}> .
@prefix sq: <https://sequeless.dev/ns/meta#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

ex:Task a owl:Class ;
  sq:label "Task" ;
  rdfs:subClassOf [ a owl:Restriction ; owl:onProperty ex:title ; owl:minCardinality "1"^^xsd:nonNegativeInteger ] .

ex:title a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:string ; sq:label "Title" ; sq:displayOrder "1"^^xsd:integer .
ex:score a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:integer ; sq:label "Score" ; sq:displayOrder "2"^^xsd:integer ;
  sq:derivedBy [ a sq:Plugin ; sq:pluginName "scorer" ] .
ex:total a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:integer ; sq:label "Total" ; sq:displayOrder "3"^^xsd:integer ;
  sq:derivedBy [ a sq:Rollup ; sq:function sq:count ; sq:over ex:Task ; sq:via ex:parent ] .
ex:parent a owl:ObjectProperty ; rdfs:domain ex:Task ; rdfs:range ex:Task ; sq:label "Parent" ; sq:displayOrder "4"^^xsd:integer .

ex:TaskLifecycle a sq:StateMachine ;
  sq:appliesTo ex:Task ;
  sq:initialState ex:Open ;
  sq:state ex:Open, ex:Done ;
  sq:transition
    [ a sq:Transition ; sq:name "complete" ; sq:from ex:Open ; sq:to ex:Done ;
      sq:trigger sq:UserAction ; sq:guard "self.title != null" ; sq:guardMessage "Needs a title" ] ,
    [ a sq:Transition ; sq:name "expire" ; sq:from ex:Open ; sq:to ex:Done ;
      sq:trigger sq:Timer ; sq:after "PT1H" ] .

ex:Open a sq:State ; sq:label "Open" ; sq:displayOrder "1"^^xsd:integer .
ex:Done a sq:State ; sq:label "Done" ; sq:displayOrder "2"^^xsd:integer .
`;
