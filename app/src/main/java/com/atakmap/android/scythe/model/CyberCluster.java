package com.atakmap.android.scythe.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Data Transfer Object for a CyberCluster swarm object.
 *
 * Maps the shape returned by:
 *   GET /api/clusters/swarms
 *   GET /api/clusters/swarms/stream  (SSE SWARM_SNAPSHOT event)
 *
 * JSON shape (server):
 * {
 *   "id":            "swarm-bd67ea5f",
 *   "centroid_lat":  34.137,
 *   "centroid_lon":  -117.9,
 *   "node_count":    12004,
 *   "threat_score":  0.92,
 *   "rf_emitters":   12,
 *   "uav_count":     3,
 *   "asn":           "AS4134",
 *   "behavior_type": "BOTNET" | "BEACON" | "SCAN" | "RF_SWARM" | "MIXED",
 *   "velocity_dx":   0.000012,
 *   "velocity_dy":  -0.000005,
 *   "radius_m":      267000.0,
 *   "threat_label":  "CRITICAL" | "HIGH" | "MEDIUM" | "LOW",
 *   "cot_type":      "cyber.botnet.swarm"
 * }
 */
public class CyberCluster {

    public final String id;
    public final double centroidLat;
    public final double centroidLon;
    public final int    nodeCount;
    public final double threatScore;    // 0.0 → 1.0
    public final int    rfEmitters;
    public final int    uavCount;
    public final String asn;
    public final String behaviorType;  // BOTNET | BEACON | SCAN | RF_SWARM | MIXED
    public final double velocityDx;    // longitudinal drift deg/s
    public final double velocityDy;    // latitudinal drift deg/s
    public final double radiusM;       // visual radius in metres
    public final String threatLabel;   // CRITICAL | HIGH | MEDIUM | LOW
    public final String cotType;
    public final long   updatedAtMs;

    private CyberCluster(Builder b) {
        this.id           = b.id;
        this.centroidLat  = b.centroidLat;
        this.centroidLon  = b.centroidLon;
        this.nodeCount    = b.nodeCount;
        this.threatScore  = b.threatScore;
        this.rfEmitters   = b.rfEmitters;
        this.uavCount     = b.uavCount;
        this.asn          = b.asn;
        this.behaviorType = b.behaviorType;
        this.velocityDx   = b.velocityDx;
        this.velocityDy   = b.velocityDy;
        this.radiusM      = b.radiusM > 0 ? b.radiusM : computeRadius(b.nodeCount);
        this.threatLabel  = b.threatLabel.isEmpty()
                            ? labelFromScore(b.threatScore) : b.threatLabel;
        this.cotType      = b.cotType;
        this.updatedAtMs  = b.updatedAtMs;
    }

    // -------------------------------------------------------------------------
    // Display helpers
    // -------------------------------------------------------------------------

    /** ARGB color encoding threat severity for GL rendering. */
    public int threatColor() {
        if (threatScore >= 0.8) return 0xFFFF3333;  // red    CRITICAL
        if (threatScore >= 0.6) return 0xFFFF9900;  // orange HIGH
        if (threatScore >= 0.4) return 0xFFFFCC00;  // yellow MEDIUM
        return 0xFF33CC66;                           // green  LOW
    }

    /** Radius in metres → screen pixels at given metres-per-pixel scale. */
    public float radiusPx(double metersPerPixel) {
        return (float) (radiusM / Math.max(1.0, metersPerPixel));
    }

    public String displayLabel() {
        return behaviorType + " [" + nodeCount + "]";
    }

    public boolean hasPosition() {
        return centroidLat != 0 || centroidLon != 0;
    }

    // -------------------------------------------------------------------------
    // Static helpers
    // -------------------------------------------------------------------------

    private static double computeRadius(int count) {
        if (count <= 1) return 200.0;
        return Math.max(200.0, Math.log10(count) * 100_000.0);
    }

    private static String labelFromScore(double s) {
        if (s >= 0.8) return "CRITICAL";
        if (s >= 0.6) return "HIGH";
        if (s >= 0.4) return "MEDIUM";
        return "LOW";
    }

    // -------------------------------------------------------------------------
    // Deserialization
    // -------------------------------------------------------------------------

    public static CyberCluster fromJson(JSONObject obj) throws JSONException {
        Builder b = new Builder();
        b.id           = obj.optString("id", "swarm-unknown");
        b.centroidLat  = obj.optDouble("centroid_lat", 0);
        b.centroidLon  = obj.optDouble("centroid_lon", 0);
        b.nodeCount    = obj.optInt("node_count", 0);
        b.threatScore  = obj.optDouble("threat_score", 0);
        b.rfEmitters   = obj.optInt("rf_emitters", 0);
        b.uavCount     = obj.optInt("uav_count", 0);
        b.asn          = obj.optString("asn", "");
        b.behaviorType = obj.optString("behavior_type", "MIXED");
        b.velocityDx   = obj.optDouble("velocity_dx", 0);
        b.velocityDy   = obj.optDouble("velocity_dy", 0);
        b.radiusM      = obj.optDouble("radius_m", 0);
        b.threatLabel  = obj.optString("threat_label", "");
        b.cotType      = obj.optString("cot_type", "cyber.mixed.cluster");
        b.updatedAtMs  = (long) (obj.optDouble("updated_at", 0) * 1000);
        return new CyberCluster(b);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        String id = "", asn = "", behaviorType = "MIXED",
               threatLabel = "", cotType = "cyber.mixed.cluster";
        double centroidLat, centroidLon, threatScore, velocityDx,
               velocityDy, radiusM;
        int    nodeCount, rfEmitters, uavCount;
        long   updatedAtMs;

        public Builder id(String v)           { id = v; return this; }
        public Builder centroidLat(double v)  { centroidLat = v; return this; }
        public Builder centroidLon(double v)  { centroidLon = v; return this; }
        public Builder nodeCount(int v)       { nodeCount = v; return this; }
        public Builder threatScore(double v)  { threatScore = v; return this; }
        public Builder rfEmitters(int v)      { rfEmitters = v; return this; }
        public Builder uavCount(int v)        { uavCount = v; return this; }
        public Builder asn(String v)          { asn = v; return this; }
        public Builder behaviorType(String v) { behaviorType = v; return this; }
        public Builder cotType(String v)      { cotType = v; return this; }
        public Builder radiusM(double v)      { radiusM = v; return this; }
        public Builder threatLabel(String v)  { threatLabel = v; return this; }
        public Builder updatedAtMs(long v)    { updatedAtMs = v; return this; }

        public CyberCluster build()           { return new CyberCluster(this); }
    }
}
