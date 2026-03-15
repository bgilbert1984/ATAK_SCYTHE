package com.atakmap.android.scythe.api;

import com.atakmap.android.scythe.model.AuthResponse;
import com.atakmap.android.scythe.model.CotEvent;
import com.atakmap.android.scythe.model.HeatmapData;
import com.atakmap.android.scythe.model.Mission;
import com.atakmap.android.scythe.model.RfNode;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ScytheApiClient} using {@link MockWebServer}.
 *
 * <p>All tests follow the same pattern:
 * <ol>
 *   <li>Enqueue a canned HTTP response on {@link MockWebServer}.</li>
 *   <li>Invoke the relevant {@link ScytheApiClient} method.</li>
 *   <li>Wait for the async callback using a {@link CountDownLatch}.</li>
 *   <li>Assert on the received result or error message.</li>
 * </ol>
 */
public class ScytheApiClientTest {

    private MockWebServer server;
    private ScytheApiClient client;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("").toString().replaceAll("/$", "");
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        client = new ScytheApiClient(baseUrl, httpClient);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullBaseUrl_throwsIllegalArgument() {
        new ScytheApiClient(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_emptyBaseUrl_throwsIllegalArgument() {
        new ScytheApiClient("");
    }

    // -------------------------------------------------------------------------
    // register
    // -------------------------------------------------------------------------

    @Test
    public void register_success_callsOnSuccessWithToken() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"token\":\"jwt-abc\",\"message\":\"Registered\"}"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AuthResponse> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        client.register("alice", "s3cr3t", new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse r) {
                result.set(r);
                latch.countDown();
            }

            @Override
            public void onFailure(String msg) {
                error.set(msg);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull("Expected no error but got: " + error.get(), error.get());
        assertNotNull(result.get());
        assertEquals("jwt-abc", result.get().getToken());
        assertEquals("Registered", result.get().getMessage());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().endsWith("/auth/register"));
    }

    @Test
    public void register_httpError_callsOnFailure() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(409).setBody("{\"message\":\"User exists\"}"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();

        client.register("alice", "s3cr3t", new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse r) {
                latch.countDown();
            }

            @Override
            public void onFailure(String msg) {
                error.set(msg);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(error.get());
        assertTrue("Error should mention HTTP 409", error.get().contains("409"));
    }

    // -------------------------------------------------------------------------
    // login
    // -------------------------------------------------------------------------

    @Test
    public void login_success_storesTokenAndCallsOnSuccess() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"token\":\"jwt-xyz\",\"message\":\"OK\"}"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AuthResponse> result = new AtomicReference<>();

        client.login("alice", "s3cr3t", new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse r) {
                result.set(r);
                latch.countDown();
            }

            @Override
            public void onFailure(String msg) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("jwt-xyz", result.get().getToken());
        assertEquals("jwt-xyz", client.getAuthToken());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().endsWith("/auth/login"));
    }

    @Test
    public void login_httpError_callsOnFailure() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"message\":\"Unauthorized\"}"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();

        client.login("alice", "wrong", new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse r) {
                latch.countDown();
            }

            @Override
            public void onFailure(String msg) {
                error.set(msg);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(error.get());
        assertTrue(error.get().contains("401"));
    }

    // -------------------------------------------------------------------------
    // RF Nodes
    // -------------------------------------------------------------------------

    @Test
    public void getRfNodes_success_callsOnSuccessWithList() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"id\":\"n1\",\"name\":\"Node A\",\"latitude\":34.0,\"longitude\":-117.0,\"status\":\"online\"}]"));

        client.setAuthToken("tok");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<RfNode>> result = new AtomicReference<>();

        client.getRfNodes(new ApiCallback<List<RfNode>>() {
            @Override
            public void onSuccess(List<RfNode> r) {
                result.set(r);
                latch.countDown();
            }

            @Override
            public void onFailure(String msg) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals(1, result.get().size());
        RfNode node = result.get().get(0);
        assertEquals("n1", node.getId());
        assertEquals("Node A", node.getName());
        assertEquals(34.0, node.getLatitude(), 0.0001);
        assertEquals("online", node.getStatus());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().endsWith("/rf/nodes"));
        assertEquals("Bearer tok", req.getHeader("Authorization"));
    }

    // -------------------------------------------------------------------------
    // CoT events
    // -------------------------------------------------------------------------

    @Test
    public void getCotEvents_success_returnsEventList() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"uid\":\"u1\",\"type\":\"a-f-G\",\"latitude\":35.0,\"longitude\":-118.0}]"));

        client.setAuthToken("tok");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<CotEvent>> result = new AtomicReference<>();

        client.getCotEvents(new ApiCallback<List<CotEvent>>() {
            @Override
            public void onSuccess(List<CotEvent> r) {
                result.set(r);
                latch.countDown();
            }

            @Override
            public void onFailure(String msg) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals(1, result.get().size());
        assertEquals("u1", result.get().get(0).getUid());
    }

    @Test
    public void postCotEvent_success_sendsEventAndReturnsResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"uid\":\"u2\",\"type\":\"a-f-G\",\"latitude\":36.0,\"longitude\":-119.0}"));

        client.setAuthToken("tok");

        CotEvent event = new CotEvent();
        event.setUid("u2");
        event.setType("a-f-G");
        event.setLatitude(36.0);
        event.setLongitude(-119.0);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<CotEvent> result = new AtomicReference<>();

        client.postCotEvent(event, new ApiCallback<CotEvent>() {
            @Override
            public void onSuccess(CotEvent r) {
                result.set(r);
                latch.countDown();
            }

            @Override
            public void onFailure(String msg) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("u2", result.get().getUid());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().endsWith("/cot/events"));
    }

    // -------------------------------------------------------------------------
    // Missions
    // -------------------------------------------------------------------------

    @Test
    public void getMissions_success_returnsMissionList() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"id\":\"m1\",\"name\":\"Alpha\",\"status\":\"active\"}]"));

        client.setAuthToken("tok");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Mission>> result = new AtomicReference<>();

        client.getMissions(new ApiCallback<List<Mission>>() {
            @Override
            public void onSuccess(List<Mission> r) {
                result.set(r);
                latch.countDown();
            }

            @Override
            public void onFailure(String msg) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals(1, result.get().size());
        assertEquals("m1", result.get().get(0).getId());
        assertEquals("Alpha", result.get().get(0).getName());
    }

    // -------------------------------------------------------------------------
    // Heatmap
    // -------------------------------------------------------------------------

    @Test
    public void getHeatmap_success_returnsHeatmapData() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"missionId\":\"m1\",\"points\":[{\"latitude\":34.0,\"longitude\":-117.0,\"weight\":0.8}]}"));

        client.setAuthToken("tok");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HeatmapData> result = new AtomicReference<>();

        client.getHeatmap("m1", new ApiCallback<HeatmapData>() {
            @Override
            public void onSuccess(HeatmapData r) {
                result.set(r);
                latch.countDown();
            }

            @Override
            public void onFailure(String msg) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("m1", result.get().getMissionId());
        assertEquals(1, result.get().getPoints().size());
        assertEquals(0.8, result.get().getPoints().get(0).getWeight(), 0.0001);

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().endsWith("/missions/m1/heatmap"));
    }

    // -------------------------------------------------------------------------
    // Token helpers
    // -------------------------------------------------------------------------

    @Test
    public void setGetAuthToken_roundtrip() {
        client.setAuthToken("my-token");
        assertEquals("my-token", client.getAuthToken());
    }

    @Test
    public void initialAuthToken_isNull() {
        assertNull(client.getAuthToken());
    }
}
