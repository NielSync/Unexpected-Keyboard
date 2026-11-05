package juloo.keyboard2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class SettingsActivity extends PreferenceActivity
{
  private static final int REQ_EXPORT = 1;
  private static final int REQ_IMPORT = 2;
  private static final int REQ_GESTURE_DICT = 3;

  /** The settings screen, saved before a search replaces it. */
  PreferenceScreen _root_screen;
  EditText _search_input;
  /** Guards against re-entering [run_search] when the results screen is
      set while the search text is being modified. */
  boolean _updating_screen = false;

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
    _root_screen = getPreferenceScreen();

    bind_backup_preferences();
    add_search_bar();

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
  public void onBackPressed()
  {
    // Leave the search results before closing the settings.
    if (_search_input != null && _search_input.length() > 0)
    {
      _search_input.setText("");
      return;
    }
    super.onBackPressed();
  }

  /** Search bar -----------------------------------------------------------------
   *
   * An edit text is inserted above the preferences list. While it contains
   * text, the list is replaced by the search results. Each result navigates
   * back to the screen containing the setting and scrolls to it. */

  private void add_search_bar()
  {
    _search_input = new EditText(this);
    _search_input.setHint(R.string.search_settings_hint);
    _search_input.setSingleLine(true);
    int pad = (int)(8.f * getResources().getDisplayMetrics().density);
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(pad, pad, pad, 0);
    box.addView(_search_input, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT));
    ViewGroup list_parent = (ViewGroup)getListView().getParent();
    list_parent.addView(box, 0);
    _search_input.addTextChangedListener(new TextWatcher()
      {
        public void onTextChanged(CharSequence s, int a, int b, int c)
        {
          run_search(s.toString());
        }
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void afterTextChanged(Editable s) {}
      });
  }

  /** One indexed setting: the preference, the keys of the screens containing
      it and the breadcrumb to display in the search results. */
  private static final class SearchEntry
  {
    final Preference pref;
    final List<String> screen_keys;
    final String breadcrumb;

    SearchEntry(Preference pref_, List<String> screen_keys_, String breadcrumb_)
    {
      pref = pref_;
      screen_keys = screen_keys_;
      breadcrumb = breadcrumb_;
    }
  }

  private static void collect_entries(PreferenceScreen screen,
      List<String> path, String breadcrumb, List<SearchEntry> out)
  {
    for (int i = 0; i < screen.getPreferenceCount(); i++)
    {
      Preference p = screen.getPreference(i);
      if (p instanceof PreferenceScreen)
      {
        PreferenceScreen sub = (PreferenceScreen)p;
        // Nested screens don't have keys in the settings file, give them one
        // so that they can be found again while navigating.
        if (p.getKey() == null)
          p.setKey("screen_" + Integer.toHexString(System.identityHashCode(p)));
        String crumb = breadcrumb.equals("") ? title_of(p) :
          breadcrumb + " > " + title_of(p);
        out.add(new SearchEntry(p, new ArrayList<String>(path), breadcrumb));
        List<String> sub_path = new ArrayList<String>(path);
        sub_path.add(p.getKey());
        collect_entries(sub, sub_path, crumb, out);
      }
      else
      {
        out.add(new SearchEntry(p, path, breadcrumb));
      }
    }
  }

  private static String title_of(Preference p)
  {
    CharSequence t = p.getTitle();
    return (t != null) ? t.toString() : "";
  }

  private void run_search(String query)
  {
    if (_updating_screen)
      return;
    query = query.trim().toLowerCase();
    if (query.equals(""))
    {
      if (getPreferenceScreen() != _root_screen)
      {
        _updating_screen = true;
        setPreferenceScreen(_root_screen);
        _updating_screen = false;
      }
      return;
    }
    List<SearchEntry> entries = new ArrayList<SearchEntry>();
    collect_entries(_root_screen, new ArrayList<String>(), "", entries);
    PreferenceScreen results = getPreferenceManager().createPreferenceScreen(this);
    int count = 0;
    for (final SearchEntry entry : entries)
    {
      String title = title_of(entry.pref);
      CharSequence summary_cs = entry.pref.getSummary();
      String summary = (summary_cs != null) ? summary_cs.toString() : "";
      if (!title.toLowerCase().contains(query)
          && !summary.toLowerCase().contains(query)
          && !entry.breadcrumb.toLowerCase().contains(query))
        continue;
      Preference result = new Preference(this);
      result.setTitle(title);
      String text = entry.breadcrumb.equals("") ? summary :
        entry.breadcrumb + " — " + summary;
      result.setSummary(text);
      result.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
          public boolean onPreferenceClick(Preference _p)
          {
            navigate_to(entry.screen_keys, entry.pref);
            return true;
          }
        });
      results.addPreference(result);
      count++;
    }
    if (count == 0)
    {
      Preference none = new Preference(this);
      none.setTitle(R.string.search_no_results);
      none.setEnabled(false);
      results.addPreference(none);
    }
    _updating_screen = true;
    setPreferenceScreen(results);
    _updating_screen = false;
  }

  /** Open the screen containing [target] and scroll its list to it. */
  private void navigate_to(final List<String> screen_keys, final Preference target)
  {
    _search_input.setText(""); // Leaves the search results
    PreferenceScreen screen = _root_screen;
    if (!screen_keys.isEmpty())
    {
      Preference next = _root_screen.findPreference(screen_keys.get(0));
      if (!(next instanceof PreferenceScreen))
        return;
      screen = (PreferenceScreen)next;
      // Nested screens are shown in a dialog when clicked. The dialog
      // mechanism is not exposed through the public API, the same private
      // method as a click has been used since API 1 is called here. When it
      // fails the main list is scrolled to the screen entry instead.
      if (!open_preference_screen(screen))
      {
        scroll_list_to(getListView(), _root_screen, screen);
        return;
      }
    }
    // The dialog list takes a moment to be bound before it can be scrolled.
    final PreferenceScreen target_screen = screen;
    getListView().postDelayed(new Runnable()
      {
        public void run()
        {
          try
          {
            android.widget.ListView lv = (target_screen == _root_screen) ? getListView() :
              (android.widget.ListView)target_screen.getDialog()
                .findViewById(android.R.id.list);
            if (lv != null)
              scroll_list_to(lv, target_screen, target);
          }
          catch (Exception _e) {} // The dialog might be gone already
        }
      }, 200);
  }

  private boolean open_preference_screen(PreferenceScreen screen)
  {
    try
    {
      java.lang.reflect.Method m =
        PreferenceScreen.class.getDeclaredMethod("showDialog", Bundle.class);
      m.setAccessible(true);
      m.invoke(screen, (Bundle)null);
      return true;
    }
    catch (Exception e)
    {
      return false;
    }
  }

  private void scroll_list_to(android.widget.ListView lv,
      PreferenceScreen screen, Preference target)
  {
    for (int i = 0; i < screen.getPreferenceCount(); i++)
      if (screen.getPreference(i) == target)
      {
        lv.setSelection(i);
        return;
      }
  }

  /** Backup and restore ---------------------------------------------------------
   *
   * The settings are exported to and imported from a JSON file through the
   * storage access framework, which doesn't require any permission. */

  private void bind_backup_preferences()
  {
    findPreference("backup_settings").setOnPreferenceClickListener(
        new Preference.OnPreferenceClickListener()
        {
          public boolean onPreferenceClick(Preference _p)
          {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "unexpected-keyboard-settings.json");
            startActivityForResult(i, REQ_EXPORT);
            return true;
          }
        });
    findPreference("restore_settings").setOnPreferenceClickListener(
        new Preference.OnPreferenceClickListener()
        {
          public boolean onPreferenceClick(Preference _p)
          {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_IMPORT);
            return true;
          }
        });
  }

  /** Open the file picker to select a gesture dictionary. Called from
      [GestureDictionaryPreference]. */
  public void pick_gesture_dictionary()
  {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("*/*");
    startActivityForResult(i, REQ_GESTURE_DICT);
  }

  @Override
  protected void onActivityResult(int req, int res, Intent data)
  {
    if (res != RESULT_OK || data == null || data.getData() == null)
      return;
    switch (req)
    {
      case REQ_EXPORT: export_settings(data.getData()); break;
      case REQ_IMPORT: import_settings(data.getData()); break;
      case REQ_GESTURE_DICT: load_gesture_dictionary(data.getData()); break;
    }
  }

  private void toast(int text)
  {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
  }

  private void export_settings(Uri uri)
  {
    try
    {
      Map<String, ?> all = getPreferenceManager().getSharedPreferences().getAll();
      JSONObject groups = new JSONObject();
      for (Map.Entry<String, ?> e : all.entrySet())
      {
        Object v = e.getValue();
        String group;
        if (v instanceof Boolean) group = "boolean";
        else if (v instanceof Integer) group = "integer";
        else if (v instanceof Float) group = "float";
        else if (v instanceof Long) group = "long";
        else if (v instanceof String) group = "string";
        else if (v instanceof Set) group = "string_set";
        else continue;
        if (!groups.has(group))
          groups.put(group, new JSONObject());
        if (v instanceof Set)
          groups.put(group, groups.getJSONObject(group).put(e.getKey(),
                new JSONArray((Set<?>)v)));
        else
          groups.put(group, groups.getJSONObject(group).put(e.getKey(), v));
      }
      JSONObject root = new JSONObject();
      root.put("format", 1);
      root.put("prefs", groups);
      OutputStream os = getContentResolver().openOutputStream(uri);
      if (os == null)
        throw new Exception("No output stream");
      os.write(root.toString(2).getBytes("UTF-8"));
      os.close();
      toast(R.string.backup_success);
    }
    catch (Exception e)
    {
      toast(R.string.backup_failure);
    }
  }

  private void import_settings(Uri uri)
  {
    try
    {
      SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
      // The config version is not imported: it describes the version of the
      // local settings, migrations already ran on them.
      int config_version = prefs.getInt("version", 0);
      InputStream in = getContentResolver().openInputStream(uri);
      BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
      StringBuilder b = new StringBuilder();
      String line;
      while ((line = r.readLine()) != null)
        b.append(line);
      r.close();
      JSONObject groups = new JSONObject(b.toString()).getJSONObject("prefs");
      SharedPreferences.Editor e = prefs.edit().clear();
      e.putInt("version", config_version);
      JSONObject g;
      if ((g = groups.optJSONObject("boolean")) != null)
      {
        Iterator<String> it = g.keys();
        while (it.hasNext()) { String k = it.next(); e.putBoolean(k, g.getBoolean(k)); }
      }
      if ((g = groups.optJSONObject("integer")) != null)
      {
        Iterator<String> it = g.keys();
        while (it.hasNext()) { String k = it.next(); e.putInt(k, g.getInt(k)); }
      }
      if ((g = groups.optJSONObject("float")) != null)
      {
        Iterator<String> it = g.keys();
        while (it.hasNext()) { String k = it.next(); e.putFloat(k, (float)g.getDouble(k)); }
      }
      if ((g = groups.optJSONObject("long")) != null)
      {
        Iterator<String> it = g.keys();
        while (it.hasNext()) { String k = it.next(); e.putLong(k, g.getLong(k)); }
      }
      if ((g = groups.optJSONObject("string")) != null)
      {
        Iterator<String> it = g.keys();
        while (it.hasNext()) { String k = it.next(); e.putString(k, g.getString(k)); }
      }
      if ((g = groups.optJSONObject("string_set")) != null)
      {
        Iterator<String> it = g.keys();
        while (it.hasNext())
        {
          String k = it.next();
          JSONArray a = g.getJSONArray(k);
          Set<String> set = new HashSet<String>();
          for (int i = 0; i < a.length(); i++)
            set.add(a.getString(i));
          e.putStringSet(k, set);
        }
      }
      e.apply();
      toast(R.string.restore_success);
    }
    catch (Exception err)
    {
      toast(R.string.restore_failure);
    }
  }

  private void load_gesture_dictionary(Uri uri)
  {
    String name = null;
    Cursor c = getContentResolver().query(uri, null, null, null, null);
    if (c != null)
    {
      int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
      if (c.moveToFirst() && idx >= 0)
        name = c.getString(idx);
      c.close();
    }
    if (!GestureTyping.instance().lib_available())
    {
      toast(R.string.gesture_library_missing);
      return;
    }
    if (GestureTyping.instance().set_dictionary(this, uri, name))
    {
      if (Config.globalConfig() != null)
        Config.globalPrefs().edit()
          .putString("gesture_dictionary_name", name).apply();
      toast(R.string.restore_success);
    }
    else
    {
      toast(R.string.gesture_dictionary_failed);
    }
    Preference p = findPreference("gesture_dictionary");
    if (p instanceof juloo.keyboard2.prefs.GestureDictionaryPreference)
      ((juloo.keyboard2.prefs.GestureDictionaryPreference)p).update_summary();
  }
}
