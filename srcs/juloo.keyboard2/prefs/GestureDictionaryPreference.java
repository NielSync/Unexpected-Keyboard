package juloo.keyboard2.prefs;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import juloo.keyboard2.GestureTyping;
import juloo.keyboard2.R;

/** Opens a file picker to select the binary dictionary used by word gesture
    typing. The picked file is copied into the private storage by
    [SettingsActivity]. */
public class GestureDictionaryPreference extends Preference
{
  public GestureDictionaryPreference(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    update_summary();
  }

  @Override
  protected void onClick()
  {
    ((juloo.keyboard2.SettingsActivity)getContext()).pick_gesture_dictionary();
  }

  public void update_summary()
  {
    String name = GestureTyping.instance().dictionary_display_name();
    if (name != null)
      setSummary(getContext().getString(R.string.gesture_dictionary_loaded, name));
    else
      setSummary(R.string.pref_gesture_dictionary_none);
  }
}
