package com.atakmap.android.scythe;

import android.content.Context;
import android.util.Log;

import com.atakmap.android.scythe.api.ApiCallback;
import com.atakmap.android.scythe.api.ScytheApiClient;
import com.atakmap.android.scythe.model.AuthResponse;
import com.atakmap.android.scythe.model.CotEvent;
import com.atakmap.android.scythe.model.HeatmapData;
import com.atakmap.android.scythe.model.Mission;
import com.atakmap.android.scythe.model.RfNode;

import java.util.List;

/**
 * Primary map component for the Scythe ATAK plugin.
 *
 * <p>Owns the {@link ScytheApiClient} singleton and exposes convenience methods
 * that other components can call to interact with rf_scythe_api_server.
 */
public class ScytheMapComponent {

    private static final String TAG = "ScytheMapComponent";

    /**
     * Default server URL. In a production build this would be read from a
     * SharedPreferences / ATAK preference store.
     */
    private static final String DEFAULT_BASE_URL = "https://rf-scythe-api.example.com";

    private final Context context;
    private ScytheApiClient apiClient;

    public ScytheMapComponent(Context context) {
        this.context = context;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void onCreate(Context context) {
        Log.d(TAG, "onCreate");
        apiClient = new ScytheApiClient(DEFAULT_BASE_URL);
    }

    public void onResume() {
        Log.d(TAG, "onResume");
    }

    public void onPause() {
        Log.d(TAG, "onPause");
    }

    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        apiClient = null;
    }

    // -------------------------------------------------------------------------
    // Public API helpers (delegate to ScytheApiClient)
    // -------------------------------------------------------------------------

    /**
     * Registers a new user account with rf_scythe_api_server.
     *
     * @param username desired username
     * @param password desired password
     * @param callback result callback
     */
    public void register(String username, String password, ApiCallback<AuthResponse> callback) {
        ensureClientReady();
        apiClient.register(username, password, callback);
    }

    /**
     * Authenticates with rf_scythe_api_server. The returned JWT token is
     * cached inside {@link ScytheApiClient} and used for all subsequent calls.
     *
     * @param username account username
     * @param password account password
     * @param callback result callback
     */
    public void login(String username, String password, ApiCallback<AuthResponse> callback) {
        ensureClientReady();
        apiClient.login(username, password, callback);
    }

    /**
     * Fetches the list of RF scanning nodes from rf_scythe_api_server.
     *
     * @param callback result callback
     */
    public void getRfNodes(ApiCallback<List<RfNode>> callback) {
        ensureClientReady();
        apiClient.getRfNodes(callback);
    }

    /**
     * Fetches all Cursor-on-Target events from rf_scythe_api_server.
     *
     * @param callback result callback
     */
    public void getCotEvents(ApiCallback<List<CotEvent>> callback) {
        ensureClientReady();
        apiClient.getCotEvents(callback);
    }

    /**
     * Publishes a new CoT event to rf_scythe_api_server.
     *
     * @param event    the CoT event to send
     * @param callback result callback
     */
    public void postCotEvent(CotEvent event, ApiCallback<CotEvent> callback) {
        ensureClientReady();
        apiClient.postCotEvent(event, callback);
    }

    /**
     * Fetches all missions from rf_scythe_api_server.
     *
     * @param callback result callback
     */
    public void getMissions(ApiCallback<List<Mission>> callback) {
        ensureClientReady();
        apiClient.getMissions(callback);
    }

    /**
     * Fetches heatmap data for the given mission from rf_scythe_api_server.
     *
     * @param missionId ID of the mission
     * @param callback  result callback
     */
    public void getHeatmap(String missionId, ApiCallback<HeatmapData> callback) {
        ensureClientReady();
        apiClient.getHeatmap(missionId, callback);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void ensureClientReady() {
        if (apiClient == null) {
            throw new IllegalStateException(
                    "ScytheMapComponent has not been initialized. Call onCreate() first.");
        }
    }

    /** Returns the underlying API client (package-private, used in tests). */
    ScytheApiClient getApiClient() {
        return apiClient;
    }
}
