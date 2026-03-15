package com.atakmap.android.scythe.plugin;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.atak.plugins.impl.PluginTool;
import com.atakmap.android.scythe.ScytheDropDownReceiver;
import com.atakmap.coremap.log.Log;

/**
 * ATAK toolbar button for the RF Scythe plugin.
 *
 * Tapping the button broadcasts {@link ScytheDropDownReceiver#SHOW_DROPDOWN}
 * which triggers the drop-down panel.
 *
 * The button icon is ic_scythe.xml (VectorDrawable RF antenna).
 */
public class ScytheTool extends PluginTool {

    public static final String TAG = "ScytheTool";

    private final Context pluginCtx;

    public ScytheTool(Context ctx) {
        super(ctx, ctx.getString(R.string.app_name),
              ctx.getString(R.string.app_desc),
              /* identifier */ "com.atakmap.android.scythe.TOOL");
        this.pluginCtx = ctx;
    }

    @Override
    public void onToolSelected() {
        Log.d(TAG, "Tool selected — opening Scythe dropdown");
        Intent intent = new Intent(ScytheDropDownReceiver.SHOW_DROPDOWN);
        com.atakmap.android.ipc.AtakBroadcast.getInstance()
                .sendBroadcast(intent);
    }

    @Override
    public Drawable getIcon() {
        return pluginCtx.getDrawable(R.drawable.ic_scythe);
    }
}
