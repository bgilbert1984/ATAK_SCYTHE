package com.atakmap.android.scythe;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.scythe.api.ScytheApiClient;
import com.atakmap.android.scythe.api.SseStreamClient;
import com.atakmap.android.scythe.layer.RFSignalLayer;
import com.atakmap.android.scythe.layer.SwarmLayer;
import com.atakmap.android.scythe.model.CyberCluster;
import com.atakmap.android.scythe.model.RFNode;
import com.atakmap.android.scythe.model.ScytheEntity;
import com.atakmap.coremap.log.Log;

import com.atakmap.android.scythe.plugin.BuildConfig;
import com.atakmap.android.scythe.plugin.R;

import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Main plugin UI panel — shown as ATAK drop-down drawer.
 *
 * Four tabs:
 *   CONNECT  — server address, operator callsign, register/login
 *   RF INTEL — live RF node list, CoT injection, push to TAK network
 *   MISSIONS — list + create missions from rf_scythe_api_server
 *   SWARMS   — live CyberCluster swarm list + CoT injection
 *
 * Opened via {@link ScytheTool} toolbar button or broadcast intent:
 *   com.atakmap.android.scythe.SHOW_DROPDOWN
 */
public class ScytheDropDownReceiver extends DropDownReceiver
        implements DropDown.OnStateListener,
                   SseStreamClient.EntityListener {

    public static final String TAG            = "ScytheDropDown";
    public static final String SHOW_DROPDOWN  = "com.atakmap.android.scythe.SHOW_DROPDOWN";

    private final Handler      uiHandler = new Handler(Looper.getMainLooper());
    private final Context      pluginCtx;
    private final RFSignalLayer rfLayer;
    private final SwarmLayer   swarmLayer;

    private ScytheApiClient   apiClient;
    private SseStreamClient   sseClient;
    private boolean            connected = false;

    // UI references (non-null while panel is open)
    private View       rootView;
    private TabLayout  tabLayout;
    private View       panelConnect, panelRf, panelMissions, panelSwarms;

    // Connect tab
    private EditText editHost, editPort, editCallsign;
    private Button   btnConnect;
    private TextView txtConnectLog, txtStatusBadge;

    // RF tab
    private TextView txtNodeCount;
    private ListView listRfNodes;
    private EditText editTakHost;

    // Missions tab
    private ListView listMissions;

    // Swarms tab
    private ListView listSwarms;
    private TextView txtSwarmCount, txtSwarmStreamStatus;

    // -------------------------------------------------------------------------

    public ScytheDropDownReceiver(MapView mapView, Context pluginCtx,
                                   RFSignalLayer rfLayer, SwarmLayer swarmLayer) {
        super(mapView);
        this.pluginCtx  = pluginCtx;
        this.rfLayer    = rfLayer;
        this.swarmLayer = swarmLayer;

        // Create API + SSE clients with defaults (updated on connect)
        apiClient = new ScytheApiClient(
                BuildConfig.DEFAULT_SCYTHE_HOST,
                BuildConfig.DEFAULT_SCYTHE_PORT);
        sseClient = new SseStreamClient(apiClient.getBaseUrl(), null);
        sseClient.setListener(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (SHOW_DROPDOWN.equals(action)) {
            showDropDown(getView(),
                    HALF_WIDTH, FULL_HEIGHT,
                    FULL_WIDTH, HALF_HEIGHT,
                    false, this);
        }
    }

    // -------------------------------------------------------------------------
    // View construction
    // -------------------------------------------------------------------------

    private View getView() {
        if (rootView == null) {
            rootView = LayoutInflater.from(pluginCtx)
                    .inflate(R.layout.scythe_dropdown, null);
            bindViews();
        }
        return rootView;
    }

    private void bindViews() {
        tabLayout     = rootView.findViewById(R.id.tab_layout);
        txtStatusBadge = rootView.findViewById(R.id.txt_status_badge);

        panelConnect  = rootView.findViewById(R.id.panel_connect);
        panelRf       = rootView.findViewById(R.id.panel_rf);
        panelMissions = rootView.findViewById(R.id.panel_missions);
        panelSwarms   = rootView.findViewById(R.id.panel_swarms);

        // Connect tab
        editHost      = rootView.findViewById(R.id.edit_host);
        editPort      = rootView.findViewById(R.id.edit_port);
        editCallsign  = rootView.findViewById(R.id.edit_callsign);
        btnConnect    = rootView.findViewById(R.id.btn_connect);
        txtConnectLog = rootView.findViewById(R.id.txt_connect_log);

        // RF tab
        txtNodeCount  = rootView.findViewById(R.id.txt_node_count);
        listRfNodes   = rootView.findViewById(R.id.list_rf_nodes);
        editTakHost   = rootView.findViewById(R.id.edit_tak_host);

        // Missions tab
        listMissions  = rootView.findViewById(R.id.list_missions);

        // Swarms tab
        listSwarms         = rootView.findViewById(R.id.list_swarms);
        txtSwarmCount      = rootView.findViewById(R.id.txt_swarm_count);
        txtSwarmStreamStatus = rootView.findViewById(R.id.txt_swarm_stream_status);

        // Pre-fill with build defaults
        editHost.setText(BuildConfig.DEFAULT_SCYTHE_HOST);
        editPort.setText(String.valueOf(BuildConfig.DEFAULT_SCYTHE_PORT));

        // Tab selection
        tabLayout.addTab(tabLayout.newTab().setText("CONNECT"));
        tabLayout.addTab(tabLayout.newTab().setText("RF INTEL"));
        tabLayout.addTab(tabLayout.newTab().setText("MISSIONS"));
        tabLayout.addTab(tabLayout.newTab().setText("SWARMS"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                showPanel(tab.getPosition());
                if (tab.getPosition() == 1 && connected) doRefreshRf();
                if (tab.getPosition() == 2 && connected) doRefreshMissions();
                if (tab.getPosition() == 3 && connected) doRefreshSwarms();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Buttons
        btnConnect.setOnClickListener(v -> doConnect());
        rootView.findViewById(R.id.btn_refresh_rf).setOnClickListener(v -> doRefreshRf());
        rootView.findViewById(R.id.btn_inject_cot).setOnClickListener(v -> doInjectCot());
        rootView.findViewById(R.id.btn_push_tak).setOnClickListener(v -> doPushTak());
        rootView.findViewById(R.id.btn_refresh_missions).setOnClickListener(v -> doRefreshMissions());
        rootView.findViewById(R.id.btn_new_mission).setOnClickListener(v -> doNewMission());
        rootView.findViewById(R.id.btn_refresh_swarms).setOnClickListener(v -> doRefreshSwarms());
        rootView.findViewById(R.id.btn_inject_swarm_cot).setOnClickListener(v -> doInjectSwarmCot());
    }

    private void showPanel(int index) {
        panelConnect.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        panelRf.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        panelMissions.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        panelSwarms.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
    }

    // -------------------------------------------------------------------------
    // Connect tab actions
    // -------------------------------------------------------------------------

    private void doConnect() {
        String host = editHost.getText().toString().trim();
        String portStr = editPort.getText().toString().trim();
        String callsign = editCallsign.getText().toString().trim();

        if (host.isEmpty() || portStr.isEmpty()) {
            appendLog("⚠ Host and port required");
            return;
        }
        if (callsign.isEmpty()) callsign = "ALPHA-1";

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            appendLog("⚠ Invalid port");
            return;
        }

        btnConnect.setEnabled(false);
        appendLog("→ Connecting to " + host + ":" + port + " …");

        // Update clients with new endpoint
        apiClient.updateEndpoint(host, port);

        final String finalCallsign = callsign;
        apiClient.register(finalCallsign, "operator", "Cyan",
                new ScytheApiClient.ApiCallback<String>() {
            @Override public void onSuccess(String token) {
                Log.d(TAG, "Registered. Token: " + token);
                sseClient.updateEndpoint(apiClient.getBaseUrl(), token);
                sseClient.start();
                uiHandler.post(() -> {
                    connected = true;
                    btnConnect.setEnabled(true);
                    btnConnect.setText("RECONNECT");
                    setStatusBadge(true, "STREAMING");
                    appendLog("✓ Registered as " + finalCallsign);
                    appendLog("✓ SSE stream started");
                    // Immediately pull RF data
                    doRefreshRf();
                });
            }
            @Override public void onError(String message) {
                Log.w(TAG, "Register failed: " + message + " — trying login");
                // Fall back to login (operator may already exist)
                apiClient.login(finalCallsign,
                        new ScytheApiClient.ApiCallback<String>() {
                    @Override public void onSuccess(String token) {
                        sseClient.updateEndpoint(apiClient.getBaseUrl(), token);
                        sseClient.start();
                        uiHandler.post(() -> {
                            connected = true;
                            btnConnect.setEnabled(true);
                            btnConnect.setText("RECONNECT");
                            setStatusBadge(true, "STREAMING");
                            appendLog("✓ Logged in as " + finalCallsign);
                        });
                    }
                    @Override public void onError(String msg2) {
                        uiHandler.post(() -> {
                            btnConnect.setEnabled(true);
                            appendLog("✗ " + msg2);
                        });
                    }
                });
            }
        });
    }

    private void appendLog(String msg) {
        if (txtConnectLog == null) return;
        String current = txtConnectLog.getText().toString();
        String updated = current.isEmpty() ? msg : current + "\n" + msg;
        // Keep last 6 lines
        String[] lines = updated.split("\n");
        if (lines.length > 6) {
            StringBuilder sb = new StringBuilder();
            for (int i = lines.length - 6; i < lines.length; i++) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(lines[i]);
            }
            updated = sb.toString();
        }
        txtConnectLog.setText(updated);
    }

    private void setStatusBadge(boolean online, String label) {
        if (txtStatusBadge == null) return;
        txtStatusBadge.setText(label);
        txtStatusBadge.setTextColor(
                online ? 0xFF00FF88 : 0xFFFF4444);
    }

    // -------------------------------------------------------------------------
    // RF Intel tab actions
    // -------------------------------------------------------------------------

    private void doRefreshRf() {
        if (!connected) { appendLog("Not connected"); return; }
        apiClient.getRfNodes(new ScytheApiClient.ApiCallback<List<RFNode>>() {
            @Override public void onSuccess(List<RFNode> nodes) {
                rfLayer.updateNodes(nodes);
                uiHandler.post(() -> {
                    txtNodeCount.setText(nodes.size() + " nodes");
                    List<String> items = new ArrayList<>();
                    for (RFNode n : nodes) {
                        items.add(String.format("[%s] %s  %s  %.2f",
                                n.kind.toUpperCase(),
                                n.callsign,
                                n.frequencyLabel(),
                                n.confidence));
                    }
                    listRfNodes.setAdapter(new ArrayAdapter<>(pluginCtx,
                            android.R.layout.simple_list_item_1, items));
                });
            }
            @Override public void onError(String msg) {
                uiHandler.post(() -> appendLog("RF error: " + msg));
            }
        });
    }

    private void doInjectCot() {
        if (!connected) { appendLog("Not connected"); return; }
        apiClient.getCotXml(new ScytheApiClient.ApiCallback<List<String>>() {
            @Override public void onSuccess(List<String> cotEvents) {
                int count = injectCotEvents(cotEvents);
                uiHandler.post(() ->
                        appendLog("✓ Injected " + count + " CoT markers into ATAK"));
            }
            @Override public void onError(String msg) {
                uiHandler.post(() -> appendLog("CoT error: " + msg));
            }
        });
    }

    /**
     * Injects CoT XML strings into ATAK's map via the
     * com.atakmap.android.cot.CotDispatcher broadcast mechanism.
     * Returns count of successfully queued events.
     */
    private int injectCotEvents(List<String> cotXmlList) {
        int count = 0;
        for (String xml : cotXmlList) {
            try {
                com.atakmap.coremap.cot.event.CotEvent event =
                        com.atakmap.coremap.cot.event.CotEvent.parse(xml);
                if (event != null && event.isValid()) {
                    com.atakmap.android.ipc.AtakBroadcast.getInstance()
                            .sendBroadcast(new Intent(
                                    "com.atakmap.android.cot.ADD_OR_UPDATE")
                                    .putExtra("cotEvent", event));
                    count++;
                }
            } catch (Exception e) {
                Log.w(TAG, "CoT inject failed: " + e.getMessage());
            }
        }
        return count;
    }

    private void doPushTak() {
        if (!connected) { appendLog("Not connected"); return; }
        String takHost = editTakHost.getText().toString().trim();
        if (takHost.isEmpty()) takHost = "239.2.3.1";
        final String finalHost = takHost;
        apiClient.sendCotToTakNetwork("udp", finalHost, 6969,
                new ScytheApiClient.ApiCallback<Integer>() {
            @Override public void onSuccess(Integer sent) {
                uiHandler.post(() ->
                        appendLog("✓ Pushed " + sent + " CoT → " + finalHost + ":6969"));
            }
            @Override public void onError(String msg) {
                uiHandler.post(() -> appendLog("Push error: " + msg));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Missions tab actions
    // -------------------------------------------------------------------------

    private void doRefreshMissions() {
        if (!connected) return;
        apiClient.getMissions(new ScytheApiClient.ApiCallback<JSONArray>() {
            @Override public void onSuccess(JSONArray missions) {
                uiHandler.post(() -> {
                    List<String> items = new ArrayList<>();
                    for (int i = 0; i < missions.length(); i++) {
                        JSONObject m = missions.optJSONObject(i);
                        if (m != null) {
                            items.add(String.format("[%s] %s — %s",
                                    m.optString("status", "active").toUpperCase(),
                                    m.optString("name", "unnamed"),
                                    m.optString("mission_id", "").substring(0, Math.min(8,
                                            m.optString("mission_id", "").length()))));
                        }
                    }
                    listMissions.setAdapter(new ArrayAdapter<>(pluginCtx,
                            android.R.layout.simple_list_item_1, items));
                });
            }
            @Override public void onError(String msg) {
                uiHandler.post(() -> appendLog("Missions error: " + msg));
            }
        });
    }

    private void doNewMission() {
        // Simple input dialog for mission name
        AlertDialog.Builder dlg = new AlertDialog.Builder(getMapView().getContext());
        dlg.setTitle("New Mission");
        final EditText input = new EditText(getMapView().getContext());
        input.setHint("Mission name");
        dlg.setView(input);
        dlg.setPositiveButton("Create", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) return;
            apiClient.createMission(name, "ATAK plugin mission",
                    new ScytheApiClient.ApiCallback<String>() {
                @Override public void onSuccess(String id) {
                    uiHandler.post(() -> {
                        appendLog("✓ Mission created: " + id);
                        doRefreshMissions();
                    });
                }
                @Override public void onError(String msg) {
                    uiHandler.post(() -> appendLog("Mission error: " + msg));
                }
            });
        });
        dlg.setNegativeButton("Cancel", null);
        dlg.show();
    }

    // -------------------------------------------------------------------------
    // Swarms tab actions
    // -------------------------------------------------------------------------

    public void refreshSwarmsFromLayer() {
        // Called by ScytheMapComponent when SwarmLayer has new data (SSE stream)
        if (panelSwarms == null || panelSwarms.getVisibility() != View.VISIBLE) return;
        updateSwarmListUi(swarmLayer.getSnapshot());
    }

    private void doRefreshSwarms() {
        if (!connected) return;
        apiClient.getSwarms(new ScytheApiClient.ApiCallback<List<CyberCluster>>() {
            @Override public void onSuccess(List<CyberCluster> clusters) {
                swarmLayer.updateAll(clusters);
                uiHandler.post(() -> {
                    updateSwarmListUi(clusters);
                    if (txtSwarmStreamStatus != null) {
                        txtSwarmStreamStatus.setText("● LAST REFRESH: " + clusters.size() + " swarms");
                        txtSwarmStreamStatus.setTextColor(0xFF00FF88);
                    }
                });
            }
            @Override public void onError(String msg) {
                uiHandler.post(() -> {
                    if (txtSwarmStreamStatus != null) {
                        txtSwarmStreamStatus.setText("● ERROR: " + msg);
                        txtSwarmStreamStatus.setTextColor(0xFFFF4444);
                    }
                });
            }
        });
    }

    private void doInjectSwarmCot() {
        if (!connected) { appendLog("Not connected"); return; }
        apiClient.getSwarmCot(new ScytheApiClient.ApiCallback<List<String>>() {
            @Override public void onSuccess(List<String> cotEvents) {
                int count = injectCotEvents(cotEvents);
                uiHandler.post(() ->
                        appendLog("✓ Injected " + count + " swarm CoT markers"));
            }
            @Override public void onError(String msg) {
                uiHandler.post(() -> appendLog("Swarm CoT error: " + msg));
            }
        });
    }

    private void updateSwarmListUi(List<CyberCluster> clusters) {
        if (listSwarms == null) return;
        List<String> items = new ArrayList<>();
        for (CyberCluster c : clusters) {
            items.add(String.format("%s  nodes:%d  threat:%.0f%%  %s",
                    c.behaviorType,
                    c.nodeCount,
                    c.threatScore * 100,
                    c.threatLabel));
        }
        listSwarms.setAdapter(new ArrayAdapter<>(pluginCtx,
                android.R.layout.simple_list_item_1, items));
        if (txtSwarmCount != null) txtSwarmCount.setText(clusters.size() + " swarms");
    }

    // -------------------------------------------------------------------------
    // SseStreamClient.EntityListener
    // -------------------------------------------------------------------------

    @Override
    public void onEntity(ScytheEntity entity) {
        switch (entity.event) {
            case PREEXISTING:
            case CREATE:
            case UPDATE:
                if (entity.type.contains("rf") || entity.type.contains("signal")) {
                    // Convert SSE entity → RFNode and push to layer
                    RFNode synced = new RFNode.Builder()
                            .id(entity.id)
                            .kind(entity.type)
                            .lat(entity.lat)
                            .lon(entity.lon)
                            .alt(entity.alt)
                            .callsign(entity.callsign)
                            .build();
                    rfLayer.upsertNode(synced);
                }
                break;
            case DELETE:
                rfLayer.removeNode(entity.id);
                break;
            default:
                break;
        }
    }

    @Override
    public void onConnected() {
        uiHandler.post(() -> setStatusBadge(true, "STREAMING"));
    }

    @Override
    public void onDisconnected(String reason) {
        uiHandler.post(() -> {
            setStatusBadge(false, "RECONNECT…");
            Log.w(TAG, "SSE disconnected: " + reason);
        });
    }

    // -------------------------------------------------------------------------
    // DropDown lifecycle
    // -------------------------------------------------------------------------

    @Override public void onDropDownSelectionRemoved() {}

    @Override
    public void onDropDownClose() {
        // Panel hidden but SSE stream continues in background
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {}

    @Override
    public void onDropDownVisible(boolean v) {}

    /** Full cleanup — called by ScytheMapComponent.onDestroy */
    public void shutdown() {
        if (sseClient != null) sseClient.stop();
        if (apiClient != null) apiClient.shutdown();
    }

    @Override
    protected void disposeImpl() {
        shutdown();
    }
}
