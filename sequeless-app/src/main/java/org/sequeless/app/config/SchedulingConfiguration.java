package org.sequeless.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} support for the whole application.
 *
 * <p>No other class in this reactor enables scheduling today; this is a tiny, single-purpose
 * configuration class purely so {@link org.sequeless.app.automation.OutboxRelay#pollOnce()} is
 * actually invoked by {@code ScheduledAnnotationBeanPostProcessor} rather than sitting dormant.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfiguration {}
