package io.apidogwatch.model;

import java.util.Objects;

/**
 * Immutable description of one mismatch between the live payload and the contract.
 */
public final class Divergence {

    private final DivergenceType type;
    private final String path;
    private final String message;
    private final String expected;
    private final String actual;

    public Divergence(DivergenceType type, String path, String message) {
        this(type, path, message, null, null);
    }

    public Divergence(DivergenceType type, String path, String message, String expected, String actual) {
        this.type = Objects.requireNonNull(type, "type");
        this.path = path == null ? "$" : path;
        this.message = Objects.requireNonNull(message, "message");
        this.expected = expected;
        this.actual = actual;
    }

    public DivergenceType getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public String getMessage() {
        return message;
    }

    public String getExpected() {
        return expected;
    }

    public String getActual() {
        return actual;
    }

    @Override
    public String toString() {
        return type + " @ " + path + ": " + message;
    }
}
