package com.atakmap.android.scythe.api;

import android.util.Log;

import com.atakmap.android.scythe.model.RFNode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * REST client for rf_scythe_api_server.py (default port 8080).
 *
 * All network calls are dispatched asynchronously via OkHttp's dispatcher.
 * Callers supply a typed {@link ApiCallback} — never block the UI thread.
 *
 * Thread-safe: single OkHttpClient instance shared across calls.
 */
public class ScytheApiClient {

    private static final String TAG = "ScytheApiClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private final OkHttpClient http;
    private volatile String baseUrl;
    private volatile String sessionToken;

    public ScytheApiClient(String host, int port) {
        this.baseUrl = "http://" + host + ":" + port;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public void updateEndpoint(String host, int port) {
        this.baseUrl = "http://" + host + ":" + port;
    }

    public String getBaseUrl() { return baseUrl; }
    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String t) { sessionToken = t; }

    // -------------------------------------------------------------------------
    // Operator auth
    // -------------------------------------------------------------------------

    /**
     * POST /api/operator/register
     * Body: { "callsign": X, "role": "operator", "team": "Cyan" }
     * On success: stores returned token, calls back with it.
     */
    public void register(String callsign, String role, String team,
                         ApiCallback<String> cb) {
        try {
            JSONObject body = new JSONObject()
                    .put("callsign", callsign)
                    .put("role", role)
                    .put("team", team);
            post("/api/operator/register", body, new Callback() {
                @Override public void onFailure(okhttp3.Call call, IOException e) {
                    cb.onError(e.getMessage());
                }
                @Override public void onResponse(okhttp3.Call call, Response resp) {
                    try {
                        String raw = resp.body().string();
                        JSONObject json = new JSONObject(raw);
                        if ("ok".equals(json.optString("status"))) {
                            sessionToken = json.optString("token", "");
                            cb.onSuccess(sessionToken);
                        } else {
                            cb.onError(json.optString("message", "register failed"));
                        }
                    } catch (Exception e) {
                        cb.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
    }

    /**
     * POST /api/operator/login
     * Body: { "callsign": X }
     */
    public void login(String callsign, ApiCallback<String> cb) {
        try {
            JSONObject body = new JSONObject().put("callsign", callsign);
            post("/api/operator/login", body, new Callback() {
                @Override public void onFailure(okhttp3.Call call, IOException e) {
                    cb.onError(e.getMessage());
                }
                @Override public void onResponse(okhttp3.Call call, Response resp) {
                    try {
                        String raw = resp.body().string();
                        JSONObject json = new JSONObject(raw);
                        if ("ok".equals(json.optString("status"))) {
                            sessionToken = json.optString("token", "");
                            cb.onSuccess(sessionToken);
                        } else {
                            cb.onError(json.optString("message", "login failed"));
                        }
                    } catch (Exception e) {
                        cb.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // RF Hypergraph
    // -------------------------------------------------------------------------

    /**
     * GET /api/rf-hypergraph/visualization
     * Returns list of RFNodes with position and signal metadata.
     */
    public void getRfNodes(ApiCallback<List<RFNode>> cb) {
        get("/api/rf-hypergraph/visualization", new Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                cb.onError(e.getMessage());
            }
            @Override public void onResponse(okhttp3.Call call, Response resp) {
                try {
                    JSONObject json = new JSONObject(resp.body().string());
                    JSONArray nodes = json.optJSONArray("nodes");
                    if (nodes == null) {
                        // fallback: server may return flat array
                        nodes = new JSONArray(json.toString());
                    }
                    List<RFNode> result = new ArrayList<>();
                    for (int i = 0; i < nodes.length(); i++) {
                        try {
                            result.add(RFNode.fromJson(nodes.getJSONObject(i)));
                        } catch (Exception ignore) { /* skip malformed node */ }
                    }
                    cb.onSuccess(result);
                } catch (Exception e) {
                    cb.onError(e.getMessage());
                }
            }
        });
    }

    /**
     * GET /api/rf-hypergraph/status
     */
    public void getStatus(ApiCallback<JSONObject> cb) {
        get("/api/rf-hypergraph/status", new Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                cb.onError(e.getMessage());
            }
            @Override public void onResponse(okhttp3.Call call, Response resp) {
                try {
                    cb.onSuccess(new JSONObject(resp.body().string()));
                } catch (Exception e) {
                    cb.onError(e.getMessage());
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // CoT export / push
    // -------------------------------------------------------------------------

    /**
     * GET /api/tak/cot?format=xml_list
     * Returns CoT XML events for all RF nodes.
     */
    public void getCotXml(ApiCallback<List<String>> cb) {
        get("/api/tak/cot?format=xml_list", new Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                cb.onError(e.getMessage());
            }
            @Override public void onResponse(okhttp3.Call call, Response resp) {
                try {
                    JSONObject json = new JSONObject(resp.body().string());
                    JSONArray events = json.optJSONArray("events");
                    List<String> result = new ArrayList<>();
                    if (events != null) {
                        for (int i = 0; i < events.length(); i++) {
                            result.add(events.optString(i));
                        }
                    }
                    cb.onSuccess(result);
                } catch (Exception e) {
                    cb.onError(e.getMessage());
                }
            }
        });
    }

    /**
     * POST /api/tak/send — instructs server to push CoT via UDP/TCP to TAK network.
     * Body: { "protocol": "udp", "host": "239.2.3.1", "port": 6969 }
     */
    public void sendCotToTakNetwork(String protocol, String host, int port,
                                     ApiCallback<Integer> cb) {
        try {
            JSONObject body = new JSONObject()
                    .put("protocol", protocol)
                    .put("host", host)
                    .put("port", port);
            post("/api/tak/send", body, new Callback() {
                @Override public void onFailure(okhttp3.Call call, IOException e) {
                    cb.onError(e.getMessage());
                }
                @Override public void onResponse(okhttp3.Call call, Response resp) {
                    try {
                        JSONObject json = new JSONObject(resp.body().string());
                        cb.onSuccess(json.optInt("sent", 0));
                    } catch (Exception e) {
                        cb.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Missions
    // -------------------------------------------------------------------------

    /**
     * GET /api/missions — list active missions (via operator session).
     * Returns raw JSON array of mission objects.
     */
    public void getMissions(ApiCallback<JSONArray> cb) {
        getWithAuth("/api/missions", new Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                cb.onError(e.getMessage());
            }
            @Override public void onResponse(okhttp3.Call call, Response resp) {
                try {
                    JSONObject json = new JSONObject(resp.body().string());
                    // Server may return { "missions": [...] } or direct array
                    JSONArray arr = json.optJSONArray("missions");
                    if (arr == null) arr = new JSONArray();
                    cb.onSuccess(arr);
                } catch (Exception e) {
                    cb.onError(e.getMessage());
                }
            }
        });
    }

    /**
     * POST /api/missions
     * Body: { "name": X, "description": Y, "operator_token": token }
     */
    public void createMission(String name, String description, ApiCallback<String> cb) {
        try {
            JSONObject body = new JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("operator_token", sessionToken != null ? sessionToken : "");
            post("/api/missions", body, new Callback() {
                @Override public void onFailure(okhttp3.Call call, IOException e) {
                    cb.onError(e.getMessage());
                }
                @Override public void onResponse(okhttp3.Call call, Response resp) {
                    try {
                        JSONObject json = new JSONObject(resp.body().string());
                        String missionId = json.optString("mission_id", "");
                        if (!missionId.isEmpty()) {
                            cb.onSuccess(missionId);
                        } else {
                            cb.onError(json.optString("message", "create failed"));
                        }
                    } catch (Exception e) {
                        cb.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Heatmap
    // -------------------------------------------------------------------------

    /**
     * GET /api/geo/heatmap?format=kml
     * Returns KML string for import into ATAK as a layer overlay.
     */
    public void getHeatmapKml(ApiCallback<String> cb) {
        get("/api/geo/heatmap?format=kml", new Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                cb.onError(e.getMessage());
            }
            @Override public void onResponse(okhttp3.Call call, Response resp) {
                try {
                    cb.onSuccess(resp.body().string());
                } catch (Exception e) {
                    cb.onError(e.getMessage());
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void get(String path, Callback callback) {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .get()
                .build();
        http.newCall(req).enqueue(callback);
    }

    private void getWithAuth(String path, Callback callback) {
        Request.Builder builder = new Request.Builder().url(baseUrl + path).get();
        if (sessionToken != null && !sessionToken.isEmpty()) {
            builder.addHeader("X-Session-Token", sessionToken);
        }
        http.newCall(builder.build()).enqueue(callback);
    }

    private void post(String path, JSONObject body, Callback callback) {
        RequestBody rb = RequestBody.create(body.toString(), JSON);
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path)
                .post(rb);
        if (sessionToken != null && !sessionToken.isEmpty()) {
            builder.addHeader("X-Session-Token", sessionToken);
        }
        http.newCall(builder.build()).enqueue(callback);
    }

    /** Cancel all in-flight requests and shut down the dispatcher. */
    public void shutdown() {
        http.dispatcher().cancelAll();
        http.connectionPool().evictAll();
    }

    // -------------------------------------------------------------------------
    // Swarm Cluster API
    // -------------------------------------------------------------------------

    /**
     * GET /api/clusters/swarms
     * Returns current CyberCluster list (JSON array under "clusters" key).
     */
    public void getSwarms(ApiCallback<List<com.atakmap.android.scythe.model.CyberCluster>> cb) {
        getWithAuth("/api/clusters/swarms", new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                cb.onError("getSwarms network error: " + e.getMessage());
            }
            @Override public void onResponse(okhttp3.Call call, Response resp) throws IOException {
                if (!resp.isSuccessful()) {
                    cb.onError("getSwarms HTTP " + resp.code());
                    return;
                }
                try {
                    String body = resp.body().string();
                    JSONObject root = new JSONObject(body);
                    JSONArray arr = root.optJSONArray("clusters");
                    List<com.atakmap.android.scythe.model.CyberCluster> list = new ArrayList<>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            try {
                                list.add(com.atakmap.android.scythe.model.CyberCluster
                                        .fromJson(arr.getJSONObject(i)));
                            } catch (Exception ex) {
                                Log.w(TAG, "getSwarms parse error at " + i, ex);
                            }
                        }
                    }
                    cb.onSuccess(list);
                } catch (Exception e) {
                    cb.onError("getSwarms parse error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * GET /api/clusters/swarms/cot
     * Returns all swarm clusters as CoT XML list; callers inject directly into ATAK.
     */
    public void getSwarmCot(ApiCallback<List<String>> cb) {
        getWithAuth("/api/clusters/swarms/cot", new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                cb.onError("getSwarmCot network error: " + e.getMessage());
            }
            @Override public void onResponse(okhttp3.Call call, Response resp) throws IOException {
                if (!resp.isSuccessful()) {
                    cb.onError("getSwarmCot HTTP " + resp.code());
                    return;
                }
                try {
                    String body = resp.body().string();
                    JSONObject root = new JSONObject(body);
                    JSONArray arr = root.optJSONArray("events");
                    List<String> events = new ArrayList<>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) events.add(arr.getString(i));
                    }
                    cb.onSuccess(events);
                } catch (Exception e) {
                    cb.onError("getSwarmCot parse: " + e.getMessage());
                }
            }
        });
    }
}
