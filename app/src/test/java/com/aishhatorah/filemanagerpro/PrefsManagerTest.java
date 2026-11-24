package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class PrefsManagerTest {

    private static Context createInMemoryPrefsContext() {
        final InMemorySharedPreferences prefs = new InMemorySharedPreferences();
        return new ContextWrapper(null) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                return prefs;
            }
        };
    }

    @Test
    public void folderSortOverridesGlobalSortPerFolderOnly() {
        Context ctx = createInMemoryPrefsContext();
        PrefsManager.setSortMode(ctx, PrefsManager.SORT_NAME);
        PrefsManager.setSortAscending(ctx, true);

        assertEquals(PrefsManager.SORT_NAME, PrefsManager.getEffectiveSortMode(ctx, "/sdcard/Music"));
        assertTrue(PrefsManager.isEffectiveSortAscending(ctx, "/sdcard/Music"));

        PrefsManager.setFolderSortMode(ctx, "/sdcard/Music", PrefsManager.SORT_DATE);
        PrefsManager.setFolderSortAscending(ctx, "/sdcard/Music", false);

        assertEquals(PrefsManager.SORT_DATE, PrefsManager.getEffectiveSortMode(ctx, "/sdcard/Music"));
        assertFalse(PrefsManager.isEffectiveSortAscending(ctx, "/sdcard/Music"));

        // Different folder still inherits global default
        assertEquals(PrefsManager.SORT_NAME, PrefsManager.getEffectiveSortMode(ctx, "/sdcard/Documents"));
        assertTrue(PrefsManager.isEffectiveSortAscending(ctx, "/sdcard/Documents"));

        // Empty scope mutates global sort
        PrefsManager.setFolderSortMode(ctx, "", PrefsManager.SORT_SIZE);
        assertEquals(PrefsManager.SORT_SIZE, PrefsManager.getSortMode(ctx));
        assertEquals(PrefsManager.SORT_SIZE, PrefsManager.getEffectiveSortMode(ctx, "/sdcard/Documents"));
        assertEquals(PrefsManager.SORT_DATE, PrefsManager.getEffectiveSortMode(ctx, "/sdcard/Music"));
    }

    @Test
    public void granularGestureFlagsSyncAndToggleIndependently() {
        Context ctx = createInMemoryPrefsContext();
        assertTrue(PrefsManager.isAutoGestureMode(ctx));
        assertTrue(PrefsManager.isGesturePdfSwipe(ctx));
        assertTrue(PrefsManager.isGestureVideoHold2x(ctx));

        PrefsManager.setAutoGestureMode(ctx, false);
        assertFalse(PrefsManager.isAutoGestureMode(ctx));
        assertFalse(PrefsManager.isGestureImageTapNav(ctx));
        assertFalse(PrefsManager.isGestureImageSwipeNav(ctx));
        assertFalse(PrefsManager.isGesturePinchZoom(ctx));
        assertFalse(PrefsManager.isGestureVideoSeekSwipe(ctx));
        assertFalse(PrefsManager.isGestureVideoVolumeBrightness(ctx));
        assertFalse(PrefsManager.isGestureVideoHold2x(ctx));
        assertFalse(PrefsManager.isGesturePdfSwipe(ctx));

        PrefsManager.setGesturePdfSwipe(ctx, true);
        assertTrue(PrefsManager.isGesturePdfSwipe(ctx));
        assertTrue(PrefsManager.isAutoGestureMode(ctx));
        assertFalse(PrefsManager.isGestureVideoHold2x(ctx));
    }

    @Test
    public void miniBarReturnModeAndFolderThumbnailDefaultsAreAccurate() {
        Context ctx = createInMemoryPrefsContext();
        assertTrue(PrefsManager.isMiniBarReturnToSongFolder(ctx));
        PrefsManager.setMiniBarReturnToSongFolder(ctx, false);
        assertFalse(PrefsManager.isMiniBarReturnToSongFolder(ctx));

        assertFalse(PrefsManager.isShowFolderThumbnails(ctx));
        PrefsManager.setShowFolderThumbnails(ctx, true);
        assertTrue(PrefsManager.isShowFolderThumbnails(ctx));
    }

    @Test
    public void versionComparisonRecognizes038Above0371() {
        assertTrue(UpdateChecker.compareSemver("0.3.8", "0.3.7.1") > 0);
        assertEquals("0.3.8", UpdateChecker.formatOfficialVer("0.3.8"));
    }

    private static final class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> map = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(map);
        }

        @Override
        public String getString(String key, String defValue) {
            Object v = map.get(key);
            return v instanceof String ? (String) v : defValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object v = map.get(key);
            return v instanceof Set ? new HashSet<>((Set<String>) v) : defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object v = map.get(key);
            return v instanceof Integer ? (Integer) v : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object v = map.get(key);
            return v instanceof Long ? (Long) v : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object v = map.get(key);
            return v instanceof Float ? (Float) v : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object v = map.get(key);
            return v instanceof Boolean ? (Boolean) v : defValue;
        }

        @Override
        public boolean contains(String key) {
            return map.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new Editor() {
                private final Map<String, Object> pending = new HashMap<>();
                private final Set<String> removals = new HashSet<>();
                private boolean clearAll = false;

                @Override public Editor putString(String key, String value) {
                    if (value == null) removals.add(key); else pending.put(key, value);
                    return this;
                }
                @Override public Editor putStringSet(String key, Set<String> values) {
                    if (values == null) removals.add(key); else pending.put(key, new HashSet<>(values));
                    return this;
                }
                @Override public Editor putInt(String key, int value) { pending.put(key, value); return this; }
                @Override public Editor putLong(String key, long value) { pending.put(key, value); return this; }
                @Override public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
                @Override public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
                @Override public Editor remove(String key) { removals.add(key); pending.remove(key); return this; }
                @Override public Editor clear() { clearAll = true; pending.clear(); removals.clear(); return this; }
                @Override public boolean commit() { apply(); return true; }
                @Override public void apply() {
                    if (clearAll) map.clear();
                    for (String k : removals) map.remove(k);
                    map.putAll(pending);
                }
            };
        }

        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    }
}
