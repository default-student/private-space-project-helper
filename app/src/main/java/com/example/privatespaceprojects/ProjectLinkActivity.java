package com.example.privatespaceprojects;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
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
        if (url == null || !isChatGptUrl(url)) {
            Toast.makeText(this, "This private project link is unavailable.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .setPackage(MainActivity.CHATGPT_PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(open);
        } catch (RuntimeException error) {
            Toast.makeText(this, "ChatGPT could not open this link in this profile.", Toast.LENGTH_LONG).show();
        }
        finish();
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

    private boolean isChatGptUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            return value.length() <= MainActivity.MAX_URL_LENGTH && uri.isHierarchical()
                    && "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getEncodedPath() != null && !"/".equals(uri.getEncodedPath())
                    && (host.equalsIgnoreCase("chatgpt.com")
                    || host.equalsIgnoreCase("www.chatgpt.com"));
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
