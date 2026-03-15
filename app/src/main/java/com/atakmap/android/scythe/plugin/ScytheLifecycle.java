package com.atakmap.android.scythe.plugin;

import android.content.Context;

import com.atak.plugins.impl.AbstractPluginLifecycle;
import com.atakmap.android.scythe.ScytheMapComponent;

/**
 * ATAK Plugin Lifecycle entry point.
 *
 * ATAK's plugin loader discovers this class via the AndroidManifest
 * plugin-api meta-data. It instantiates ScytheMapComponent which registers
 * the RF signal layer, drop-down UI, and SSE/REST clients.
 *
 * Build note — 16KB ELF compliance:
 *   This plugin ships NO native .so libraries. All logic is Kotlin/Java.
 *   ELF compatibility flags in build.gradle and AndroidManifest.xml are
 *   precautionary and prevent misaligned extraction of any transitively
 *   bundled host libs (e.g. ATAK SDK stub JARs).
 *
 *   If native code is added in the future:
 *     • Build with NDK r27+
 *     • Add to CMakeLists.txt: target_link_options(... -Wl,--max-page-size=16384)
 *     • Re-run verify_16kb_alignment.sh (AndroidAppSceneview reference)
 */
public class ScytheLifecycle extends AbstractPluginLifecycle {

    public ScytheLifecycle(Context ctx) {
        super(ctx, new ScytheMapComponent());
    }
}
