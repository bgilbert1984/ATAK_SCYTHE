package com.atakmap.android.scythe.layer;

import com.atakmap.android.scythe.model.RFNode;
import com.atakmap.map.layer.AbstractLayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ATAK map layer that holds RF Hypergraph Nodes from rf_scythe_api_server.py.
 *
 * Data is updated on any thread; the GLRFSignalLayer renderer reads on the
 * GL thread — updates are protected by synchronized(nodes).
 *
 * Layer registration in ScytheMapComponent:
 *   mapView.addLayer(MapView.RenderStack.WIDGETS, new RFSignalLayer());
 *
 * The matching GL renderer {@link GLRFSignalLayer} is registered via its SPI.
 */
public class RFSignalLayer extends AbstractLayer {

    public static final String TAG  = "RFSignalLayer";
    public static final String NAME = "RF Scythe Signals";

    // Node map keyed by id for O(1) update/delete
    private final Map<String, RFNode> nodes = new LinkedHashMap<>();
    private volatile boolean dirty = false;

    public RFSignalLayer() {
        super(NAME);
        setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Data access (thread-safe)
    // -------------------------------------------------------------------------

    public synchronized void updateNodes(List<RFNode> incoming) {
        nodes.clear();
        for (RFNode n : incoming) {
            if (n.hasPosition()) nodes.put(n.id, n);
        }
        dirty = true;
        dispatchOnLayerVisible(this, isVisible()); // nudge listeners
    }

    public synchronized void upsertNode(RFNode node) {
        if (node.hasPosition()) {
            nodes.put(node.id, node);
            dirty = true;
        }
    }

    public synchronized void removeNode(String id) {
        if (nodes.remove(id) != null) dirty = true;
    }

    /** Returns a snapshot copy for GL thread rendering. */
    public synchronized List<RFNode> getSnapshot() {
        return new ArrayList<>(nodes.values());
    }

    public synchronized boolean isDirtyAndReset() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public synchronized int size() { return nodes.size(); }
}
