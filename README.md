# Private Space Projects

Private Space Projects is a small Android helper for saving ChatGPT project links as Lawnchair shortcuts from a separate Android profile. Its package name is `com.example.privatespaceprojects`; Lawnchair uses this fixed package and the enabled activity aliases to recognize published links.

The helper stores project URLs only in its app-private profile storage. It does not fall back to opening links in another profile. Lawnchair must include the Private Space project-link confirmation support for the dynamic shortcuts to become home-screen items.

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

For example, use `VERSION_CODE=100003 VERSION_NAME=0.1.1 ./build-local.sh` to build an update over the initial `v0.1.0` release.

The signed APK is written to `artifacts/private-space-project-helper.apk`. The signing key must be the same key as earlier installations if you want Android to update the app in place. The test APK previously built from this project used the Android debug certificate with SHA-256 `29e00e98c2637cabe800a4e2eb6b5acd33449ff302f276cf6c24855af92bf1dd`.

## GitHub releases

Pushing a version tag such as `v0.2.0` builds and publishes a GitHub Release with `private-space-project-helper.apk`. You can also run **Build and release APK** manually from the Actions tab; choose whether to publish the APK as a release. The release APK uses the configured signing key, so existing installations update without uninstalling when that key matches.

Configure these repository Actions secrets before publishing:

- `ANDROID_SIGNING_KEYSTORE_BASE64`: base64 encoding of the existing signing keystore.
- `ANDROID_SIGNING_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_SIGNING_KEY_ALIAS`: key alias.
- `ANDROID_SIGNING_KEY_PASSWORD`: key password.

Do not commit the keystore or passwords. The configured Actions secrets use the same signing key kept locally at `.secrets/android-signing/release.keystore`. If a different key is used, Android will require uninstalling the old app before installing the release APK.

## Install and update

Download `private-space-project-helper.apk` from the [latest GitHub Release](https://github.com/default-student/private-space-project-helper/releases/latest), enable installing apps from the download source if Android asks, then open the APK to install. Future release APKs signed with the same key install as updates. Install the helper in the profile where you want to share and open project links. The helper has been designed to work with Lawnchair's Private Space support; this repository does not include Lawnchair itself.

To receive updates with Obtainium, add `https://github.com/default-student/private-space-project-helper` as a source and select the `private-space-project-helper.apk` release asset. Obtainium checks the repository's GitHub Releases for newer APKs.

For the manual integration flow, install the helper in Private Space, use ChatGPT's project share action, choose **Private Space Projects**, save a name, then add the shortcut from Lawnchair's confirmation card. Verify that the link opens ChatGPT in the same profile. The project shortcut feature still depends on the corresponding Lawnchair build.
