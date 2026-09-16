package org.sequeless.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Sequeless Spring Boot application.
 *
 * <p>Deliberately declared at the {@code org.sequeless.app} root, rather than a nested package, so
 * {@code @SpringBootApplication}'s implicit component scan reaches every sibling package this
 * module defines — {@code .rest} (inbound REST adapter), {@code .config} (bean wiring, including
 * the {@code WhoAmI} use case), and {@code .port} (the fail-fast adapter registry) — without any of
 * them needing an explicit {@code @ComponentScan} base package.
 */
@SpringBootApplication
public class SequelessApplication {

    public static void main(String[] args) {
        SpringApplication.run(SequelessApplication.class, args);
    }
}
