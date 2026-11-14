<div align="center">

<img src="https://socialify.git.ci/crimera/piko/image?forks=1&language=1&name=1&owner=1&pattern=Circuit%20Board&stargazers=1&theme=Auto" alt="piko" width="640" height="320" />

<h1>
    Morphe patches focused on Twitter/X & Instagram<br>
    <a href="https://t.me/pikopatches">
        <img src="https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram"/>
    </a>
    <a href="https://crowdin.com/project/piko">
        <img src="https://img.shields.io/badge/Crowdin-1B263B?style=for-the-badge&logo=crowdin&logoColor=white" alt="Crowdin"/>
    </a>
</h1>

</div>

## 🕹️ Usage

Starting with patches v3.0.0, Piko patches use [Morphe](https://morphe.software).

### Morphe Manager

Tap the button to add Piko as a patch source in Morphe Manager:

<a href="https://morphe.software/add-source?github=crimera/piko">
  <img src="docs/images/add-to-morphe.svg" alt="Add to Morphe" height="36">
</a>

Then patch Twitter/X or Instagram:
1. Tap X or Instagram app icon in Morphe
2. Download original APKM file from ApkMirror. Do _not_ unspilt or modify the file, Morphe patches APKM directly
3. Wait for patching to complete, install

### Morphe CLI

```sh
java -jar cli.jar patch --patches piko.mpp input.apkm
```

### X-Shim

Starting `12.5.0-release.0` **you need not** apply X-Shim along with Piko patches. X login and XChat will work fine without X-Shim patches.

<details>

<summary>Open old information</summary>

<del>

To patch with Twitter/X version 11.88 to 12.4, you must include patches from another repo/project called [X-Shim](https://gitlab.com/inotia00/x-shim) developed by [@inotia00](https://github.com/inotia00)

[➕ Add X-Shim to Morphe](https://morphe.software/add-source?gitlab=inotia00/x-shim)

⚠️Important steps⚠️
* Enable `expert mode` in Morphe Manager.
* You must select all patches from the X Shim bundle and recommended/preferred patches from Piko. **DO NOT INCLUDE** patches from any other bundles.
* Select "Proceed anyway" at the prompt warning of using multiple bundles.

⚠️Additional Context⚠️

* An active internet connection is required during patching.
* XChat decryption and XChat video calls work. 
* The X Shim patch only adds a compatibility shim layer and pairip is not removed.

> For an up-to-date patching guide, [follow this Reddit guide](https://www.reddit.com/r/MorpheApp/comments/1r4xt24/x_twitter_can_now_be_patched_with_piko_patches/).

</del>

</details>

## ⚙️ Patch Details

<!-- PATCHES_START -->
> **[v1.4.1](https://github.com/ABHIJAT-DEV/piko/releases/tag/v1.4.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;67 patches total
<details>
<summary>📦 Instagram&nbsp;&nbsp;•&nbsp;&nbsp;67 patches</summary>
<br>

**🎯 Supported versions:**

| 452.1.0.53.64 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Add settings](#add-settings) | Adds settings to control preferences are patching |  |
| [Change like animation](#change-like-animation) | Change the animation to one from existing Rings like animations |  |
| [Clone](#clone) | Changes the package name and the app name. This allows you to install the patched app alongside the original Instagram app. | • Package name<br>• App name |
| [Copy comment](#copy-comment) | Adds buttons to copy comments and their authors' usernames on posts and reels. |  |
| [Custom font](#custom-font) | Adds an option to replace the app font with a font file from the device storage. |  |
| [Custom sharing domain](#custom-sharing-domain) | Allows for using custom domains when sharing posts, reels and stories. |  |
| [Customise story ring size](#customise-story-ring-size) |  |  |
| [Customise story timestamp](#customise-story-timestamp) | Customise the timestamp that shows when the story was posted |  |
| [Customize navigation bar](#customize-navigation-bar) | Choose which tabs appear in the bottom navigation bar and reorder them |  |
| [Device spoofing](#device-spoofing) | Reports the device model chosen here to Instagram, for features gated to other phones such as HDR playback quality. | • Device model<br>• Custom manufacturer<br>• Custom brand<br>• Custom model<br>• Custom device codename |
| [Disable Play Store updates](#disable-play-store-updates) | Disables Play Store updates by setting the version code to the maximum allowed. This patch may cause unexpected issues with some apps and does not work if the app is installed by root mounting |  |
| [Disable Reels scrolling](#disable-reels-scrolling) | Disables the endless scrolling behavior in Instagram Reels, preventing swiping to the next Reel. Note: On a clean install, the 'Tip' animation may appear but will stop on its own after a few seconds. |  |
| [Disable ads](#disable-ads) |  |  |
| [Disable comments](#disable-comments) | Disables comments and hides comment buttons on posts, reels and stories |  |
| [Disable disappearing messages swipe](#disable-disappearing-messages-swipe) | Prevents swiping up in a chat from enabling disappearing messages |  |
| [Disable discover people](#disable-discover-people) | Hides suggested accounts |  |
| [Disable double tap like](#disable-double-tap-like) | Disable double tap like on post, reel, comment and message |  |
| [Disable explore](#disable-explore) |  |  |
| [Disable highlights](#disable-highlights) |  |  |
| [Disable screenshot detection](#disable-screenshot-detection) | Disables screenshots detection in DM |  |
| [Disable stories](#disable-stories) |  |  |
| [Disable story flipping](#disable-story-flipping) | Disable automatic flipping/moving to next story |  |
| [Disable swipe to create](#disable-swipe-to-create) | Prevents opening the creation screen by swiping right on the home tab. |  |
| [Disable typing status](#disable-typing-status) |  |  |
| [Disable video autoplay](#disable-video-autoplay) |  |  |
| [Download media](#download-media) | Adds ability to download posts, reels, stories and highlights |  |
| [Download voice message](#download-voice-message) | Enables ability to download voice messages |  |
| [External downloader](#external-downloader) | Adds support to share post links directly to external downloader |  |
| [Filter stories](#filter-stories) | Filter stories to hide based on different categories |  |
| [Focus Lock](#focus-lock) | Commitment mode for cutting down on Instagram. Pick what to block (Reels, Explore) and a duration; once locked those protections are forced on and cannot be switched off. Reels shared with you still open. Unlocking early requires a 24 hour cooling-off period, and resetting or importing settings is blocked while locked. |  |
| [Friendship status indicator](#friendship-status-indicator) | Adds a follows you back status label on the profile page and shows a detailed friendship status breakdown on click |  |
| [Hidden chats](#hidden-chats) | Adds an option to the chat menu that moves the chat to a private inbox, opened by tapping the messages button several times. |  |
| [Hide Reels follow button](#hide-reels-follow-button) | Removes the follow button from Reels. |  |
| [Hide feed posts](#hide-feed-posts) | Hides posts from the home feed |  |
| [Hide group creation button on sharesheet](#hide-group-creation-button-on-sharesheet) |  |  |
| [Hide hidden requests](#hide-hidden-requests) | Hides the hidden requests (spam) button in the message requests page |  |
| [Hide like buttons](#hide-like-buttons) | Hides like buttons on posts, reels and stories |  |
| [Hide notes tray](#hide-notes-tray) | Hides notes tray in DM section |  |
| [Hide reshare button](#hide-reshare-button) | Hides the reshare button from both posts and reels. |  |
| [Hide save buttons](#hide-save-buttons) | Hides save buttons on posts and reels |  |
| [Hide share button](#hide-share-button) | Hides the share button on posts, reels and stories |  |
| [Hide stories tray](#hide-stories-tray) | Hides stories tray from main feed. |  |
| [Hide story follow button](#hide-story-follow-button) | Hides the follow button at the top of stories. |  |
| [Hide suggested content](#hide-suggested-content) | Hides suggested stories, reels, threads (Suggested posts will still be shown). |  |
| [Improve image viewing](#improve-image-viewing) | Fetches max resolution images from server. |  |
| [Inbox lock](#inbox-lock) | Asks for your fingerprint, face or screen lock before the inbox is shown. |  |
| [Limit feed to following profiles](#limit-feed-to-following-profiles) | Filters the home feed to display only content from profiles you follow. |  |
| [Loop story](#loop-story) | Replay the current story when it ends |  |
| [Make ephemeral media permanent](#make-ephemeral-media-permanent) | Changes unexpired view once, view twice media to permanent view. |  |
| [Mark as read after reply](#mark-as-read-after-reply) | Adds an option to mark chats as read after replying while viewing DMs anonymously. |  |
| [Mark chat as read manually](#mark-chat-as-read-manually) | Adds option to mark a thread aka message as read manually |  |
| [More options on post](#more-options-on-post) | Adds an overflow menu button to get more options on post/reels, like copy description, copy username etc |  |
| [More options on profile](#more-options-on-profile) | Adds a new button to handle user related data like copy handle, download profile picture etc |  |
| [Open links externally](#open-links-externally) | Changes links to always open in your external browser, instead of the in-app browser. |  |
| [Recommended flags](#recommended-flags) | Developer flags suggested by the community |  |
| [Remove empty bottom space](#remove-empty-bottom-space) | Removes empty space below bottom navigation bar |  |
| [Resume Reels after scrolling](#resume-reels-after-scrolling) | Resumes videos from where you left off when you scroll back to them in Reels |  |
| [Sanitize share links](#sanitize-share-links) |  |  |
| [Save Instants](#save-instants) | Captures view-once Instants as you view them so you can re-view and download them later. |  |
| [Save deleted messages](#save-deleted-messages) | Captures incoming DMs locally as they arrive from the server and marks them when the sender deletes them. |  |
| [Save media comment](#save-media-comment) | Adds a button to save media comments on posts and reels. |  |
| [Theme](#theme) | Adds Material You and AMOLED controls to Piko settings on Android 12 and later. On Android 8–11, it applies a fixed Material You-style theme or an optional AMOLED theme. | • Pure-black AMOLED theme for Android 8–11 |
| [Unlock developer options](#unlock-developer-options) | Unlocks developer option by long pressing home icon |  |
| [View DMs anonymously](#view-dms-anonymously) |  |  |
| [View live anonymously](#view-live-anonymously) |  |  |
| [View stories anonymously](#view-stories-anonymously) |  |  |
| [View story mentions](#view-story-mentions) | Add option to view visible and hidden story mentions. |  |

</details>

<!-- PATCHES_END -->

## 🛠️ Building

To build Piko Patches, follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## Contributing

Thank you for considering contributing to Piko Patches.

- If you plan to add a new feature, it is recommended to open an issue to discuss it with the
maintainers of Piko beforehand. This will help you determine whether your feature is acceptable
and whether it is worth your time to implement it. (In the issue, please clearly state that you
intend to work on this feature.)
- Development happens on the `dev` branch. Create your branch from `dev`. Submit a pull request to `dev`.
- Our team will review your pull request and provide feedback. Once your pull request is approved,
it will be merged into the `dev` branch and will be included in the next release of patches.
- Currently, development for the New X Android app (latest version of X) is taking place on the `x-lite` branch.
- You can contribute translations at [Crowdin](https://crowdin.com/project/piko).

## License

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

These patches are fully FOSS redistributable and modifiable under the [GNU General Public License v3](https://www.gnu.org/licenses/gpl.html) or later.
