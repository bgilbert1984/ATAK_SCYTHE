package com.atakmap.android.scythe.api;

/**
 * Generic callback used to deliver asynchronous results from
 * {@link ScytheApiClient}.
 *
 * <p><strong>Threading:</strong> callbacks are invoked on OkHttp's internal
 * dispatcher thread pool, <em>not</em> on the Android main thread. Callers
 * that need to update the UI must marshal the result back to the main thread
 * themselves (e.g. via {@code Handler} or {@code Activity.runOnUiThread}).
 *
 * @param <T> the type of the successful result value
 */
public interface ApiCallback<T> {

    /**
     * Called when the request completes successfully.
     *
     * @param result the deserialized response body
     */
    void onSuccess(T result);

    /**
     * Called when the request fails due to a network error, an unexpected HTTP
     * status code, or a serialization problem.
     *
     * @param errorMessage a human-readable description of the failure
     */
    void onFailure(String errorMessage);
}
