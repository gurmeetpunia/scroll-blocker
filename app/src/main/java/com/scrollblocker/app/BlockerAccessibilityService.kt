package com.scrollblocker.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BlockerAccessibilityService : AccessibilityService() {

    companion object {
        // All packages we monitor
        val TARGET_PACKAGES = setOf(
            "com.google.android.youtube",        // YouTube
            "com.instagram.android",              // Instagram
            "com.zhiliaoapp.musically",           // TikTok (global)
            "com.ss.android.ugc.trill",           // TikTok (some regions)
            "com.android.chrome",                 // Chrome
            "org.mozilla.firefox",                // Firefox
            "com.microsoft.emmx",                 // Edge
            "com.opera.browser",                  // Opera
            "com.brave.browser",                  // Brave
            "com.sec.android.app.sbrowser"        // Samsung Internet
        )

        // Apps where the ENTIRE app is short-form video — block always
        val ALWAYS_BLOCK_PACKAGES = setOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill"
        )

        // Browser packages
        val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.brave.browser",
            "com.sec.android.app.sbrowser"
        )

        // URLs that indicate short-form vertical video content across all platforms
        val SHORT_VIDEO_URL_PATTERNS = listOf(
            "youtube.com/shorts",       // YouTube Shorts
            "instagram.com/reels",      // Instagram Reels
            "tiktok.com",               // TikTok (entire site is short video)
            "vm.tiktok.com",            // TikTok short share links
            "vt.tiktok.com",            // TikTok video links
            "snapchat.com/spotlight",   // Snapchat Spotlight
            "snapchat.com/discover",    // Snapchat Discover stories
            "x.com/i/reels",            // X (Twitter) Reels/video feed
            "twitter.com/i/reels",      // Twitter legacy Reels
            "facebook.com/reels",       // Facebook Reels
            "moj.in",                   // Moj short videos (popular in India)
            "mx.takatak.com",           // MX TakaTak
            "share.mxmojo.com"          // MX Mojo share links
        )

        const val PREFS_NAME = "scroll_blocker_prefs"
        const val KEY_ENABLED = "blocker_enabled"

        // After blocking a package, how long before we block it again.
        // YouTube/Instagram get 6s so user can reopen and switch to a safe tab.
        // TikTok gets 30s — the whole app is the problem.
        // Browsers get 4s grace.
        val PACKAGE_COOLDOWNS = mapOf(
            "com.google.android.youtube" to 6000L,
            "com.instagram.android"      to 6000L,
            "com.zhiliaoapp.musically"   to 30000L,
            "com.ss.android.ugc.trill"   to 30000L
        )
        val DEFAULT_COOLDOWN = 4000L
    }

    private lateinit var prefs: SharedPreferences

    // Per-package last-blocked timestamps to prevent the re-open loop
    private val lastBlockedPerPackage = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = (
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            )
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = (
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            )
            // Rebuild from companion so Samsung browser is included
            packageNames = TARGET_PACKAGES.toTypedArray()
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Check if the blocker is enabled via app toggle
        if (!prefs.getBoolean(KEY_ENABLED, true)) return

        val pkg = event.packageName?.toString() ?: return

        // Per-package cooldown — prevents the "re-open loop":
        // e.g. YouTube remembers Shorts was last open, so when user
        // reopens the app we give them a grace window to switch tabs.
        val now = System.currentTimeMillis()
        val cooldown = PACKAGE_COOLDOWNS[pkg] ?: DEFAULT_COOLDOWN
        val lastBlocked = lastBlockedPerPackage[pkg] ?: 0L
        if (now - lastBlocked < cooldown) return

        val shouldBlock = when {
            pkg in ALWAYS_BLOCK_PACKAGES -> true
            pkg == "com.google.android.youtube" -> isYouTubeShorts()
            pkg == "com.instagram.android" -> isInstagramReels()
            pkg in BROWSER_PACKAGES -> isBrowserShowingShortVideo(event)
            else -> false
        }

        if (shouldBlock) {
            lastBlockedPerPackage[pkg] = now
            goHome()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  YouTube Shorts Detection
    //
    //  Two entry points for Shorts:
    //  A) Via bottom nav Shorts icon → nav tab becomes selected
    //  B) Via home feed Shorts shelf → full-screen reel player
    //     opens WITHOUT changing the nav tab selection
    //
    //  We check both.
    // ─────────────────────────────────────────────────────────────
    private fun isYouTubeShorts(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            // ── A: Shorts tab selected in bottom navigation ────────
            val shortsNodes = root.findAccessibilityNodeInfosByText("Shorts")
            for (node in shortsNodes) {
                if (isNodeActiveInNav(node)) return true
            }

            // ── B: Full-screen Shorts player is open ───────────────
            // These view IDs are present whenever the Shorts reel
            // player is active, regardless of how it was opened.
            val shortsPlayerViewIds = listOf(
                "com.google.android.youtube:id/reel_player_page_container",
                "com.google.android.youtube:id/reel_watch_fragment_root",
                "com.google.android.youtube:id/shorts_container",
                "com.google.android.youtube:id/reel_player_haptic_feedback_overlay"
            )
            for (viewId in shortsPlayerViewIds) {
                if (root.findAccessibilityNodeInfosByViewId(viewId).isNotEmpty()) return true
            }

            // ── C: Fallback — scan all nodes for reel player markers
            // Catches future YouTube UI changes where view IDs shift
            // but still contain "reel_player" in their name.
            val allNodes = getAllNodes(root)
            for (node in allNodes) {
                val viewId = node.viewIdResourceName ?: continue
                if (viewId.contains("reel_player", ignoreCase = true)) return true
            }

            false
        } catch (e: Exception) {
            false
        } finally {
            root.recycle()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Instagram Reels Detection
    //  Checks if the "Reels" tab is currently selected in the
    //  bottom navigation bar of Instagram.
    // ─────────────────────────────────────────────────────────────
    private fun isInstagramReels(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            // Strategy 1: Look for "Reels" text in selected nav item
            val reelsNodes = root.findAccessibilityNodeInfosByText("Reels")
            for (node in reelsNodes) {
                if (isNodeActiveInNav(node)) return true
            }

            // Strategy 2: Check content description (Instagram uses content-desc for nav icons)
            val allNodes = getAllNodes(root)
            for (node in allNodes) {
                val contentDesc = node.contentDescription?.toString() ?: ""
                if (contentDesc.contains("reels", ignoreCase = true) && isNodeActiveInNav(node)) {
                    return true
                }
            }

            false
        } catch (e: Exception) {
            false
        } finally {
            root.recycle()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Browser Short Video Detection
    //
    //  Samsung Browser (and others) don't expose the URL as readable
    //  text in the accessibility tree while a page is playing.
    //
    //  Strategy:
    //  1. Try to read URL directly from known URL bar view IDs
    //  2. If that fails, perform ACTION_COPY on the URL bar node
    //     and read the result from the clipboard
    //  3. Fallback: scan all node text/contentDesc as last resort
    //
    //  Covers Chrome, Samsung Browser, Firefox, Edge, Opera, Brave.
    // ─────────────────────────────────────────────────────────────

    // Known URL bar view IDs across browsers
    private val URL_BAR_IDS = listOf(
        // Samsung Browser
        "com.sec.android.app.sbrowser:id/url_bar",
        "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.sec.android.app.sbrowser:id/sb_url_bar",
        // Chrome
        "com.android.chrome:id/url_bar",
        "com.android.chrome:id/search_box_text",
        // Firefox
        "org.mozilla.firefox:id/url_bar_title",
        "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        // Edge
        "com.microsoft.emmx:id/url_bar",
        // Opera
        "com.opera.browser:id/url_field",
        // Brave
        "com.brave.browser:id/url_bar"
    )

    private fun isBrowserShowingShortVideo(event: AccessibilityEvent): Boolean {
        // ── Step 1: Check event text (fired on page load) ──────────
        val eventText = event.text?.joinToString(" ") ?: ""
        if (containsShortVideoPattern(eventText)) return true

        val root = rootInActiveWindow ?: return false
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        return try {
            // ── Step 2: Try known URL bar IDs — direct text read ───
            for (id in URL_BAR_IDS) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (nodes.isNotEmpty()) {
                    val node = nodes[0]

                    // 2a. Read text directly
                    val directText = node.text?.toString() ?: ""
                    if (containsShortVideoPattern(directText)) return true

                    // 2b. Perform copy action → read clipboard
                    // This works even when text isn't exposed as readable
                    val beforeClip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    node.performAction(AccessibilityNodeInfo.ACTION_COPY)
                    val afterClip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""

                    // Only check if clipboard actually changed (avoid false positives)
                    if (afterClip != beforeClip && containsShortVideoPattern(afterClip)) return true
                }
            }

            // ── Step 3: Fallback — scan all nodes ──────────────────
            getAllNodes(root).forEach { node ->
                val text = node.text?.toString() ?: ""
                val desc = node.contentDescription?.toString() ?: ""
                if (containsShortVideoPattern(text) || containsShortVideoPattern(desc)) return true
            }

            false
        } catch (e: Exception) {
            false
        } finally {
            root.recycle()
        }
    }

    private fun containsShortVideoPattern(text: String): Boolean {
        if (text.isBlank()) return false
        return SHORT_VIDEO_URL_PATTERNS.any { text.contains(it, ignoreCase = true) }
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Checks if a node (or its parent/siblings) indicates the nav item is active/selected.
     * Navigation bars in modern apps mark selected items as `isSelected = true`.
     */
    private fun isNodeActiveInNav(node: AccessibilityNodeInfo): Boolean {
        if (node.isSelected || node.isChecked) return true
        val parent = node.parent ?: return false
        if (parent.isSelected || parent.isChecked) return true
        // Check siblings — sometimes the parent container is marked selected
        val grandParent = parent.parent ?: return false
        if (grandParent.isSelected || grandParent.isChecked) return true
        return false
    }

    /**
     * Recursively collects all accessibility nodes in the tree.
     */
    private fun getAllNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var safetyCounter = 0 // Prevent infinite loops on cyclic graphs
        while (queue.isNotEmpty() && safetyCounter < 500) {
            val node = queue.removeFirst()
            result.add(node)
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            safetyCounter++
        }
        return result
    }

    /**
     * Extracts all text from a node and its children.
     */
    private fun getTextFromNodeTree(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        sb.append(node.text ?: "")
        sb.append(node.contentDescription ?: "")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { sb.append(getTextFromNodeTree(it)) }
        }
        return sb.toString()
    }

    /**
     * Sends the user to the home screen immediately.
     */
    private fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {
        // Service interrupted — nothing to clean up
    }
}
