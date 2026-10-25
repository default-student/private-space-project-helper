package com.example.privatespaceprojects;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public final class ProjectLinkActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int slot = readSlot(getIntent());
        String url = slot < 1 || slot > MainActivity.SLOT_COUNT ? null
                : getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                        .getString("slot." + slot + ".url", null);
        String targetPackage = slot < 1 || slot > MainActivity.SLOT_COUNT ? null
                : getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                        .getString("slot." + slot + ".package", MainActivity.CHATGPT_PACKAGE);
        if (url == null || targetPackage == null || !MainActivity.isAllowedLink(url)) {
            Toast.makeText(this, "This private shortcut is unavailable.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        MainActivity.logLinkEvent("home-shortcut-slot-" + slot, url, targetPackage);
        MainActivity.openTargetLink(this, "home-shortcut-slot-" + slot, url, targetPackage,
                networkPermissionDenied -> {
                    Toast.makeText(this, networkPermissionDenied
                                    ? "Allow Network permission for Private Space Shortcuts in Settings, then retry."
                                    : "The selected app cannot open this link or its HTTPS redirects.",
                            Toast.LENGTH_LONG).show();
                    finish();
                }, this::finish);
    }

    private int readSlot(Intent intent) {
        ComponentName component = intent == null ? null : intent.getComponent();
        if (component == null) return -1;
        String className = component.getClassName();
        String prefix = MainActivity.PUBLISHER + ".ProjectLink";
        if (!className.startsWith(prefix)) return -1;
        try {
            return Integer.parseInt(className.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
