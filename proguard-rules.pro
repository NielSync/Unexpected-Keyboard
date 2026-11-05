-keep public class juloo.cdict.* {
  public protected private *;
}
# Keep the vendored gesture typing classes: their names and native method
# declarations are matched against libjni_latinimegoogle.so through JNI.
-keep class com.android.inputmethod.** { *; }
-keep class org.dslul.openboard.inputmethod.** { *; }
