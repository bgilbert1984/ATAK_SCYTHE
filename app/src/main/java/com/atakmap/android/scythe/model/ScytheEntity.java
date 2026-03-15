package com.atakmap.android.scythe.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Data Transfer Object for an entity delivered via SSE from:
 *   GET /api/entities/stream?token=X
 *
 * SSE event data shape (server):
 * {
 *   "id":       "entity-uuid",
 *   "type":     "rf_emitter" | "operator" | "mission_marker" | ...,
 *   "lat":      34.123,
 *   "lon":      -118.442,
 *   "alt":      0.0,
 *   "callsign": "BRAVO-1",
 *   "team":     "Cyan",
 *   "role":     "HQ",
 *   "seq":      981231,
 *   "ts":       1712350010,
 *   "metadata": { ... }
 * }
 *
 * SSE frame format:
 *   event: CREATE | UPDATE | DELETE | PREEXISTING | HEARTBEAT
 *   data: <JSON above>
 */
public class ScytheEntity {

    /** SSE event type */
    public enum EventType {
        PREEXISTING, CREATE, UPDATE, DELETE, HEARTBEAT, UNKNOWN
    }

    public final String    id;
    public final String    type;
    public final double    lat;
    public final double    lon;
    public final double    alt;
    public final String    callsign;
    public final String    team;
    public final String    role;
    public final long      seq;
    public final long      tsMs;
    public final EventType event;

    private ScytheEntity(Builder b) {
        this.id       = b.id;
        this.type     = b.type;
        this.lat      = b.lat;
        this.lon      = b.lon;
        this.alt      = b.alt;
        this.callsign = b.callsign;
        this.team     = b.team;
        this.role     = b.role;
        this.seq      = b.seq;
        this.tsMs     = b.tsMs;
        this.event    = b.event;
    }

    public boolean hasPosition() {
        return lat != 0 || lon != 0;
    }

    /** Convert entity type to ATAK CoT type string */
    public String cotType() {
        switch (type) {
            case "rf_emitter":      return "a-u-G-U-C-I";
            case "operator":        return "a-f-G-U-C";
            case "mission_marker":  return "a-n-G-I";
            case "drone":           return "a-u-A-C-F-q";
            case "sensor":          return "a-f-G-E-S";
            default:                return "a-u-G";
        }
    }

    public static ScytheEntity fromJson(JSONObject obj, EventType event) throws JSONException {
        Builder b = new Builder();
        b.id       = obj.optString("id", "");
        b.type     = obj.optString("type", "unknown");
        b.lat      = obj.optDouble("lat", 0);
        b.lon      = obj.optDouble("lon", 0);
        b.alt      = obj.optDouble("alt", 0);
        b.callsign = obj.optString("callsign", b.id);
        b.team     = obj.optString("team", "");
        b.role     = obj.optString("role", "");
        b.seq      = obj.optLong("seq", 0);
        b.tsMs     = (long) (obj.optDouble("ts", 0) * 1000);
        b.event    = event;
        return new ScytheEntity(b);
    }

    public static EventType parseEventType(String raw) {
        if (raw == null) return EventType.UNKNOWN;
        switch (raw.trim().toUpperCase()) {
            case "PREEXISTING": return EventType.PREEXISTING;
            case "CREATE":      return EventType.CREATE;
            case "UPDATE":      return EventType.UPDATE;
            case "DELETE":      return EventType.DELETE;
            case "HEARTBEAT":   return EventType.HEARTBEAT;
            default:            return EventType.UNKNOWN;
        }
    }

    private static class Builder {
        String id = "", type = "", callsign = "", team = "", role = "";
        double lat, lon, alt;
        long seq, tsMs;
        EventType event = EventType.UNKNOWN;
    }
}
