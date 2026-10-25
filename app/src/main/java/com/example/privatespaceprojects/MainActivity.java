package com.example.privatespaceprojects;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    static final String PUBLISHER = "com.example.privatespaceprojects";
    static final String PREFS = "private_project_links";
    static final String ID_PREFIX = "shared-project-";
    static final String EXTRA_VERSION = "app.lawnchair.private_share.version";
    static final String EXTRA_PENDING = "app.lawnchair.private_share.pending";
    static final String EXTRA_TITLE_REQUIRED = "app.lawnchair.private_share.title_required";
    static final String CHATGPT_PACKAGE = "com.openai.chatgpt";
    static final int SLOT_COUNT = 10;
    static final int MAX_URL_LENGTH = 4096;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\\\"']+");

    private EditText nameInput;
    private EditText urlInput;
    private TextView status;
    private LinearLayout savedLinks;
    private int accent;
    private int surface;
    private int onSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureColors();
        setContentView(buildContent());
        acceptSharedLink(getIntent());
        renderSavedLinks();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptSharedLink(intent);
    }

    private void configureColors() {
        accent = systemColor("system_accent1_600", 0xff176b5b);
        surface = systemColor("system_neutral1_10", 0xfff4f5f3);
        onSurface = systemColor("system_neutral1_900", 0xff1b1c1a);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(surface);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private int systemColor(String name, int fallback) {
        int id = getResources().getIdentifier(name, "color", "android");
        if (id == 0) return fallback;
        try {
            return getResources().getColor(id, getTheme());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(surface);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(28), dp(24), dp(32));
        scroll.addView(page);

        TextView title = text("Private Space Projects", 27, true);
        title.setTextColor(onSurface);
        page.addView(title, margin(0, 0, 0, 8));
        TextView intro = text("Share a ChatGPT project link here, name it, then add its shortcut from Lawnchair.", 15, false);
        intro.setTextColor(onSurface);
        page.addView(intro, margin(0, 0, 0, 22));

        TextView nameLabel = label("Project name");
        page.addView(nameLabel, margin(0, 0, 0, 6));
        nameInput = input("Name shown on your home screen", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        page.addView(nameInput, margin(0, 0, 0, 16));

        TextView urlLabel = label("ChatGPT project link");
        page.addView(urlLabel, margin(0, 0, 0, 6));
        urlInput = input("https://chatgpt.com/...", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        urlInput.setMinLines(2);
        urlInput.setGravity(Gravity.TOP | Gravity.START);
        page.addView(urlInput, margin(0, 0, 0, 14));

        Button save = new Button(this);
        save.setText("Save and publish shortcut");
        save.setTextColor(Color.WHITE);
        save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        save.setOnClickListener(v -> saveProjectLink());
        page.addView(save, margin(0, 0, 0, 12));

        status = text("The link stays in this Private Space. Lawnchair receives only the shortcut name and alias.", 13, false);
        status.setTextColor(onSurface);
        page.addView(status, margin(0, 0, 0, 28));

        TextView savedHeading = text("Saved project links", 19, true);
        savedHeading.setTextColor(onSurface);
        page.addView(savedHeading, margin(0, 0, 0, 10));
        savedLinks = new LinearLayout(this);
        savedLinks.setOrientation(LinearLayout.VERTICAL);
        page.addView(savedLinks);
        return scroll;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return result;
    }

    private TextView label(String value) {
        TextView result = text(value, 14, true);
        result.setTextColor(accent);
        return result;
    }

    private EditText input(String hint, int inputType) {
        EditText result = new EditText(this);
        result.setSingleLine(false);
        result.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        result.setHint(hint);
        result.setInputType(inputType);
        result.setPadding(dp(14), dp(12), dp(14), dp(12));
        result.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        return result;
    }

    private void acceptSharedLink(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String url = extractUrl(shared == null ? null : shared.toString());
        if (url == null) {
            status.setText("No web link was found in the shared content. Paste the project link below.");
            return;
        }
        urlInput.setText(url);
        urlInput.setSelection(urlInput.length());
        nameInput.requestFocus();
        status.setText("Link received. Enter a name and publish its private shortcut.");
    }

    private String extractUrl(String shared) {
        if (shared == null) return null;
        Matcher matcher = URL_PATTERN.matcher(shared);
        while (matcher.find()) {
            String candidate = matcher.group();
            while (!candidate.isEmpty() && ".,;:!?)]}".indexOf(candidate.charAt(candidate.length() - 1)) >= 0) {
                candidate = candidate.substring(0, candidate.length() - 1);
            }
            if (isChatGptUrl(candidate)) return candidate;
        }
        String trimmed = shared.trim();
        return isChatGptUrl(trimmed) ? trimmed : null;
    }

    private boolean isChatGptUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            return value.length() <= MAX_URL_LENGTH && uri.isHierarchical()
                    && "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getEncodedPath() != null && !"/".equals(uri.getEncodedPath())
                    && (host.equalsIgnoreCase("chatgpt.com")
                    || host.equalsIgnoreCase("www.chatgpt.com"));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void saveProjectLink() {
        String name = nameInput.getText().toString().trim();
        String url = urlInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInput.setError("Enter a project name");
            nameInput.requestFocus();
            return;
        }
        if (!isChatGptUrl(url)) {
            urlInput.setError("Enter an https ChatGPT project link");
            urlInput.requestFocus();
            return;
        }

        int slot = firstFreeSlot();
        if (slot < 0) {
            status.setText("All 10 shortcut slots are in use. Remove one saved link first.");
            return;
        }
        String id = ID_PREFIX + UUID.randomUUID();
        ComponentName alias = aliasFor(slot);
        try {
            getPackageManager().setComponentEnabledSetting(alias,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            boolean stored = getPreferences().edit()
                    .putString(key(slot, "title"), name)
                    .putString(key(slot, "url"), url)
                    .putString(key(slot, "id"), id)
                    .commit();
            if (!stored || !publishShortcut(slot, id, name, alias)) {
                getPreferences().edit().remove(key(slot, "title")).remove(key(slot, "url"))
                        .remove(key(slot, "id")).commit();
                getPackageManager().setComponentEnabledSetting(alias,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
                status.setText("Android could not publish the shortcut. Nothing was saved.");
                return;
            }
        } catch (RuntimeException error) {
            status.setText("Could not publish the shortcut: " + error.getClass().getSimpleName());
            return;
        }
        nameInput.setText("");
        urlInput.setText("");
        status.setText("Shortcut published. Open Lawnchair and add the new private project item.");
        renderSavedLinks();
    }

    private boolean publishShortcut(int slot, String id, String name, ComponentName alias) {
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        if (manager == null) return false;
        PersistableBundle marker = new PersistableBundle();
        marker.putInt(EXTRA_VERSION, 1);
        marker.putBoolean(EXTRA_PENDING, true);
        marker.putBoolean(EXTRA_TITLE_REQUIRED, false);
        Intent launch = new Intent(Intent.ACTION_MAIN).setComponent(alias);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(this, id)
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(Icon.createWithResource(this, R.drawable.ic_project_link))
                .setActivity(alias)
                .setIntent(launch)
                .setExtras(marker)
                .build();
        try {
            return manager.addDynamicShortcuts(Collections.singletonList(shortcut));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private int firstFreeSlot() {
        for (int slot = 1; slot <= SLOT_COUNT; slot++) {
            if (!getPreferences().contains(key(slot, "url"))) return slot;
        }
        return -1;
    }

    private ComponentName aliasFor(int slot) {
        return new ComponentName(this, PUBLISHER + ".ProjectLink" + String.format(java.util.Locale.ROOT, "%02d", slot));
    }

    private String key(int slot, String field) {
        return "slot." + slot + "." + field;
    }

    private android.content.SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void renderSavedLinks() {
        if (savedLinks == null) return;
        savedLinks.removeAllViews();
        boolean any = false;
        for (int slot = 1; slot <= SLOT_COUNT; slot++) {
            String url = getPreferences().getString(key(slot, "url"), null);
            if (url == null) continue;
            any = true;
            String name = getPreferences().getString(key(slot, "title"), "Project link");
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(10));
            card.setBackgroundColor(systemColor("system_neutral1_50", Color.WHITE));
            TextView itemTitle = text(name, 16, true);
            itemTitle.setTextColor(onSurface);
            TextView itemUrl = text(url, 12, false);
            itemUrl.setTextColor(onSurface);
            itemUrl.setMaxLines(2);
            card.addView(itemTitle);
            card.addView(itemUrl, margin(0, 4, 0, 4));
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            final int selectedSlot = slot;
            Button open = new Button(this);
            open.setText("Open in ChatGPT");
            open.setOnClickListener(v -> openInChatGpt(url));
            Button remove = new Button(this);
            remove.setText("Remove");
            remove.setOnClickListener(v -> removeLink(selectedSlot));
            actions.addView(open);
            actions.addView(remove);
            card.addView(actions);
            savedLinks.addView(card, margin(0, 0, 0, 10));
        }
        if (!any) {
            TextView empty = text("No links saved yet.", 14, false);
            empty.setTextColor(onSurface);
            savedLinks.addView(empty);
        }
    }

    private void removeLink(int slot) {
        new AlertDialog.Builder(this)
                .setTitle("Remove saved link?")
                .setMessage("Delete its Lawnchair home-screen item first. Existing icons for this link will stop working.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> removeLinkAfterConfirmation(slot))
                .show();
    }

    private void removeLinkAfterConfirmation(int slot) {
        String id = getPreferences().getString(key(slot, "id"), null);
        if (id != null) {
            ShortcutManager manager = getSystemService(ShortcutManager.class);
            if (manager != null) manager.removeDynamicShortcuts(Collections.singletonList(id));
        }
        getPreferences().edit().remove(key(slot, "title")).remove(key(slot, "url"))
                .remove(key(slot, "id")).commit();
        getPackageManager().setComponentEnabledSetting(aliasFor(slot),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        status.setText("Saved project link removed.");
        renderSavedLinks();
    }

    private void openInChatGpt(String url) {
        try {
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .setPackage(CHATGPT_PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(open);
        } catch (RuntimeException error) {
            Toast.makeText(this, "ChatGPT could not open this link in this profile.", Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
