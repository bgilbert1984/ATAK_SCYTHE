package com.atakmap.android.scythe;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

/**
 * ATAK plugin lifecycle stub.
 *
 * <p>In a full ATAK plugin integration this class would extend
 * {@code AbstractPluginLifecycle} from the ATAK SDK and register the
 * {@link ScytheMapComponent}. The ATAK SDK is not bundled with the project
 * sources, so this class implements the minimum contract expected by the ATAK
 * host application via reflection: it exposes {@code onCreate},
 * {@code onResume}, {@code onPause} and {@code onDestroy} hooks.
 */
public class ScytheLifecycle extends Activity {

    private ScytheMapComponent mapComponent;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getApplicationContext();
        mapComponent = new ScytheMapComponent(context);
        mapComponent.onCreate(context);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapComponent != null) {
            mapComponent.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapComponent != null) {
            mapComponent.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapComponent != null) {
            mapComponent.onDestroy();
            mapComponent = null;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
