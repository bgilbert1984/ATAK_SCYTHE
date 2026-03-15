package com.atakmap.android.scythe.api;

import com.atakmap.android.scythe.model.AuthRequest;
import com.atakmap.android.scythe.model.AuthResponse;
import com.atakmap.android.scythe.model.CotEvent;
import com.atakmap.android.scythe.model.HeatmapData;
import com.atakmap.android.scythe.model.Mission;
import com.atakmap.android.scythe.model.RfNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * OkHttp-based REST client for rf_scythe_api_server.
 *
 * <p>All network calls are executed asynchronously. Results are delivered via
 * {@link ApiCallback}. Callers are responsible for marshalling the callback
 * invocations back to the UI thread if required.
 *
 * <h3>Supported endpoints</h3>
 * <ul>
 *   <li>{@code POST /auth/register} – create a new user account</li>
 *   <li>{@code POST /auth/login}    – authenticate and obtain a JWT</li>
 *   <li>{@code GET  /rf/nodes}      – list RF scanning nodes</li>
 *   <li>{@code GET  /cot/events}    – list Cursor-on-Target events</li>
 *   <li>{@code POST /cot/events}    – publish a new CoT event</li>
 *   <li>{@code GET  /missions}      – list missions</li>
 *   <li>{@code GET  /missions/{id}/heatmap} – fetch heatmap for a mission</li>
 * </ul>
 */
