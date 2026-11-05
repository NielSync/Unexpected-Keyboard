package juloo.keyboard2;

import org.dslul.openboard.inputmethod.latin.common.InputPointers;

/** Record the trajectory of a pointer and decide when it becomes a word
    gesture. Word gestures start when the pointer travels far enough while
    being over an other key than the one it started on. This leaves the
    swipes performed inside a key to [Pointers]. */
public final class WordGesture
{
  public enum State
  {
    /** The gesture might still be a swipe or a slide performed inside the
        initial key. */
    Pending,
    /** Word gesture recognized, the pointer is recorded. */
    Started,
    /** Not a word gesture (the pointer went up before starting). */
    Aborted
  }

  private final int _pointer_id;
  private final int _start_x;
  private final int _start_y;
  private final float _key_width;
  private final KeyboardData.Key _start_key;

  private State _state = State.Pending;
  /** Set to [true] once the keyboard view reacted to the gesture starting. */
  private boolean _start_notified = false;
  private float _path_length = 0.f;
  private float _last_x;
  private float _last_y;
  private int _last_time;
  private final InputPointers _pointers = new InputPointers(128);

  public WordGesture(int pointer_id, float x, float y, int time,
      KeyboardData.Key start_key, float key_width)
  {
    _pointer_id = pointer_id;
    _start_x = (int)x;
    _start_y = (int)y;
    _last_x = x;
    _last_y = y;
    _last_time = time;
    _start_key = start_key;
    _key_width = key_width;
    _pointers.addPointer(_start_x, _start_y, pointer_id, time);
  }

  public int pointer_id() { return _pointer_id; }
  public boolean started() { return _state == State.Started; }
  public boolean aborted() { return _state == State.Aborted; }
  public boolean start_notified() { return _start_notified; }
  public void mark_start_notified() { _start_notified = true; }

  /** Record a movement of the pointer. [key] is the key under the pointer,
      might be [null]. Returns the new state. */
  public State feed(float x, float y, int time, KeyboardData.Key key)
  {
    if (_state == State.Aborted)
      return _state;
    float dx = x - _last_x;
    float dy = y - _last_y;
    int dt = time - _last_time;
    _path_length += (float)Math.hypot(dx, dy);
    _last_x = x;
    _last_y = y;
    _last_time = time;
    if (_state == State.Pending)
    {
      // Start the gesture when the pointer moved far enough while being on an
      // other key than the one it started on. The extra distance keeps the
      // swipes reaching into the corners of a neighbouring key working.
      if (key != null && key != _start_key
          && _path_length > 1.6f * _key_width)
      {
        _state = State.Started;
        _pointers.addPointer((int)x, (int)y, _pointer_id, time);
      }
    }
    else if (Math.abs(dx) + Math.abs(dy) >= 2 || dt >= 10)
    {
      // Don't record points too close to each other, the decoder normalizes
      // the sampling rate by itself.
      _pointers.addPointer((int)x, (int)y, _pointer_id, time);
    }
    return _state;
  }

  /** The recorded stroke, ready to be decoded. */
  public InputPointers pointers() { return _pointers; }
}
