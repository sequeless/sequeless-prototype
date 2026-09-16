package org.sequeless.spi;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The version of this SPI module, and the compatibility check adapters and the port registry use
 * to decide whether an adapter built against an older (or newer) SPI may be loaded.
 *
 * <p>An adapter declares the SPI versions it was built against as a range string in its {@link
 * AdapterDescriptor#spiVersionRange()}, of the form {@code ">=MAJOR.MINOR.PATCH"} — a single
 * minimum bound, not a Maven-style interval. An interval grammar would need a parsing library, and
 * this module may import nothing outside {@code java.*}. {@link #isCompatibleWith(String)} answers
 * whether the SPI actually running satisfies an adapter's declared minimum, using ordinary semantic
 * versioning rules: the major version must match exactly (a major bump signals a breaking change an
 * adapter cannot silently tolerate), and the running SPI's (minor, patch) pair must be greater than
 * or equal to the requested one.
 *
 * <p>This is the human-readable, runtime-checkable counterpart to the binary compatibility check
 * japicmp performs at build time against this module's own published jar: the two are meant to
 * agree about what "compatible" means for this SPI.
 */
public final class SpiVersion {

    /** Major version: bumped only for changes that break binary or source compatibility. */
    public static final int MAJOR = 0;

    /** Minor version: bumped for backward-compatible additions to the SPI surface. */
    public static final int MINOR = 1;

    /** Patch version: bumped for fixes that change no public API shape. */
    public static final int PATCH = 0;

    /** The full dotted version string of this SPI build, e.g. {@code "0.1.0"}. */
    public static final String VERSION = MAJOR + "." + MINOR + "." + PATCH;

    private static final Pattern RANGE_PATTERN = Pattern.compile("^>=(\\d+)\\.(\\d+)\\.(\\d+)$");

    private SpiVersion() {
    }

    /**
     * Reports whether {@code range} is well-formed according to this SPI's version-range grammar
     * ({@code ">=MAJOR.MINOR.PATCH"}), without regard to whether the running SPI actually satisfies
     * it. Used by {@link AdapterDescriptor}'s compact constructor to validate the range shape at
     * construction time, independently of the compatibility question {@link
     * #isCompatibleWith(String)} answers.
     *
     * @param range the candidate range string; {@code null} is accepted and reported as invalid
     * @return {@code true} if {@code range} matches the grammar
     */
    public static boolean isValidRange(String range) {
        return range != null && RANGE_PATTERN.matcher(range).matches();
    }

    /**
     * Reports whether this running SPI ({@link #VERSION}) satisfies the minimum version an adapter
     * declares it needs.
     *
     * @param range a version-range string of the form {@code ">=MAJOR.MINOR.PATCH"}
     * @return {@code true} if the major versions match and this SPI's (minor, patch) is greater
     *     than or equal to the requested (minor, patch)
     * @throws NullPointerException if {@code range} is {@code null}
     * @throws IllegalArgumentException if {@code range} does not match the expected grammar
     */
    public static boolean isCompatibleWith(String range) {
        Matcher m = RANGE_PATTERN.matcher(Objects.requireNonNull(range, "range must not be null"));
        if (!m.matches()) {
            throw new IllegalArgumentException(
                "Malformed SPI version range (expected '>=MAJOR.MINOR.PATCH'): " + range);
        }
        int reqMajor = Integer.parseInt(m.group(1));
        int reqMinor = Integer.parseInt(m.group(2));
        int reqPatch = Integer.parseInt(m.group(3));
        if (MAJOR != reqMajor) {
            return false;
        }
        if (MINOR != reqMinor) {
            return MINOR > reqMinor;
        }
        return PATCH >= reqPatch;
    }
}
