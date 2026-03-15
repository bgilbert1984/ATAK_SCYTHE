package com.atakmap.android.scythe.layer;

import com.atakmap.android.scythe.model.CyberCluster;
import com.atakmap.map.layer.AbstractLayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ATAK map layer holding live CyberCluster swarm objects.
 *
 * Updated by ScytheMapComponent from the SSE swarm stream and REST refreshes.
 * The GL renderer {@link GLSwarmLayer} reads snapshots on the GL thread.
 *
 * Thread-safe via synchronized(clusters).
 */
public class SwarmLayer extends AbstractLayer {

    public static final String TAG  = "SwarmLayer";
    public static final String NAME = "RF Scythe Swarms";

    private final Map<String, CyberCluster> clusters = new LinkedHashMap<>();
    private volatile boolean dirty = false;

    public SwarmLayer() {
        super(NAME);
        setVisible(true);
    }

    // -------------------------------------------------------------------------

    public synchronized void updateAll(List<CyberCluster> incoming) {
        clusters.clear();
        for (CyberCluster c : incoming) {
            if (c.hasPosition()) clusters.put(c.id, c);
        }
        dirty = true;
    }

    public synchronized void upsert(CyberCluster c) {
        if (c.hasPosition()) { clusters.put(c.id, c); dirty = true; }
    }

    public synchronized void remove(String id) {
        if (clusters.remove(id) != null) dirty = true;
    }

    public synchronized List<CyberCluster> getSnapshot() {
        return new ArrayList<>(clusters.values());
    }

    public synchronized boolean isDirtyAndReset() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public synchronized int size() { return clusters.size(); }
}
