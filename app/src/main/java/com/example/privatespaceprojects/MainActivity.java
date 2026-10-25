package com.example.privatespaceprojects;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.util.Log;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    static final String LOG_TAG = "PrivateSpaceShortcuts";
    static final int SLOT_COUNT = 10;
    static final int MAX_URL_LENGTH = 4096;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\\\"']+");

    private EditText nameInput;
    private EditText urlInput;
    private Spinner targetAppInput;
    private List<TargetApp> targetApps = Collections.emptyList();
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
        acceptSharedLink(getIntent(), getReferrer());
        refreshPublishedShortcutIcons();
        renderSavedLinks();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptSharedLink(intent, null);
        refreshPublishedShortcutIcons();
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

        TextView title = text("Private Space Shortcuts", 27, true);
        title.setTextColor(onSurface);
        page.addView(title, margin(0, 0, 0, 8));
        TextView intro = text("Save a link, choose an app in this Private Space, then add its shortcut from Lawnchair.", 15, false);
        intro.setTextColor(onSurface);
        page.addView(intro, margin(0, 0, 0, 22));

        TextView nameLabel = label("Shortcut name");
        page.addView(nameLabel, margin(0, 0, 0, 6));
        nameInput = input("Name shown on your home screen", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        page.addView(nameInput, margin(0, 0, 0, 16));

        TextView urlLabel = label("Link or app deep link");
        page.addView(urlLabel, margin(0, 0, 0, 6));
        urlInput = input("https://… or an app link", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        urlInput.setMinLines(2);
        urlInput.setGravity(Gravity.TOP | Gravity.START);
        page.addView(urlInput, margin(0, 0, 0, 14));

        TextView appLabel = label("Open with app in this Private Space");
        page.addView(appLabel, margin(0, 0, 0, 6));
        targetApps = loadTargetApps();
        targetAppInput = new Spinner(this);
        List<TargetApp> appOptions = new ArrayList<>();
        appOptions.add(new TargetApp("", "Choose an app in this Private Space"));
        appOptions.addAll(targetApps);
        ArrayAdapter<TargetApp> appAdapter = new TargetAppAdapter(appOptions);
        targetAppInput.setAdapter(appAdapter);
        if (targetApps.isEmpty()) {
            targetAppInput.setEnabled(false);
        }
        page.addView(targetAppInput, margin(0, 0, 0, 16));

        Button save = new Button(this);
        save.setText("Save and publish shortcut");
        save.setTextColor(Color.WHITE);
        save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        save.setOnClickListener(v -> saveProjectLink());
        page.addView(save, margin(0, 0, 0, 12));

        status = text(targetApps.isEmpty()
                ? "No launchable apps were found in this Private Space. Install an app here first."
                : "The link and selected app stay in this Private Space. Lawnchair receives only the shortcut name and alias.", 13, false);
        status.setTextColor(onSurface);
        page.addView(status, margin(0, 0, 0, 28));

        TextView savedHeading = text("Saved shortcuts", 19, true);
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

    private void acceptSharedLink(Intent intent, Uri activityReferrer) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        TargetApp sourceApp = sharedFromApp(intent, activityReferrer);
        if (sourceApp != null) {
            for (int index = 0; index < targetAppInput.getCount(); index++) {
                TargetApp option = (TargetApp) targetAppInput.getItemAtPosition(index);
                if (sourceApp.packageName.equals(option.packageName)) {
                    targetAppInput.setSelection(index);
                    break;
                }
            }
        } else {
            targetAppInput.setSelection(0);
        }
        CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String url = extractLink(shared == null ? null : shared.toString());
        if (url == null) {
            status.setText(sourceApp == null
                    ? "No supported link was found in the shared content. Paste it below and choose an app."
                    : "Shared from " + sourceApp.label + ". No supported link was found, so paste it below.");
            return;
        }
        urlInput.setText(url);
        urlInput.setSelection(urlInput.length());
        nameInput.requestFocus();
        status.setText(sourceApp == null
                ? "Link received. Choose the app that should open it, then publish the shortcut."
                : "Link received from " + sourceApp.label + ". The app was selected; change it if needed, then publish the shortcut.");
    }

    private TargetApp sharedFromApp(Intent intent, Uri activityReferrer) {
        List<String> packageCandidates = new ArrayList<>();
        addReferrerPackage(packageCandidates, activityReferrer);
        try {
            addReferrerPackage(packageCandidates,
                    intent.getParcelableExtra(Intent.EXTRA_REFERRER, Uri.class));
        } catch (RuntimeException ignored) {
            // Malformed referrer metadata should leave the app choice empty.
        }
        String referrerName = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME);
        if (referrerName != null) addReferrerPackage(packageCandidates, Uri.parse(referrerName));

        // Some share senders include their package through AndroidX ShareCompat metadata.
        String callingPackage = intent.getStringExtra("androidx.core.app.EXTRA_CALLING_PACKAGE");
        if (callingPackage == null) {
            callingPackage = intent.getStringExtra("android.support.v4.app.EXTRA_CALLING_PACKAGE");
        }
        if (callingPackage != null) packageCandidates.add(callingPackage);
        for (String packageName : packageCandidates) {
            TargetApp app = findTargetApp(targetApps, packageName);
            if (app != null) return app;
        }
        return null;
    }

    private void addReferrerPackage(List<String> candidates, Uri referrer) {
        if (referrer == null || !"android-app".equals(referrer.getScheme())) return;
        String packageName = referrer.getHost();
        if (packageName != null && !packageName.isEmpty()) candidates.add(packageName);
    }

    private String extractLink(String shared) {
        if (shared == null) return null;
        String trimmed = shared.trim();
        if (isAllowedLink(trimmed)) return trimmed;
        Matcher matcher = URL_PATTERN.matcher(shared);
        while (matcher.find()) {
            String candidate = matcher.group();
            while (!candidate.isEmpty() && ".,;:!?)]}".indexOf(candidate.charAt(candidate.length() - 1)) >= 0) {
                candidate = candidate.substring(0, candidate.length() - 1);
            }
            if (isAllowedLink(candidate)) return candidate;
        }
        return null;
    }

    static boolean isAllowedLink(String value) {
        try {
            if (value == null || value.isEmpty() || value.length() > MAX_URL_LENGTH
                    || value.matches(".*\\s+.*")) return false;
            Uri uri = Uri.parse(value);
            String scheme = uri.getScheme();
            if (scheme == null || !scheme.matches("[A-Za-z][A-Za-z0-9+.-]*")) return false;
            if ("intent".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme)
                    || "content".equalsIgnoreCase(scheme) || "javascript".equalsIgnoreCase(scheme)
                    || "data".equalsIgnoreCase(scheme)) return false;
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return uri.isHierarchical() && uri.getHost() != null
                        && uri.getUserInfo() == null
                        && (uri.getPort() == -1
                        || ("https".equalsIgnoreCase(scheme) && uri.getPort() == 443)
                        || ("http".equalsIgnoreCase(scheme) && uri.getPort() == 80));
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void saveProjectLink() {
        String name = nameInput.getText().toString().trim();
        String url = urlInput.getText().toString().trim();
        TargetApp target = targetApps.isEmpty() ? null
                : (TargetApp) targetAppInput.getSelectedItem();
        if (name.isEmpty()) {
            nameInput.setError("Enter a shortcut name");
            nameInput.requestFocus();
            return;
        }
        if (!isAllowedLink(url)) {
            urlInput.setError("Enter a valid web or app link");
            urlInput.requestFocus();
            return;
        }
        if (target == null || target.packageName.isEmpty()) {
            status.setText(targetApps.isEmpty()
                    ? "Install an app in this Private Space before saving a shortcut."
                    : "Choose the app that should open this link.");
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
                    .putString(key(slot, "package"), target.packageName)
                    .putString(key(slot, "id"), id)
                    .commit();
            if (!stored || !publishShortcut(slot, id, name, alias, target)) {
                getPreferences().edit().remove(key(slot, "title")).remove(key(slot, "url"))
                        .remove(key(slot, "package"))
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
        status.setText("Shortcut published. Link compatibility is checked when you open it in the selected app. Add it from Lawnchair.");
        renderSavedLinks();
    }

    private boolean publishShortcut(int slot, String id, String name, ComponentName alias,
            TargetApp target) {
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
                .setIcon(shortcutIcon(target))
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

    private Icon shortcutIcon(TargetApp target) {
        if (target == null || target.icon == null) {
            return Icon.createWithResource(this, R.drawable.ic_project_link);
        }
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        int iconLimit = manager == null ? 192
                : Math.min(manager.getIconMaxWidth(), manager.getIconMaxHeight());
        if (iconLimit <= 0) {
            return Icon.createWithResource(this, R.drawable.ic_project_link);
        }
        int size = Math.min(iconLimit, 512);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable.ConstantState iconState = target.icon.getConstantState();
        Drawable drawable = iconState == null
                ? target.icon : iconState.newDrawable(getResources(), getTheme());
        drawable = drawable.mutate();
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return Icon.createWithAdaptiveBitmap(bitmap);
    }

    private void refreshPublishedShortcutIcons() {
        for (int slot = 1; slot <= SLOT_COUNT; slot++) {
            String id = getPreferences().getString(key(slot, "id"), null);
            String name = getPreferences().getString(key(slot, "title"), null);
            String packageName = getPreferences().getString(key(slot, "package"), null);
            TargetApp target = findTargetApp(targetApps, packageName);
            if (id == null || name == null || target == null) continue;
            publishShortcut(slot, id, name, aliasFor(slot), target);
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
            String name = getPreferences().getString(key(slot, "title"), "Saved shortcut");
            String targetPackage = getPreferences().getString(key(slot, "package"), CHATGPT_PACKAGE);
            logLinkEvent("saved-slot-" + slot, url, targetPackage);
            String targetLabel = appLabel(targetPackage);
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
            TextView itemTarget = text("Opens with " + targetLabel, 12, false);
            itemTarget.setTextColor(onSurface);
            card.addView(itemTarget);
            card.addView(itemUrl, margin(0, 4, 0, 4));
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            final int selectedSlot = slot;
            Button open = new Button(this);
            open.setText("Open");
            open.setOnClickListener(v -> openLink(url, targetPackage));
            Button remove = new Button(this);
            remove.setText("Remove");
            remove.setOnClickListener(v -> removeLink(selectedSlot));
            actions.addView(open);
            actions.addView(remove);
            card.addView(actions);
            savedLinks.addView(card, margin(0, 0, 0, 10));
        }
        if (!any) {
            TextView empty = text("No shortcuts saved yet.", 14, false);
            empty.setTextColor(onSurface);
            savedLinks.addView(empty);
        }
    }

    private void removeLink(int slot) {
        new AlertDialog.Builder(this)
                .setTitle("Remove saved link?")
                .setMessage("Delete its Lawnchair home-screen item first. Existing icons for this shortcut will stop working.")
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
                .remove(key(slot, "package")).remove(key(slot, "id")).commit();
        getPackageManager().setComponentEnabledSetting(aliasFor(slot),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        status.setText("Saved shortcut removed.");
        renderSavedLinks();
    }

    private void openLink(String url, String targetPackage) {
        openTargetLink(this, "saved-link", url, targetPackage,
                networkPermissionDenied -> Toast.makeText(this,
                        networkPermissionDenied
                                ? "Allow Network permission for Private Space Shortcuts in Settings, then retry."
                                : "The selected app cannot open this link or its HTTPS redirects.",
                        Toast.LENGTH_LONG).show(), () -> {});
    }

    static void openTargetLink(Activity activity, String stage, String url,
                               String targetPackage, LinkFailureHandler onFailure,
                               Runnable onSuccess) {
        Uri original = Uri.parse(url);
        logLinkEvent(stage, url, targetPackage);
        try {
            startTarget(activity, original, targetPackage);
            onSuccess.run();
            return;
        } catch (ActivityNotFoundException error) {
            logLinkFailure(stage, targetPackage, error);
        }
        if (!"https".equalsIgnoreCase(original.getScheme())) {
            onFailure.onFailure(false);
            return;
        }
        if (activity.checkSelfPermission(Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(LOG_TAG, stage + " redirect denied reason=internet-permission");
            onFailure.onFailure(true);
            return;
        }
        logLinkEvent(stage + "-redirect-check", url, targetPackage);
        Thread resolver = new Thread(() -> {
            Uri resolved = resolveHttpsRedirects(stage, original);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (resolved == null || resolved.equals(original)) {
                    Log.w(LOG_TAG, stage + " redirect unresolved target=" + targetPackage);
                    onFailure.onFailure(false);
                    return;
                }
                logLinkEvent(stage + "-redirected", resolved.toString(), targetPackage);
                try {
                    startTarget(activity, resolved, targetPackage);
                    onSuccess.run();
                } catch (RuntimeException error) {
                    logLinkFailure(stage + "-redirected", targetPackage, error);
                    onFailure.onFailure(false);
                }
            });
        }, "private-shortcut-link-resolver");
        resolver.setDaemon(true);
        resolver.start();
    }

    private static void startTarget(Activity activity, Uri uri, String targetPackage) {
        Intent open = new Intent(Intent.ACTION_VIEW, uri)
                .setPackage(targetPackage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(open);
    }

    interface LinkFailureHandler {
        void onFailure(boolean networkPermissionDenied);
    }

    private static Uri resolveHttpsRedirects(String stage, Uri start) {
        Uri current = start;
        boolean followedRedirect = false;
        Set<String> visited = new HashSet<>();
        long deadline = SystemClock.elapsedRealtime() + 12000;
        for (int hop = 0; hop < 6; hop++) {
            int remaining = (int) (deadline - SystemClock.elapsedRealtime());
            if (remaining <= 0) return null;
            if (!visited.add(current.toString())) return null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URI(current.toString()).toURL().openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(Math.min(4000, remaining));
                connection.setReadTimeout(Math.min(4000, remaining));
                connection.setRequestMethod("GET");
                connection.setUseCaches(false);
                connection.setAllowUserInteraction(false);
                int response = connection.getResponseCode();
                Log.i(LOG_TAG, stage + " redirect hop=" + hop + " status=" + response
                        + " scheme=" + current.getScheme() + " host=" + current.getHost());
                if (response < 300 || response >= 400) return followedRedirect ? current : null;
                String location = connection.getHeaderField("Location");
                if (location == null || location.trim().isEmpty()) return null;
                URI nextValue = new URI(current.toString()).resolve(location.trim());
                Uri next = Uri.parse(nextValue.toString());
                if (!isAllowedLink(next.toString())
                        || !"https".equalsIgnoreCase(next.getScheme())) {
                    Log.w(LOG_TAG, stage + " redirect rejected scheme=" + next.getScheme()
                            + " host=" + next.getHost());
                    return null;
                }
                Log.i(LOG_TAG, stage + " redirect location scheme=" + next.getScheme()
                        + " host=" + next.getHost());
                current = next;
                followedRedirect = true;
            } catch (Exception error) {
                Log.w(LOG_TAG, "redirect resolution failed exception="
                        + error.getClass().getSimpleName());
                return null;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return followedRedirect ? current : null;
    }

    static void logLinkEvent(String stage, String url, String targetPackage) {
        Uri uri = Uri.parse(url);
        Log.i(LOG_TAG, stage + " scheme=" + uri.getScheme() + " host=" + uri.getHost()
                + " target=" + targetPackage);
    }

    static void logLinkFailure(String stage, String targetPackage, RuntimeException error) {
        Log.w(LOG_TAG, stage + " failed target=" + targetPackage + " exception="
                + error.getClass().getSimpleName());
    }

    private List<TargetApp> loadTargetApps() {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> resolved = packageManager.queryIntentActivities(launcherIntent, 0);
        List<TargetApp> apps = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || info.activityInfo.packageName.equals(getPackageName())) continue;
            String packageName = info.activityInfo.packageName;
            TargetApp existing = findTargetApp(apps, packageName);
            if (existing == null) {
                CharSequence label = info.loadLabel(packageManager);
                Drawable icon = null;
                try {
                    icon = info.loadIcon(packageManager);
                } catch (RuntimeException ignored) {
                    // Keep the app selectable if its icon cannot be loaded.
                }
                apps.add(new TargetApp(packageName,
                        label == null ? packageName : label.toString(), icon));
            }
        }
        Collections.sort(apps, Comparator.comparing(app -> app.label.toLowerCase(Locale.ROOT)));
        return apps;
    }

    private TargetApp findTargetApp(List<TargetApp> apps, String packageName) {
        for (TargetApp app : apps) {
            if (app.packageName.equals(packageName)) return app;
        }
        return null;
    }

    private String appLabel(String packageName) {
        TargetApp app = findTargetApp(targetApps, packageName);
        return app == null ? packageName : app.label;
    }

    private static final class TargetApp {
        final String packageName;
        final String label;
        final Drawable icon;

        TargetApp(String packageName, String label) {
            this(packageName, label, null);
        }

        TargetApp(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final class TargetAppAdapter extends ArrayAdapter<TargetApp> {
        TargetAppAdapter(List<TargetApp> apps) {
            super(MainActivity.this, 0, apps);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return appRow(getItem(position), convertView, false);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return appRow(getItem(position), convertView, true);
        }
    }

    private View appRow(TargetApp app, View convertView, boolean dropdown) {
        LinearLayout row;
        ImageView icon;
        TextView label;
        if (convertView instanceof LinearLayout
                && convertView.getTag() instanceof TargetAppRow) {
            row = (LinearLayout) convertView;
            TargetAppRow holder = (TargetAppRow) row.getTag();
            icon = holder.icon;
            label = holder.label;
        } else {
            row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            icon = new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            label = text("", 16, false);
            label.setTextColor(onSurface);
            row.addView(icon);
            row.addView(label, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setTag(new TargetAppRow(icon, label));
        }

        row.setPadding(dp(8), dp(dropdown ? 6 : 2), dp(8), dp(dropdown ? 6 : 2));
        row.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams iconParams = (LinearLayout.LayoutParams) icon.getLayoutParams();
        iconParams.width = dp(dropdown ? 40 : 32);
        iconParams.height = dp(dropdown ? 40 : 32);
        iconParams.setMargins(0, 0, app.icon == null ? 0 : dp(12), 0);
        icon.setLayoutParams(iconParams);
        icon.setImageDrawable(app.icon);
        icon.setVisibility(app.icon == null ? View.GONE : View.VISIBLE);
        label.setText(app.label);
        return row;
    }

    private static final class TargetAppRow {
        final ImageView icon;
        final TextView label;

        TargetAppRow(ImageView icon, TextView label) {
            this.icon = icon;
            this.label = label;
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
