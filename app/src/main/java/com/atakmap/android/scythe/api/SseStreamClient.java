package com.atakmap.android.scythe.api;

import android.util.Log;

import com.atakmap.android.scythe.model.ScytheEntity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Server-Sent Events (SSE) client for:
 *   GET /api/entities/stream?token={sessionToken}
 *
 * Runs on a dedicated background thread. Automatically reconnects on
 * disconnect with exponential backoff (1s → 2s → 4s … max 30s).
 *
 * SSE frame format from server:
 *   event: CREATE
 *   data: {"id":"x","type":"rf_emitter","lat":34.1,"lon":-118.4,...}
 *
 * Dispatches parsed {@link ScytheEntity} objects to registered
 * {@link EntityListener}s on the same background thread; callers must
 * post to main thread if they update UI.
 */
public class SseStreamClient {

    private static final String TAG        = "SseStreamClient";
    private static final int    MAX_BACKOFF_MS = 30_000;

    public interface EntityListener {
        void onEntity(ScytheEntity entity);
        void onConnected();
        void onDisconnected(String reason);
    }

    private final OkHttpClient     http;
    private final AtomicBoolean    running  = new AtomicBoolean(false);
    private final AtomicBoolean    stopping = new AtomicBoolean(false);
    private       EntityListener   listener;
    private volatile String        streamUrl;
    private volatile String        sessionToken;
    private Thread                 streamThread;

    public SseStreamClient(String baseUrl, String sessionToken) {
        this.streamUrl    = baseUrl + "/api/entities/stream";
        this.sessionToken = sessionToken;
        // Long read timeout: SSE is a long-lived connection
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)   // infinite for SSE
                .build();
    }

    public void setListener(EntityListener l) {
        this.listener = l;
    }

    public void updateEndpoint(String baseUrl, String token) {
        this.streamUrl    = baseUrl + "/api/entities/stream";
        this.sessionToken = token;
    }

    public boolean isRunning() { return running.get(); }

    /** Start the SSE stream on a background thread. Idempotent. */
    public synchronized void start() {
        if (running.get()) return;
        stopping.set(false);
        streamThread = new Thread(this::runLoop, "SseScytheStream");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    /** Gracefully stop the SSE stream. */
    public synchronized void stop() {
        stopping.set(true);
        http.dispatcher().cancelAll();
        if (streamThread != null) {
            streamThread.interrupt();
        }
    }

    // -------------------------------------------------------------------------

    private void runLoop() {
        running.set(true);
        int backoffMs = 1000;

        while (!stopping.get()) {
            try {
                connect();
                backoffMs = 1000; // reset on successful connection
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.w(TAG, "SSE error: " + e.getMessage());
                if (listener != null) listener.onDisconnected(e.getMessage());
            }

            if (!stopping.get()) {
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            }
        }

        running.set(false);
        Log.d(TAG, "SSE stream stopped");
    }

    private void connect() throws IOException {
        String url = streamUrl;
        String tok = sessionToken;
        if (tok != null && !tok.isEmpty()) {
            url += "?token=" + tok;
        }

        Log.d(TAG, "SSE connecting: " + url);

        Request req = new Request.Builder()
                .url(url)
                .addHeader("Accept", "text/event-stream")
                .addHeader("Cache-Control", "no-cache")
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + " from SSE endpoint");
            }
            if (listener != null) listener.onConnected();

            InputStream is = resp.body().byteStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            String eventType = null;
            StringBuilder dataBuilder = new StringBuilder();

            String line;
            while (!stopping.get() && (line = reader.readLine()) != null) {
                if (line.startsWith("event:")) {
                    eventType = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    dataBuilder.append(line.substring(5).trim());
                } else if (line.isEmpty() && dataBuilder.length() > 0) {
                    // Empty line = end of SSE frame — dispatch
                    dispatchEvent(eventType, dataBuilder.toString());
                    eventType = null;
                    dataBuilder.setLength(0);
                }
                // ":" comment lines (heartbeat pings) are silently ignored
            }
        }
    }

    private void dispatchEvent(String eventTypeStr, String data) {
        ScytheEntity.EventType evType = ScytheEntity.parseEventType(eventTypeStr);
        if (evType == ScytheEntity.EventType.HEARTBEAT) return;
        if (listener == null) return;

        try {
            JSONObject json = new JSONObject(data);
            ScytheEntity entity = ScytheEntity.fromJson(json, evType);
            listener.onEntity(entity);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse SSE entity: " + e.getMessage()
                    + " | raw: " + data);
        }
    }
}
