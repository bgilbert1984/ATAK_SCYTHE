package com.atakmap.android.scythe;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.scythe.layer.GLRFSignalLayer;
import com.atakmap.android.scythe.layer.GLSwarmLayer;
import com.atakmap.android.scythe.layer.RFSignalLayer;
import com.atakmap.android.scythe.layer.SwarmLayer;
import com.atakmap.coremap.log.Log;

/**
 * ATAK plugin Map Component.
 *
 * Lifecycle:
 *   onCreate  → create RFSignalLayer + SwarmLayer, register GL renderer SPIs,
 *               add layers to map, register DropDown intent receiver
 *   onDestroy → unregister receiver, remove layers, stop network clients
 *
 * The plugin has no native dependencies — all networking is pure Java/OkHttp.
 * ELF 16KB compliance is therefore enforced by packaging flags only
 * (extractNativeLibs="false", useLegacyPackaging=false).
 */
public class ScytheMapComponent extends DropDownMapComponent {

    public static final String TAG = "ScytheMapComponent";

    private RFSignalLayer          rfLayer;
    private SwarmLayer             swarmLayer;
    private ScytheDropDownReceiver dropDown;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        super.onCreate(context, intent, view);

        Log.d(TAG, "ScytheMapComponent.onCreate");

        // ----------------------------------------------------------------
        // 1. Create RF signal map layer
        // ----------------------------------------------------------------
        rfLayer = new RFSignalLayer();
        com.atakmap.map.layer.opengl.GLLayerFactory.register(GLRFSignalLayer.SPI);
        view.addLayer(MapView.RenderStack.WIDGETS, rfLayer);
        Log.d(TAG, "RFSignalLayer added to map");

        // ----------------------------------------------------------------
        // 2. Create Swarm overlay layer
        // ----------------------------------------------------------------
        swarmLayer = new SwarmLayer();
        com.atakmap.map.layer.opengl.GLLayerFactory.register(GLSwarmLayer.SPI);
        view.addLayer(MapView.RenderStack.WIDGETS, swarmLayer);
        Log.d(TAG, "SwarmLayer added to map");

        // ----------------------------------------------------------------
        // 3. Create drop-down receiver
        // ----------------------------------------------------------------
        dropDown = new ScytheDropDownReceiver(view, context, rfLayer, swarmLayer);

        // Register broadcast intent that opens the panel
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(ScytheDropDownReceiver.SHOW_DROPDOWN,
                "Opens the RF Scythe control panel");
        AtakBroadcast.getInstance().registerReceiver(dropDown, filter);

        Log.d(TAG, "ScytheDropDownReceiver registered");
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        Log.d(TAG, "ScytheMapComponent.onDestroy");

        if (dropDown != null) {
            dropDown.shutdown();
            AtakBroadcast.getInstance().unregisterReceiver(dropDown);
            dropDown = null;
        }

        if (rfLayer != null) {
            view.removeLayer(MapView.RenderStack.WIDGETS, rfLayer);
            rfLayer = null;
        }

        if (swarmLayer != null) {
            view.removeLayer(MapView.RenderStack.WIDGETS, swarmLayer);
            swarmLayer = null;
        }

        super.onDestroyImpl(context, view);
    }
}
