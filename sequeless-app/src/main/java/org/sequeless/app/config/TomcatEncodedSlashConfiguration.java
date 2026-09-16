package org.sequeless.app.config;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.sequeless.app.rest.TypesController;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables embedded Tomcat to accept a percent-encoded slash ({@code %2F}) inside a request path,
 * which {@link TypesController}'s {@code GET /types/{nameOrIri}} relies on when a client passes a
 * full type IRI containing {@code /} as a single path variable.
 *
 * <p>By default, Tomcat rejects any {@code %2F} in a request URI outright — a 400 raised at the
 * connector level, before the request ever reaches Spring's {@code DispatcherServlet} — as a
 * directory-traversal guard against ambiguity between an encoded and a literal path separator.
 * {@link EncodedSolidusHandling#DECODE} turns that rejection into "decode it and proceed", which
 * is safe here because {@code TypesController} never uses its path variable to address the
 * filesystem; it is looked up purely against in-memory {@link
 * org.sequeless.spi.meta.MetaModelSnapshot} type IRIs.
 *
 * <p><b>Must be a top-level class, not nested inside {@link TypesController}.</b> A nested
 * {@code @Configuration} class is a well-known Spring idiom in general, but it only registers via
 * component scan when reached as one of {@code @SpringBootApplication}'s regular candidates; empirically
 * verified while building this class (a scratch test asserted {@code
 * context.getBeansOfType(WebServerFactoryCustomizer.class)} before promoting it here) that when
 * nested one level inside a {@code @RestController}'s own top-level class, the customizer bean did
 * not appear in the context at all and every {@code %2F} request still 400'd — moving it to its own
 * top-level file fixed it immediately, with the exact same bean body.
 */
@Configuration(proxyBeanMethods = false)
public class TomcatEncodedSlashConfiguration {

    /**
     * @return a customizer that sets every Tomcat connector's encoded-solidus handling to {@link
     *     EncodedSolidusHandling#DECODE}
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> encodedSlashCustomizer() {
        return factory ->
                factory.addConnectorCustomizers(
                        connector ->
                                connector.setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue()));
    }
}
