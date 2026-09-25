package com.unhooked.extension;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Runtime configuration backed by SharedPreferences ("unhooked_prefs").
 *
 * Independent block toggles (feed, explore, reels, stories, instants, notes,
 * suggested, ads). The application Context is resolved reflectively so no
 * patched class needs to hand us one.
 */
public final class Config {

    private static final String PREFS = "unhooked_prefs";

    /**
     * Set once any setting changes in this process. While true, leaving the
     * settings page forces a clean restart instead of returning to a stale UI.
     * Cleared implicitly on every process start.
     */
    private static boolean sNeedsRestart;

    private Config() {}

    /** Resolve the process Application context via ActivityThread, or null. */
    public static Context getAppContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object app = activityThread.getMethod("currentApplication").invoke(null);
            return (Context) app;
        } catch (Throwable t) {
            return null;
        }
    }

    private static SharedPreferences prefs() {
        Context context = getAppContext();
        return context == null ? null : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean getBlocked(String key, boolean defaultValue) {
        SharedPreferences prefs = prefs();
        return prefs == null ? defaultValue : prefs.getBoolean(key, defaultValue);
    }

    public static void setBlocked(String key, boolean value) {
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        prefs.edit().putBoolean(key, value).apply();
    }

    /** Whether to check GitHub for a newer release on launch. On by default. */
    public static boolean isAutoUpdateEnabled() {
        return getBlocked("auto_update", true);
    }

    /**
     * Whether Instagram's window is pinned to SDR (COLOR_MODE_DEFAULT) instead of
     * the HDR mode Instagram forces on some accounts. On by default: the HDR
     * window mode only lifts the black floor of the dark UI (washed-out fond) with
     * no visible benefit.
     */
    public static boolean isForceSdr() { return getBlocked("force_sdr", true); }

    /**
     * Whether screenshots and screen recordings are allowed in sensitive
     * surfaces (view-once media, disappearing DMs): FLAG_SECURE is stripped
     * from Instagram's windows, recents thumbnails are forced on, and the
     * screenshot-taken report to the sender is dropped (see {@link Block}).
     * Off by default: it changes what the other side can observe, so it is
     * strictly opt-in.
     */
    public static boolean isScreenshotBypassEnabled() { return getBlocked("allow_screenshots", false); }

    /**
     * Whether the home feed is restricted to accounts you follow (chronological
     * "Following" feed) instead of the recommended feed. Off by default.
     */
    public static boolean isFollowingFeedOnly() {
        return getBlocked("limit_following_feed", false);
    }

    public static boolean isFeedBlocked()      { return getBlocked("block_feed", true); }
    public static boolean isExploreBlocked()   { return getBlocked("block_explore", true); }
    public static boolean isReelsBlocked()     { return getBlocked("block_reels", true); }
    /**
     * Whether the "Friends" tab and its avatar facepile are hidden from the Reels
     * viewer's header. On by default: Instagram gates that entry point behind a
     * server flag ({@code friends_lane_floating_pogs_entrypoint_enabled}) only
     * reachable from an internal developer menu, so the only way out is to drop
     * it from the view tree — issue #94.
     */
    public static boolean isFriendsLaneBlocked() { return getBlocked("block_friends_lane", true); }
    public static boolean isStoriesBlocked()   { return getBlocked("block_stories", false); }
    public static boolean isInstantsBlocked()  { return getBlocked("block_instants", true); }
    public static boolean isNotesBlocked()     { return getBlocked("block_notes", true); }
    public static boolean isSuggestedBlocked() { return getBlocked("block_suggested", true); }
    public static boolean isAdsBlocked()       { return getBlocked("block_ads", true); }

    /**
     * Whether the notifications ("heart") button in the feed header is hidden.
     * Off by default: it's an opt-in distraction cut, and people expect their
     * notifications entry-point to still be there after an update.
     */
    public static boolean isNotificationsButtonBlocked() { return getBlocked("block_notifications", false); }

    /**
     * Whether Instagram's popups (its own toasts: "Couldn't refresh feed",
     * "Impossible d'actualiser le fil", ...) are dropped. On by default: blocking
     * a surface makes Instagram see a failed request and raise one every time, so
     * they are noise the mod creates itself. All of them go, not just the errors —
     * see {@link Toasts}. Deliberately not a {@code block_*} key.
     */
    public static boolean arePopupsHidden() { return getBlocked("hide_toasts", true); }

    /**
     * Bottom-navigation icon visibility, stored per tab as {@code nav_show_<tab>}
     * (true = shown). Independent of the content blocks, so hiding the Reels
     * *icon* is decoupled from blocking Reels *content*. Home is deliberately not
     * configurable: long-pressing it is the only Settings entry point, so it must
     * stay present and tappable. Reels defaults to hidden to preserve the previous
     * behaviour (blocking reels used to also hide the tab).
     */
    public static boolean isReelsTabShown() { return getBlocked("nav_show_reels", false); }

    /**
     * Whether the first-run coach mark (long-press Home to open settings) has
     * already been shown on this installation. Set once, never cleared: the guide
     * is a one-time thing, not a setting.
     */
    public static boolean isOnboardingDone() { return getBlocked("onboarding_done", false); }

    public static void setOnboardingDone() { setBlocked("onboarding_done", true); }

    /** Navigation-bar icons, with the default the getters use (true = shown). */
    private static final String[] NAV_KEYS = {
            "nav_show_search", "nav_show_reels", "nav_show_create",
            "nav_show_direct", "nav_show_profile",
    };

    /** Only Reels defaults to hidden; see {@link #isReelsTabShown()}. */
    public static boolean navDefault(String key) {
        return !"nav_show_reels".equals(key);
    }

    /** Whether the bottom-bar icon for a {@code nav_show_*} key is currently shown. */
    public static boolean isNavTabShown(String navKey) {
        return getBlocked(navKey, navDefault(navKey));
    }

    /**
     * The {@code nav_show_*} key whose icon a landing page rides on, or null for
     * "home" (whose tab is never configurable) and for anything unrecognised.
     */
    public static String navKeyForLanding(String landing) {
        if ("search".equals(landing)) return "nav_show_search";
        if ("direct".equals(landing)) return "nav_show_direct";
        if ("profile".equals(landing)) return "nav_show_profile";
        return null;
    }

    /**
     * Whether a surface may be used as the landing page. Landing on a tab that was
     * hidden from the bottom bar would hand back the very surface the user removed,
     * so a hidden tab is not a candidate.
     */
    public static boolean isLandingAvailable(String landing) {
        String navKey = navKeyForLanding(landing);
        return navKey == null || isNavTabShown(navKey);
    }

    /**
     * The surface the app should jump to on cold start: one of "home" (default),
     * "search", "direct", "profile".
     */
    public static String getLandingPage() {
        SharedPreferences prefs = prefs();
        String stored = prefs == null ? "home" : prefs.getString("landing_page", "home");
        // The stored choice is kept as-is so re-showing the tab restores it, but a
        // landing page whose tab is hidden right now falls back to the home feed.
        return isLandingAvailable(stored) ? stored : "home";
    }

    public static void setLandingPage(String value) {
        if (!isLandingAvailable(value)) return;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        prefs.edit().putString("landing_page", value).apply();
    }

    /**
     * The app version whose release notes have already been shown, or null on a
     * fresh install. Compared against the installed version on launch so the
     * "What's new" card appears exactly once after an update.
     */
    public static String getLastSeenVersion() {
        SharedPreferences prefs = prefs();
        return prefs == null ? null : prefs.getString("last_seen_version", null);
    }

    public static void setLastSeenVersion(String value) {
        SharedPreferences prefs = prefs();
        if (prefs == null || value == null) return;
        prefs.edit().putString("last_seen_version", value).apply();
    }

    public static void setNeedsRestart() { sNeedsRestart = true; }

    public static boolean isRestartPending() { return sNeedsRestart; }
}
