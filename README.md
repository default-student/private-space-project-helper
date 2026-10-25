# Private Space Shortcuts

This Android helper saves a name, link and target app as a Lawnchair shortcut. The selected app must be installed and launchable in the same Private Space as the helper. Links and target package names stay in that profile's app-private storage. The shortcut first opens the link with `ACTION_VIEW` restricted to the selected package. If that app has no handler for an HTTPS short link, the helper follows up to six HTTPS `Location` redirects, then retries the final URI with the same selected package. It does not fall back to another app or profile.

The package name `com.example.privatespaceprojects`, the `shared-project-` shortcut ID prefix, Lawnchair marker extras and the enabled `ProjectLink01` through `ProjectLink10` activity aliases are part of the Lawnchair integration. Keep them stable. Lawnchair must include the Private Space project-link confirmation support for dynamic shortcuts to become home-screen items.

The helper accepts HTTP and HTTPS URLs without user information or nonstandard ports, plus app-specific URI schemes. It rejects `intent:`, `file:`, `content:`, `javascript:` and `data:` URIs. For a failed HTTPS deep-link launch, it makes a bounded GET request to read redirect headers only; it does not store the resolved URI or read redirect page scripts. Redirects must remain HTTPS, use standard ports, and pass the same URI validation. Custom app schemes are never fetched. It passes only the URI to the selected package through a newly created `ACTION_VIEW` intent; it does not parse or launch an arbitrary serialized Android intent.

On GrapheneOS, allow the helper's **Network** permission in the Private Space profile if HTTPS redirect resolution is needed. The helper reports this specifically when the permission is denied.

## Build locally

The source is Java-only and builds with the Android SDK tools directly. Set these environment variables:

```sh
ANDROID_HOME=/path/to/android-sdk
ANDROID_DEBUG_KEYSTORE=/path/to/debug.keystore
ANDROID_DEBUG_KEYSTORE_PASSWORD=...
ANDROID_DEBUG_KEY_PASSWORD=...
```

In this checkout, the matching signing key is kept locally at `.secrets/android-signing/release.keystore`. The `.secrets/` directory is excluded from Git. Set `ANDROID_DEBUG_KEYSTORE` to that path and supply both passwords through the environment.

Then run (set a version code higher than the version already installed if needed):

```sh
./build-local.sh
```

The signed APK is written to `artifacts/private-space-project-helper.apk`. The signing key must match an earlier installation for Android to update it in place.

## GitHub releases

Pushing a version tag builds and publishes a GitHub Release with `private-space-project-helper.apk`. You can also run **Build and release APK** manually from the Actions tab; choose whether to publish the APK as a release. Configure the repository Actions secrets for the signing keystore and passwords. Do not commit the keystore or passwords.

## Install and use

Install the helper and the target app in the Private Space. Enter a shortcut name and a link, choose the app that should open it, and save. Then open Lawnchair and add the new private shortcut from its confirmation flow. Existing ChatGPT project links remain available and default to ChatGPT when they predate the saved target-app setting.

The app list contains launchable apps visible in the current profile. Apps without a launcher activity are not listed. The target app must support the URI supplied for the shortcut.
