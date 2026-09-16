package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link JenaOntologyProperties} plain-POJO behaviour: the {@code owl} default this class's javadoc
 * commits to (see {@link JenaOntologyProperties}'s F47 note — {@code owl} is the only reasoner
 * setting that detects the project's inconsistency fixture), and that every property round-trips
 * through its getter/setter pair the way Spring's relaxed binder requires. This module has no
 * {@code spring-boot-test} dependency (see {@code sequeless-adapter-authz-permitall}'s precedent),
 * so this is a plain unit test rather than an actual {@code @ConfigurationProperties} binding test;
 * {@link JenaOntologyAutoConfigurationTest} separately proves this class is registered via {@code
 * @EnableConfigurationProperties}.
 */
class JenaOntologyPropertiesTest {

    @Test
    void reasonerDefaultsToOwl() {
        JenaOntologyProperties properties = new JenaOntologyProperties();

        assertThat(properties.getReasoner()).isEqualTo(ReasonerSetting.OWL);
    }

    @Test
    void adapterReasonerAndSourceRoundTripThroughTheirSetters() {
        JenaOntologyProperties properties = new JenaOntologyProperties();

        properties.setAdapter("jena");
        properties.setReasoner(ReasonerSetting.NONE);
        properties.setSource("classpath:ontology/reference.ttl");

        assertThat(properties.getAdapter()).isEqualTo("jena");
        assertThat(properties.getReasoner()).isEqualTo(ReasonerSetting.NONE);
        assertThat(properties.getSource()).isEqualTo("classpath:ontology/reference.ttl");
    }
}
