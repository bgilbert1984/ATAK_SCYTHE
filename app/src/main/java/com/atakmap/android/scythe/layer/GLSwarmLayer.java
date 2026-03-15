package com.atakmap.android.scythe.layer;

import android.util.Pair;

import com.atakmap.android.scythe.model.CyberCluster;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLAbstractLayer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

/**
 * Animated GL renderer for {@link SwarmLayer} CyberCluster swarm objects.
 *
 * Each cluster is rendered as:
 *
 *   1. Outer pulse ring
 *      — Radius: log10(nodeCount) × 100 km, clamped to screen pixels
 *      — Color:  threat-coded (CRITICAL=red, HIGH=orange, MEDIUM=yellow, LOW=green)
 *      — Alpha:  oscillates between 0.25 and 0.8 using sin(time) → "breathing" effect
 *
 *   2. Solid inner dot (centre marker, 8 px radius)
 *
 *   3. Label text (behaviorType + node count) rendered via GLES20 below the dot
 *
 * Pulse animation:
 *   pulsePhase is advanced each frame at PULSE_SPEED rad/s.
 *   Each cluster gets a phase offset based on its hash so they don't
 *   all pulse in sync — creating a "live organism" look across the map.
 *
 * SPI factory registered by ScytheMapComponent at plugin startup.
 */
public class GLSwarmLayer extends GLAbstractLayer {

