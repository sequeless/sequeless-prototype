package org.sequeless.spi.query;

import java.util.Objects;
import org.sequeless.kernel.values.ObjectId;

/**
 * A single request to {@link ReadPort#read}: the id of the object to read.
 *
 * @param id the id of the object to read; must not be {@code null}
 */
public record ReadRequest(ObjectId id) {

    public ReadRequest {
        Objects.requireNonNull(id, "id must not be null");
    }
}
