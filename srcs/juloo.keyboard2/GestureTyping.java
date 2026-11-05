package juloo.keyboard2;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.BinaryDictionary;
import com.android.inputmethod.latin.DicTraverseSession;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.dslul.openboard.inputmethod.latin.common.InputPointers;
import org.dslul.openboard.inputmethod.latin.utils.JniUtils;

/** Word gesture typing: loads the swype library and the gesture dictionary
    and decodes gesture strokes into suggested words.

    The native library is bundled with the application. The dictionary is a
    binary dictionary in the format used by OpenBoard, selected by the user
    and copied into the private storage of the application. */
public final class GestureTyping
{
  private static final String TAG = GestureTyping.class.getSimpleName();
  private static final String DICT_FILE_NAME = "gesture_dict.bin";
  private static GestureTyping _instance;

  public static GestureTyping instance()
  {
    if (_instance == null)
      _instance = new GestureTyping();
    return _instance;
  }

  private GestureTyping() {}

  private BinaryDictionary _dict = null;
  private ProximityInfo _proximity = null;
  private String _dict_display_name = null;

  /** Whether the native gesture library could be loaded. */
  public boolean lib_available()
  {
    return JniUtils.sHaveGestureLib;
  }

  /** Whether decoding can happen: the library, a dictionary and the
      description of the current layout are all loaded. */
  public boolean is_ready()
  {
    return lib_available() && _dict != null && _dict.isValidDictionary()
      && _proximity != null;
  }

  /** Name of the loaded dictionary as chosen by the user, [null] if none. */
  public String dictionary_display_name()
  {
    return _dict_display_name;
  }

  /** Copy the dictionary pointed to by [uri] into the private storage of the
      application and load it. Returns [true] on success. */
  public boolean set_dictionary(Context ctx, Uri uri, String display_name)
  {
    File out = new File(ctx.getFilesDir(), DICT_FILE_NAME);
    InputStream in = null;
    OutputStream os = null;
    try
    {
      in = ctx.getContentResolver().openInputStream(uri);
      os = new FileOutputStream(out);
      byte[] buf = new byte[8192];
      int len;
      while ((len = in.read(buf)) > 0)
        os.write(buf, 0, len);
      os.close();
      os = null;
    }
    catch (Exception e)
    {
      Log.e(TAG, "Could not copy the gesture dictionary", e);
      if (os != null)
        try { os.close(); } catch (IOException _e) {}
      return false;
    }
    finally
    {
      if (in != null)
        try { in.close(); } catch (IOException _e) {}
    }
    close_dictionary();
    _dict = new BinaryDictionary(out.getAbsolutePath(), 0 /* offset */,
        out.length(), false /* useFullEditDistance */, null /* locale */);
    if (!_dict.isValidDictionary())
    {
      Log.e(TAG, "The gesture dictionary could not be opened");
      close_dictionary();
      return false;
    }
    _dict_display_name = display_name;
    return true;
  }

  public void close_dictionary()
  {
    if (_dict != null)
    {
      _dict.close();
      _dict = null;
    }
    _dict_display_name = null;
  }

  /** Reopen the dictionary that was copied to the private storage before,
      if any. Called when the keyboard service starts. */
  public void restore(Context ctx)
  {
    if (!lib_available() || _dict != null)
      return;
    File f = new File(ctx.getFilesDir(), DICT_FILE_NAME);
    if (!f.exists())
      return;
    try
    {
      _dict = new BinaryDictionary(f.getAbsolutePath(), 0 /* offset */,
          f.length(), false /* useFullEditDistance */, null /* locale */);
      if (!_dict.isValidDictionary())
      {
        Log.e(TAG, "The saved gesture dictionary could not be opened");
        close_dictionary();
        f.delete();
        return;
      }
      if (Config.globalConfig() != null)
        _dict_display_name =
          Config.globalPrefs().getString("gesture_dictionary_name", null);
    }
    catch (Throwable e)
    {
      Log.e(TAG, "Restoring the gesture dictionary failed", e);
      close_dictionary();
    }
  }

  /** Install the description of the current keyboard layout. Called whenever
      the layout or the dimensions of the keyboard view change. */
  public void set_proximity_info(ProximityInfo info)
  {
    if (_proximity != null)
      _proximity.release();
    _proximity = info;
  }

  /** Decode the stroke recorded in [pointers] into suggested words, best
      first. */
  public String[] decode(InputPointers pointers)
  {
    if (!is_ready())
      return new String[0];
    return _dict.getSuggestionsFromGesture(pointers,
        _proximity.getNativeProximityInfo());
  }
}
