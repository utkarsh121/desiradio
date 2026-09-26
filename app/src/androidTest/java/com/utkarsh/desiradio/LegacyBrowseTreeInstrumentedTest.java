package com.utkarsh.desiradio;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Talks to RadioService using android.support.v4.media.MediaBrowserCompat —
 * the exact legacy MediaBrowserService protocol real Android Auto / Android
 * Automotive head units speak (Media3's MediaLibrarySessionLegacyStub is
 * what answers on the other end). This is a faithful on-device reproduction
 * of what a car does when it connects, browses "root", and pages through
 * "All Stations" — the scenario reported as "No items" in the README.
 */
@RunWith(AndroidJUnit4.class)
public class LegacyBrowseTreeInstrumentedTest {

    private MediaBrowserCompat connect(Context ctx) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> failed = new AtomicReference<>(false);
        MediaBrowserCompat browser = new MediaBrowserCompat(ctx,
                new ComponentName(ctx, RadioService.class),
                new MediaBrowserCompat.ConnectionCallback() {
                    @Override public void onConnected() { latch.countDown(); }
                    @Override public void onConnectionFailed() { failed.set(true); latch.countDown(); }
                }, null);
        browser.connect();
        assertTrue("Timed out connecting to RadioService", latch.await(10, TimeUnit.SECONDS));
        assertFalse("onConnectionFailed()", failed.get());
        assertTrue("MediaBrowserCompat reports not connected", browser.isConnected());
        return browser;
    }

    private List<MediaBrowserCompat.MediaItem> subscribe(
            MediaBrowserCompat browser, String parentId, Bundle options) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<MediaBrowserCompat.MediaItem>> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        MediaBrowserCompat.SubscriptionCallback cb = new MediaBrowserCompat.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(String parentId, List<MediaBrowserCompat.MediaItem> children) {
                result.set(children);
                latch.countDown();
            }
            @Override
            public void onChildrenLoaded(String parentId, List<MediaBrowserCompat.MediaItem> children, Bundle options) {
                result.set(children);
                latch.countDown();
            }
            @Override
            public void onError(String parentId) {
                error.set("onError(" + parentId + ")");
                latch.countDown();
            }
            @Override
            public void onError(String parentId, Bundle options) {
                error.set("onError(" + parentId + ", " + options + ")");
                latch.countDown();
            }
        };
        if (options != null) {
            browser.subscribe(parentId, options, cb);
        } else {
            browser.subscribe(parentId, cb);
        }
        boolean onTime = latch.await(10, TimeUnit.SECONDS);
        browser.unsubscribe(parentId, cb);
        assertTrue("Timed out waiting for children of '" + parentId + "'", onTime);
        assertNull("Browse callback reported an error: " + error.get(), error.get());
        return result.get();
    }

    @Test
    public void legacyClient_defaultSubscribe_seesFullBrowseTree() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        MediaBrowserCompat browser = connect(ctx);
        try {
            String rootId = browser.getRoot();
            assertNotNull("getRoot() returned null", rootId);

            // Real head units subscribe with no EXTRA_PAGE/EXTRA_PAGE_SIZE at
            // all for the first level — this is the page=0, pageSize=MAX_VALUE
            // path documented for the desktop head unit and most real cars.
            List<MediaBrowserCompat.MediaItem> topLevel = subscribe(browser, rootId, null);
            assertNotNull("root: onChildrenLoaded delivered null (surfaces as 'No items')", topLevel);
            assertEquals("root should expose exactly Favorites + All Stations", 2, topLevel.size());

            String allId = null;
            String favId = null;
            for (MediaBrowserCompat.MediaItem mi : topLevel) {
                if ("all".equals(mi.getMediaId())) allId = mi.getMediaId();
                if ("favorites".equals(mi.getMediaId())) favId = mi.getMediaId();
            }
            assertNotNull("'all' folder missing from root", allId);
            assertNotNull("'favorites' folder missing from root", favId);

            List<MediaBrowserCompat.MediaItem> stations = subscribe(browser, allId, null);
            assertNotNull("All Stations: onChildrenLoaded delivered null", stations);
            assertFalse("All Stations came back empty ('No items' bug)", stations.isEmpty());
            for (MediaBrowserCompat.MediaItem mi : stations) {
                assertTrue("station '" + mi.getMediaId() + "' not flagged playable",
                        mi.isPlayable());
            }

            // Favorites falls back to the visible list when empty (by design) —
            // must never come back null/empty on a fresh install.
            List<MediaBrowserCompat.MediaItem> favorites = subscribe(browser, favId, null);
            assertNotNull("Favorites: onChildrenLoaded delivered null", favorites);
            assertFalse("Favorites came back empty", favorites.isEmpty());
        } finally {
            browser.disconnect();
        }
    }

    @Test
    public void legacyClient_smallPageSize_paginatesWithoutLosingOrDuplicatingItems() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        MediaBrowserCompat browser = connect(ctx);
        try {
            String rootId = browser.getRoot();
            List<MediaBrowserCompat.MediaItem> topLevel = subscribe(browser, rootId, null);
            String allId = null;
            for (MediaBrowserCompat.MediaItem mi : topLevel) {
                if ("all".equals(mi.getMediaId())) allId = mi.getMediaId();
            }
            assertNotNull(allId);

            List<MediaBrowserCompat.MediaItem> full = subscribe(browser, allId, null);
            int total = full.size();
            assertTrue("Need at least a few stations to test pagination", total > 4);

            int pageSize = 4;
            java.util.Set<String> collected = new java.util.HashSet<>();
            for (int page = 0; page * pageSize < total; page++) {
                Bundle opts = new Bundle();
                opts.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
                opts.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);
                List<MediaBrowserCompat.MediaItem> pageItems = subscribe(browser, allId, opts);
                assertNotNull("page " + page + " (size " + pageSize + ") delivered null", pageItems);
                assertTrue("page " + page + " returned more than pageSize items: " + pageItems.size(),
                        pageItems.size() <= pageSize);
                for (MediaBrowserCompat.MediaItem mi : pageItems) {
                    collected.add(mi.getMediaId());
                }
            }
            assertEquals("Paginated browse lost or duplicated items vs. the unpaged list",
                    total, collected.size());
        } finally {
            browser.disconnect();
        }
    }

    @Test
    public void legacyClient_zeroPageSize_doesNotErrorOrHang() throws Exception {
        // README's suspected root cause: some head unit sends a page request
        // with pageSize <= 0. This must not throw inside onGetChildren and
        // must not silently hang the subscription.
        Context ctx = ApplicationProvider.getApplicationContext();
        MediaBrowserCompat browser = connect(ctx);
        try {
            String rootId = browser.getRoot();
            List<MediaBrowserCompat.MediaItem> topLevel = subscribe(browser, rootId, null);
            String allId = null;
            for (MediaBrowserCompat.MediaItem mi : topLevel) {
                if ("all".equals(mi.getMediaId())) allId = mi.getMediaId();
            }
            assertNotNull(allId);

            Bundle opts = new Bundle();
            opts.putInt(MediaBrowserCompat.EXTRA_PAGE, 0);
            opts.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, 0);
            // Must complete (not hang) and must not deliver an onError.
            subscribe(browser, allId, opts);
        } finally {
            browser.disconnect();
        }
    }
}
