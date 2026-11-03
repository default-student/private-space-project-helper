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
        String linkKey = readLinkKey(getIntent());
        boolean legacy = isLegacySlot(linkKey);
        String prefix = legacy ? "slot." + linkKey + "." : "link." + linkKey + ".";
        String url = linkKey == null ? null : getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getString(prefix + "url", null);
        String targetPackage = linkKey == null ? null : getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getString(prefix + "package", MainActivity.CHATGPT_PACKAGE);
        if (url == null || targetPackage == null || !MainActivity.isAllowedLink(url)) {
            Toast.makeText(this, "This private shortcut is unavailable.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        MainActivity.logLinkEvent("home-shortcut", url, targetPackage);
        MainActivity.openTargetLink(this, "home-shortcut", url, targetPackage,
                networkPermissionDenied -> {
                    Toast.makeText(this, networkPermissionDenied
                                    ? "Allow Network permission for Private Space Shortcuts in Settings, then retry."
                                    : "The selected app cannot open this link or its HTTPS redirects.",
                            Toast.LENGTH_LONG).show();
                    finish();
                }, this::finish);
    }

    private String readLinkKey(Intent intent) {
        ComponentName component = intent == null ? null : intent.getComponent();
        if (component == null) return null;
        String className = component.getClassName();
        String prefix = MainActivity.PUBLISHER + ".ProjectLink";
        if (!className.startsWith(prefix)) return null;
        String suffix = className.substring(prefix.length());
        if (suffix.isEmpty()) {
            String key = intent.getStringExtra(MainActivity.EXTRA_LINK_KEY);
            try {
                java.util.UUID.fromString(key);
                return key;
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        try {
            int slot = Integer.parseInt(suffix);
            return slot >= 1 && slot <= MainActivity.LEGACY_SLOT_COUNT ? Integer.toString(slot) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isLegacySlot(String linkKey) {
        if (linkKey == null) return false;
        try {
            int slot = Integer.parseInt(linkKey);
            return slot >= 1 && slot <= MainActivity.LEGACY_SLOT_COUNT;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
