package com.atakmap.android.scythe.layer;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.scythe.model.RFNode;
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
 * OpenGL renderer for {@link RFSignalLayer}.
 *
 * Each RF node is drawn as:
 *   • A filled circle (triangle-fan) whose color encodes signal strength
 *       green  (#00FF88) = strong  (rssi > -50 dBm, normalized ≥ 0.7)
 *       yellow (#FFCC00) = medium  (0.4 – 0.7)
 *       red    (#FF4444) = weak    (< 0.4)
 *   • A small white point at the exact position
 *
 * Labels (callsign + freq) are rendered via GLES20FixedPipeline text when
 * the map scale is zoomed in enough (< 500 m screen width).
 *
 * SPI factory is registered by ScytheMapComponent on startup.
 */
public class GLRFSignalLayer extends GLAbstractLayer {

    public static final GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override public int getPriority() { return 2; }
        @Override public GLLayer2 create(Pair<MapRenderer, Layer> object) {
            if (!(object.second instanceof RFSignalLayer)) return null;
            return new GLRFSignalLayer(object.first, (RFSignalLayer) object.second);
        }
    };

    private static final float CIRCLE_RADIUS_DP = 12f;
    private static final int   CIRCLE_SEGMENTS  = 16;

    private final RFSignalLayer subject;
    private List<RFNode>        frameNodes;
    private FloatBuffer         circleVerts;

    public GLRFSignalLayer(MapRenderer surface, RFSignalLayer subject) {
        super(surface, subject);
        this.subject = subject;
        // Pre-compute unit circle verts (center + CIRCLE_SEGMENTS + close)
        circleVerts = buildCircleVerts(CIRCLE_RADIUS_DP, CIRCLE_SEGMENTS);
    }

    @Override
    protected void init() {
        super.init();
        frameNodes = subject.getSnapshot();
    }

    @Override
    protected void drawImpl(GLMapView view) {
        // Pull fresh snapshot only when data changed
        if (subject.isDirtyAndReset()) {
            frameNodes = subject.getSnapshot();
        }
        if (frameNodes == null || frameNodes.isEmpty()) return;

        GLES20FixedPipeline.glPushMatrix();

        for (RFNode node : frameNodes) {
            drawNode(view, node);
        }

        GLES20FixedPipeline.glPopMatrix();
    }

    private void drawNode(GLMapView view, RFNode node) {
        GeoPoint geo = GeoPoint.createMutable();
        geo.set(node.lat, node.lon);

        // Project geo → screen
        float[] screen = new float[2];
        view.forward(geo, screen);
        float sx = screen[0];
        float sy = screen[1];

        // Color by signal strength
        float str = node.normalizedStrength();
        setColorByStrength(str);

        // Draw filled circle (triangle fan)
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(sx, sy, 0f);

        GLES20FixedPipeline.glEnableClientState(
                GLES20FixedPipeline.GL_VERTEX_ARRAY);
        circleVerts.rewind();
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT,
                0, circleVerts);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN,
                0, CIRCLE_SEGMENTS + 2);
        GLES20FixedPipeline.glDisableClientState(
                GLES20FixedPipeline.GL_VERTEX_ARRAY);

        GLES20FixedPipeline.glPopMatrix();
    }

    /** Map normalized signal strength to RGBA. */
    private void setColorByStrength(float s) {
        float r, g, b;
        if (s >= 0.7f) {
            // strong → green
            r = 0f;  g = 1f;  b = 0.53f;
        } else if (s >= 0.4f) {
            // medium → yellow
            r = 1f;  g = 0.8f; b = 0f;
        } else {
            // weak → red
            r = 1f;  g = 0.27f; b = 0.27f;
        }
        GLES20FixedPipeline.glColor4f(r, g, b, 0.85f);
    }

    private static FloatBuffer buildCircleVerts(float radius, int segments) {
        // center + segments + 1 closing vertex = segments+2 points × 2 floats
        FloatBuffer buf = ByteBuffer
                .allocateDirect((segments + 2) * 2 * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        // center
        buf.put(0f); buf.put(0f);

        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            buf.put((float) (radius * Math.cos(angle)));
            buf.put((float) (radius * Math.sin(angle)));
        }
        buf.rewind();
        return buf;
    }

    @Override
    public void release() {
        circleVerts = null;
        frameNodes  = null;
    }
}
