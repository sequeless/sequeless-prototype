/**
 * Reading the ontology's halves for a use case. {@link org.sequeless.core.ontology.SchemaAndBehaviour}
 * reads the {@link org.sequeless.spi.meta.schema.SchemaSource schema} and {@link
 * org.sequeless.spi.meta.behaviour.BehaviourSource behaviour} halves together and checks that they
 * come from the same ontology version, so a request never mixes two activations.
 */
package org.sequeless.core.ontology;