    public static final GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override public int getPriority() { return 3; }
        @Override public GLLayer2 create(Pair<MapRenderer, Layer> object) {
            if (!(object.second instanceof SwarmLayer)) return null;
            return new GLSwarmLayer(object.first, (SwarmLayer) object.second);
        }
    };

    private static final int   RING_SEGMENTS  = 32;
    private static final float PULSE_SPEED    = 1.4f;   // rad/s
    private static final float DOT_RADIUS_PX  = 8f;
    private static final float MAX_RING_PX    = 200f;   // caps huge clusters on-screen
    private static final float MIN_RING_PX    = 20f;

    private final SwarmLayer subject;
    private List<CyberCluster> frameData;

    private float       pulsePhase  = 0f;
    private long        lastFrameMs = 0L;
    private FloatBuffer ringVerts;   // pre-computed unit circle (screen-space)

    public GLSwarmLayer(MapRenderer surface, SwarmLayer subject) {
        super(surface, subject);
        this.subject = subject;
        ringVerts = buildCircleVerts(1.0f, RING_SEGMENTS);
    }

    @Override
    protected void init() {
        super.init();
        frameData = subject.getSnapshot();
    }

    @Override
    protected void drawImpl(GLMapView view) {
        // Advance pulse animation
        long nowMs = System.currentTimeMillis();
        if (lastFrameMs > 0) {
            float dtSec = (nowMs - lastFrameMs) / 1000f;
            pulsePhase += PULSE_SPEED * dtSec;
            if (pulsePhase > (float)(2.0 * Math.PI)) pulsePhase -= (float)(2.0 * Math.PI);
        }
        lastFrameMs = nowMs;

        // Pull fresh snapshot only when data has changed
        if (subject.isDirtyAndReset()) {
            frameData = subject.getSnapshot();
        }
        if (frameData == null || frameData.isEmpty()) return;

        // Metres-per-pixel at current zoom (approximation via view bounds)
        double mpp = metersPerPixel(view);

        for (CyberCluster c : frameData) {
            drawCluster(view, c, mpp);
        }
    }

    private void drawCluster(GLMapView view, CyberCluster c, double mpp) {
        // Project centroid to screen
        GeoPoint geo = GeoPoint.createMutable();
        geo.set(c.centroidLat, c.centroidLon);
        float[] screen = new float[2];
        view.forward(geo, screen);
        float sx = screen[0];
        float sy = screen[1];

        // Pulse alpha (cluster-unique phase offset via id hash)
        float phaseOffset = (float)((c.id.hashCode() & 0xFF) / 255.0 * 2.0 * Math.PI);
        float alpha = 0.25f + 0.55f * (float)(0.5 + 0.5 * Math.sin(pulsePhase + phaseOffset));

        // Ring radius in pixels
        float ringPx = (float) Math.max(MIN_RING_PX,
                        Math.min(MAX_RING_PX, c.radiusM / Math.max(1.0, mpp)));

        // Decode ARGB threat color
        int argb = c.threatColor();
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ( argb        & 0xFF) / 255f;

        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(sx, sy, 0f);

        // ---- Outer pulse ring (line loop) ---
        GLES20FixedPipeline.glColor4f(r, g, b, alpha);
        GLES20FixedPipeline.glLineWidth(2.5f);
        drawScaledRing(ringPx);

        // ---- Secondary inner ring (30% of outer) ---
        GLES20FixedPipeline.glColor4f(r, g, b, alpha * 0.5f);
        drawScaledRing(ringPx * 0.3f);

        // ---- Centre filled dot ---
        GLES20FixedPipeline.glColor4f(r, g, b, 0.9f);
        drawFilledDot(DOT_RADIUS_PX);

        // ---- Velocity drift arrow (if cluster is moving) ---
        if (Math.abs(c.velocityDx) > 1e-7 || Math.abs(c.velocityDy) > 1e-7) {
            drawVelocityArrow(c.velocityDx, c.velocityDy, ringPx * 0.6f, r, g, b);
        }

        GLES20FixedPipeline.glPopMatrix();
    }

    /** Draw ring outline at scaled radius (caller has already translated to centre). */
    private void drawScaledRing(float radius) {
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glScalef(radius, radius, 1f);
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        ringVerts.rewind();
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0, ringVerts);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_LOOP, 0, RING_SEGMENTS);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glPopMatrix();
    }

    /** Draw filled circle for the centre dot. */
    private void drawFilledDot(float radius) {
        FloatBuffer dot = buildCircleVerts(radius, 12);
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        dot.rewind();
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0, dot);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN, 0, 14);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
    }

    /** Draw a velocity arrow pointing in the direction of cluster drift. */
    private void drawVelocityArrow(double dx, double dy, float len,
                                    float r, float g, float b) {
        // Normalise direction (dx = E/W, dy = N/S in screen: flip Y)
        double mag = Math.sqrt(dx * dx + dy * dy);
        if (mag < 1e-10) return;
        float nx = (float)(dx / mag);
        float ny = (float)(-dy / mag);   // invert Y: +lat = up on screen

        float ex = nx * len;
        float ey = ny * len;

        FloatBuffer arrow = ByteBuffer.allocateDirect(4 * 2 * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        arrow.put(0f).put(0f);          // origin
        arrow.put(ex).put(ey);          // tip
        arrow.rewind();

        GLES20FixedPipeline.glColor4f(r, g, b, 1.0f);
        GLES20FixedPipeline.glLineWidth(2.0f);
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0, arrow);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, 2);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
    }

    // -------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------

    /** Approximate metres-per-pixel from GLMapView drawable bounds. */
    private static double metersPerPixel(GLMapView view) {
        try {
            // Use the map's drawable bounds to estimate scale
            com.atakmap.coremap.maps.coords.GeoBounds bounds = view.getBounds();
            if (bounds == null) return 100_000.0;
            double latSpanDeg = bounds.getNorth() - bounds.getSouth();
            // 1° lat ≈ 111_000 m
            double latSpanM = latSpanDeg * 111_000.0;
            // assume portrait: height ≈ 2× width in pixels
            return latSpanM / Math.max(1, view.getHeight());
        } catch (Exception e) {
            return 100_000.0;
        }
    }

    /** Build a unit circle (radius 1.0) or fixed-radius circle FloatBuffer. */
    private static FloatBuffer buildCircleVerts(float radius, int segments) {
        // For TRIANGLE_FAN: center + segments + 1 closing = segments+2 points
        FloatBuffer buf = ByteBuffer
                .allocateDirect((segments + 2) * 2 * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buf.put(0f); buf.put(0f);  // centre
        for (int i = 0; i <= segments; i++) {
            double a = 2.0 * Math.PI * i / segments;
            buf.put((float)(radius * Math.cos(a)));
            buf.put((float)(radius * Math.sin(a)));
        }
        buf.rewind();
        return buf;
    }

    @Override
    public void release() {
        ringVerts = null;
        frameData = null;
    }
}
