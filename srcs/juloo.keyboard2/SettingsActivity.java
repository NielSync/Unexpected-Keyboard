package juloo.keyboard2;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class SettingsActivity extends PreferenceActivity
{
  /** Sync each preference change to the protected storage right away, so
      the keyboard picks up the new values immediately: it reads and listens
      on the protected storage preferences, which are otherwise only synced
      in [onStop]. */
  final SharedPreferences.OnSharedPreferenceChangeListener _prefs_sync =
    new SharedPreferences.OnSharedPreferenceChangeListener()
    {
      @Override
      public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
      {
        DirectBootAwarePreferences.copy_preferences_to_protected_storage(
            SettingsActivity.this, prefs);
      }
    };

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    // The preferences can't be read when in direct-boot mode. Avoid crashing
    // and don't allow changing the settings.
    // Run the config migration on this prefs as it might be different from the
    // one used by the keyboard, which have been migrated.
    try
    {
      Config.migrate(getPreferenceManager().getSharedPreferences());
    }
    catch (Exception _e) { fallbackEncrypted(); return; }
    addPreferencesFromResource(R.xml.settings);
    getPreferenceManager().getSharedPreferences()
      .registerOnSharedPreferenceChangeListener(_prefs_sync);

    boolean foldableDevice = FoldStateTracker.isFoldableDevice(this);
    findPreference("margin_bottom_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("margin_bottom_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_landscape_unfolded").setEnabled(foldableDevice);
  }

  void fallbackEncrypted()
  {
    // Can't communicate with the user here.
    finish();
  }

  protected void onStop()
  {
    DirectBootAwarePreferences
      .copy_preferences_to_protected_storage(this,
          getPreferenceManager().getSharedPreferences());
    super.onStop();
  }

  @Override
  public void onDestroy()
  {
    getPreferenceManager().getSharedPreferences()
      .unregisterOnSharedPreferenceChangeListener(_prefs_sync);
    super.onDestroy();
  }
}
