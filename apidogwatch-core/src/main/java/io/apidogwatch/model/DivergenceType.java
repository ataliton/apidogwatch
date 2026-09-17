package io.apidogwatch.model;

/**
 * Classification of a single contract divergence found while comparing
 * a live response payload against the OpenAPI schema.
 */
public enum DivergenceType {
    /** Field present in the live payload but absent from the contract. */
    EXTRA_FIELD,
    /** Field required/declared in the contract but missing in the live payload. */
    MISSING_FIELD,
    /** Field exists in both sides but JSON/OpenAPI types do not match. */
    TYPE_MISMATCH,
    /** No matching path/operation found in the OpenAPI document. */
    UNDOCUMENTED_ROUTE,
    /** Status code returned by the API is not declared for the operation. */
    UNEXPECTED_STATUS,
    /** Response body is not valid JSON while the contract expects an object/array. */
    INVALID_JSON
}
