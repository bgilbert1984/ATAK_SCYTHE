package com.atakmap.android.scythe.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Data Transfer Object for an RF Hypergraph Node from rf_scythe_api_server.py.
 *
 * Maps the shape returned by:
 *   GET /api/rf-hypergraph/visualization
 *   GET /api/entities/stream (kind=rf_signal, rf_emitter, etc.)
 *
 * Node JSON shape (server):
 * {
 *   "id": "node-uuid",
 *   "kind": "rf_emitter" | "rf_signal" | "sensor" | ...,
 *   "position": [lat, lon, alt],
 *   "frequency": 2450000000.0,
 *   "labels": { "obs_class": "observed", "confidence": 0.87, ... },
 *   "metadata": { "rssi": -65.0, "signature": "wifi_ap", ... },
 *   "created_at": 1712350010.0,
 *   "updated_at": 1712350020.0
 * }
 */
public class RFNode {

    public final String id;
    public final String kind;
    public final double lat;
    public final double lon;
    public final double alt;
    public final double frequencyHz;
    public final double confidence;
    public final String obsClass;
    public final String callsign;
    public final double rssiDbm;
    public final long   updatedAt;

    private RFNode(Builder b) {
        this.id          = b.id;
        this.kind        = b.kind;
        this.lat         = b.lat;
        this.lon         = b.lon;
        this.alt         = b.alt;
        this.frequencyHz = b.frequencyHz;
        this.confidence  = b.confidence;
        this.obsClass    = b.obsClass != null ? b.obsClass : "unknown";
        this.callsign    = b.callsign != null && !b.callsign.isEmpty() ? b.callsign : b.id;
        this.rssiDbm     = b.rssiDbm;
        this.updatedAt   = b.updatedAt;
    }

    /** Return friendly frequency label e.g. "2.45 GHz" */
    public String frequencyLabel() {
        if (frequencyHz <= 0) return "";
        if (frequencyHz >= 1e9) return String.format("%.3f GHz", frequencyHz / 1e9);
        if (frequencyHz >= 1e6) return String.format("%.1f MHz", frequencyHz / 1e6);
        return String.format("%.0f kHz", frequencyHz / 1e3);
    }

    /** CoT type string for ATAK marker display */
    public String cotType() {
        switch (kind) {
            case "rf_emitter": return "a-u-G-U-C-I";
            case "sensor":     return "a-f-G-E-S";
            case "drone":      return "a-u-A-C-F-q";
            default:           return "a-u-G";
        }
    }

    /** Signal strength [0..1] for heat color rendering */
    public float normalizedStrength() {
        if (rssiDbm == 0) return 0.5f;
        float norm = (float) ((rssiDbm + 100.0) / 70.0);
        return Math.max(0f, Math.min(1f, norm));
    }

    public boolean hasPosition() {
        return lat != 0 || lon != 0;
    }

    // -------------------------------------------------------------------------
    // Builder (shared by fromJson and programmatic construction via SSE)
    // -------------------------------------------------------------------------

    public static final class Builder {
        String id = "", kind = "rf_signal", obsClass = "unknown", callsign = "";
        double lat, lon, alt, frequencyHz, confidence, rssiDbm;
        long updatedAt;

        public Builder id(String v)         { id = v; return this; }
        public Builder kind(String v)       { kind = v; return this; }
        public Builder obsClass(String v)   { obsClass = v; return this; }
        public Builder callsign(String v)   { callsign = v; return this; }
        public Builder lat(double v)        { lat = v; return this; }
        public Builder lon(double v)        { lon = v; return this; }
        public Builder alt(double v)        { alt = v; return this; }
        public Builder frequency(double v)  { frequencyHz = v; return this; }
        public Builder confidence(double v) { confidence = v; return this; }
        public Builder rssiDbm(double v)    { rssiDbm = v; return this; }
        public Builder updatedAt(long v)    { updatedAt = v; return this; }

        public RFNode build() { return new RFNode(this); }
    }

    // -------------------------------------------------------------------------
    // JSON deserialization
    // -------------------------------------------------------------------------

    public static RFNode fromJson(JSONObject obj) throws JSONException {
        Builder b = new Builder();
        b.id   = obj.optString("id", "");
        b.kind = obj.optString("kind", "rf_signal");

        JSONArray pos = obj.optJSONArray("position");
        if (pos != null && pos.length() >= 2) {
            b.lat = pos.optDouble(0, 0);
            b.lon = pos.optDouble(1, 0);
            b.alt = pos.length() >= 3 ? pos.optDouble(2, 0) : 0;
        }

        b.frequencyHz = obj.optDouble("frequency", 0);
        b.updatedAt   = (long) (obj.optDouble("updated_at", 0) * 1000);

        JSONObject labels = obj.optJSONObject("labels");
        if (labels != null) {
            b.confidence = labels.optDouble("confidence", 0);
            b.obsClass   = labels.optString("obs_class", "unknown");
            b.callsign   = labels.optString("callsign",
                           labels.optString("signature", b.id));
        }

        JSONObject meta = obj.optJSONObject("metadata");
        if (meta != null) {
            b.rssiDbm = meta.optDouble("rssi", meta.optDouble("rssi_dbm", 0));
            if (b.callsign.isEmpty()) {
                b.callsign = meta.optString("signature", b.id);
            }
        }

        return new RFNode(b);
    }
}