public class ScytheApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;

    /** Bearer token set after a successful login or register call. */
    private volatile String authToken;

    /**
     * Creates a new client pointing at {@code baseUrl}.
     *
     * @param baseUrl base URL of rf_scythe_api_server, e.g.
     *                {@code "https://api.example.com"}. Must not end with a
     *                trailing slash.
     */
    public ScytheApiClient(String baseUrl) {
        this(baseUrl, buildDefaultHttpClient());
    }

    /**
     * Package-private constructor that accepts a pre-configured
     * {@link OkHttpClient}; used in tests to inject a {@code MockWebServer}.
     */
    ScytheApiClient(String baseUrl, OkHttpClient httpClient) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("baseUrl must not be null or empty");
        }
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.gson = new GsonBuilder().create();
    }

    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------

    /**
     * Registers a new user account.
     *
     * <p>On success the returned {@link AuthResponse} will contain the JWT that
     * can be used for subsequent authenticated requests.
     *
     * @param username desired username
     * @param password desired password
     * @param callback result callback
     */
    public void register(String username, String password, ApiCallback<AuthResponse> callback) {
        AuthRequest body = new AuthRequest(username, password);
        Request request = postRequest("/auth/register", body);
        executeAsync(request, AuthResponse.class, callback);
    }

    /**
     * Authenticates an existing user.
     *
     * <p>On success the returned {@link AuthResponse#getToken()} is cached
     * internally and automatically attached as a {@code Bearer} token to all
     * subsequent requests.
     *
     * @param username account username
     * @param password account password
     * @param callback result callback
     */
    public void login(String username, String password, ApiCallback<AuthResponse> callback) {
        AuthRequest body = new AuthRequest(username, password);
        Request request = postRequest("/auth/login", body);
        executeAsync(request, AuthResponse.class, new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                if (result.getToken() != null) {
                    authToken = result.getToken();
                }
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    // -------------------------------------------------------------------------
    // RF Nodes
    // -------------------------------------------------------------------------

    /**
     * Retrieves the list of RF scanning nodes visible to the authenticated user.
     *
     * @param callback result callback delivering a list of {@link RfNode} objects
     */
    public void getRfNodes(ApiCallback<List<RfNode>> callback) {
        Request request = authenticatedGetRequest("/rf/nodes");
        Type listType = new TypeToken<List<RfNode>>() {}.getType();
        executeAsyncList(request, listType, callback);
    }

    // -------------------------------------------------------------------------
    // Cursor-on-Target (CoT)
    // -------------------------------------------------------------------------

    /**
     * Retrieves all CoT events currently stored on the server.
     *
     * @param callback result callback delivering a list of {@link CotEvent} objects
     */
    public void getCotEvents(ApiCallback<List<CotEvent>> callback) {
        Request request = authenticatedGetRequest("/cot/events");
        Type listType = new TypeToken<List<CotEvent>>() {}.getType();
        executeAsyncList(request, listType, callback);
    }

    /**
     * Publishes a new CoT event to the server.
     *
     * @param event    the event to publish
     * @param callback result callback
     */
    public void postCotEvent(CotEvent event, ApiCallback<CotEvent> callback) {
        Request request = authenticatedPostRequest("/cot/events", event);
        executeAsync(request, CotEvent.class, callback);
    }

    // -------------------------------------------------------------------------
    // Missions
    // -------------------------------------------------------------------------

    /**
     * Retrieves all missions accessible to the authenticated user.
     *
     * @param callback result callback delivering a list of {@link Mission} objects
     */
    public void getMissions(ApiCallback<List<Mission>> callback) {
        Request request = authenticatedGetRequest("/missions");
        Type listType = new TypeToken<List<Mission>>() {}.getType();
        executeAsyncList(request, listType, callback);
    }

    // -------------------------------------------------------------------------
    // Heatmap
    // -------------------------------------------------------------------------

    /**
     * Retrieves heatmap data for a specific mission.
     *
     * @param missionId the ID of the mission whose heatmap is requested
     * @param callback  result callback delivering a {@link HeatmapData} object
     */
    public void getHeatmap(String missionId, ApiCallback<HeatmapData> callback) {
        Request request = authenticatedGetRequest("/missions/" + missionId + "/heatmap");
        executeAsync(request, HeatmapData.class, callback);
    }

    // -------------------------------------------------------------------------
    // Token management
    // -------------------------------------------------------------------------

    /**
     * Explicitly sets the bearer token used for authenticated requests.
     * Useful when a token has been persisted and restored across sessions.
     *
     * @param token JWT bearer token
     */
    public void setAuthToken(String token) {
        this.authToken = token;
    }

    /**
     * Returns the currently stored bearer token, or {@code null} if the user
     * has not yet logged in during this session.
     */
    public String getAuthToken() {
        return authToken;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Request postRequest(String path, Object body) {
        String json = gson.toJson(body);
        RequestBody requestBody = RequestBody.create(json, JSON);
        return new Request.Builder()
                .url(baseUrl + path)
                .post(requestBody)
                .build();
    }

    private Request authenticatedGetRequest(String path) {
        Request.Builder builder = new Request.Builder().url(baseUrl + path).get();
        if (authToken != null) {
            builder.header(HEADER_AUTHORIZATION, BEARER_PREFIX + authToken);
        }
        return builder.build();
    }

    private Request authenticatedPostRequest(String path, Object body) {
        String json = gson.toJson(body);
        RequestBody requestBody = RequestBody.create(json, JSON);
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path)
                .post(requestBody);
        if (authToken != null) {
            builder.header(HEADER_AUTHORIZATION, BEARER_PREFIX + authToken);
        }
        return builder.build();
    }

    /** Executes a request asynchronously and deserializes the JSON body into {@code type}. */
    private <T> void executeAsync(Request request, Class<T> type, ApiCallback<T> callback) {
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        callback.onFailure("HTTP " + response.code() + ": " + response.message());
                        return;
                    }
                    if (responseBody == null) {
                        callback.onFailure("Empty response body");
                        return;
                    }
                    String bodyString = responseBody.string();
                    T result = gson.fromJson(bodyString, type);
                    callback.onSuccess(result);
                } catch (IOException | JsonSyntaxException e) {
                    callback.onFailure(e.getMessage());
                }
            }
        });
    }

    /** Executes a request asynchronously and deserializes the JSON body into a generic {@code Type}. */
    private <T> void executeAsyncList(Request request, Type type, ApiCallback<T> callback) {
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        callback.onFailure("HTTP " + response.code() + ": " + response.message());
                        return;
                    }
                    if (responseBody == null) {
                        callback.onFailure("Empty response body");
                        return;
                    }
                    String bodyString = responseBody.string();
                    T result = gson.fromJson(bodyString, type);
                    callback.onSuccess(result);
                } catch (IOException | JsonSyntaxException e) {
                    callback.onFailure(e.getMessage());
                }
            }
        });
    }

    private static OkHttpClient buildDefaultHttpClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();
    }
}
