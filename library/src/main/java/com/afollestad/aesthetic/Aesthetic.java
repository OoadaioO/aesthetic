package com.afollestad.aesthetic;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.CheckResult;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.StyleRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;

import com.f2prateek.rx.preferences.RxSharedPreferences;

import rx.Observable;
import rx.subscriptions.CompositeSubscription;

import static com.afollestad.aesthetic.Rx.distinctToMainThread;
import static com.afollestad.aesthetic.Rx.onErrorLogAndRethrow;
import static com.afollestad.aesthetic.Util.isColorLight;
import static com.afollestad.aesthetic.Util.resolveColor;
import static com.afollestad.aesthetic.Util.setLightStatusBarCompat;
import static com.afollestad.aesthetic.Util.setNavBarColorCompat;

/** @author Aidan Follestad (afollestad) */
public class Aesthetic {

  private static final String PREFS_NAME = "[aesthetic-prefs]";
  private static final String KEY_FIRST_TIME = "first_time";
  private static final String KEY_ACTIVITY_THEME = "activity_theme";
  private static final String KEY_IS_DARK = "is_dark";
  private static final String KEY_PRIMARY_COLOR = "primary_color";
  private static final String KEY_ACCENT_COLOR = "accent_color";
  private static final String KEY_PRIMARY_TEXT_COLOR = "primary_text";
  private static final String KEY_SECONDARY_TEXT_COLOR = "secondary_text";
  private static final String KEY_PRIMARY_TEXT_INVERSE_COLOR = "primary_text_inverse";
  private static final String KEY_SECONDARY_TEXT_INVERSE_COLOR = "secondary_text_inverse";
  private static final String KEY_WINDOW_BG_COLOR = "window_bg_color";
  private static final String KEY_STATUS_BAR_COLOR = "status_bar_color";
  private static final String KEY_NAV_BAR_COLOR = "nav_bar_color";
  private static final String KEY_LIGHT_STATUS_MODE = "light_status_mode";

  @SuppressLint("StaticFieldLeak")
  private static Aesthetic instance;

  private AppCompatActivity context;
  private SharedPreferences prefs;
  private SharedPreferences.Editor editor;
  private RxSharedPreferences rxPrefs;
  private CompositeSubscription subs;
  private int lastActivityTheme;

  @SuppressLint("CommitPrefEdits")
  private Aesthetic(AppCompatActivity context) {
    this.context = context;
    prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    editor = prefs.edit();
    rxPrefs = RxSharedPreferences.create(prefs);
  }

  /** Should be called before super.onCreate() in each Activity. */
  @NonNull
  public static Aesthetic attach(@NonNull AppCompatActivity activity) {
    if (instance == null) {
      instance = new Aesthetic(activity);
    }
    LayoutInflater li = activity.getLayoutInflater();
    Util.setInflaterFactory(li, activity);
    instance.lastActivityTheme = instance.prefs.getInt(KEY_ACTIVITY_THEME, 0);
    if (instance.lastActivityTheme != 0) {
      activity.setTheme(instance.lastActivityTheme);
    }
    return instance;
  }

  @NonNull
  @CheckResult
  public static Aesthetic get() {
    if (instance == null) {
      throw new IllegalStateException("Not attached!");
    }
    return instance;
  }

  /** Should be called in onPause() of each Activity. */
  public static void pause() {
    if (instance == null) {
      return;
    }
    if (instance.subs != null) {
      instance.subs.unsubscribe();
    }
  }

  /** Should be called in onResume() of each Activity. */
  public static void resume(@NonNull AppCompatActivity activity) {
    if (instance == null) {
      return;
    }
    instance.context = activity;
    if (instance.subs != null) {
      instance.subs.unsubscribe();
    }
    instance.subs = new CompositeSubscription();
    instance.subs.add(
        instance
            .activityTheme()
            .compose(distinctToMainThread())
            .subscribe(
                themeId -> {
                  if (instance.lastActivityTheme == themeId) {
                    return;
                  }
                  instance.lastActivityTheme = themeId;
                  instance.context.recreate();
                }));
    instance.subs.add(
        instance
            .statusBarColor()
            .compose(distinctToMainThread())
            .subscribe(color -> instance.invalidateStatusBar(), onErrorLogAndRethrow()));
    instance.subs.add(
        instance
            .navBarColor()
            .compose(distinctToMainThread())
            .subscribe(
                color -> setNavBarColorCompat(instance.context, color), onErrorLogAndRethrow()));
    instance.subs.add(
        instance
            .windowBgColor()
            .compose(distinctToMainThread())
            .subscribe(
                color ->
                    instance.context.getWindow().setBackgroundDrawable(new ColorDrawable(color)),
                onErrorLogAndRethrow()));
    instance.subs.add(
        instance
            .lightStatusBarMode()
            .compose(distinctToMainThread())
            .subscribe(color -> instance.invalidateStatusBar(), onErrorLogAndRethrow()));
  }

  /** Should be called when your application is exiting. */
  public static void destroy() {
    if (instance != null) {
      if (instance.subs != null) {
        instance.subs.unsubscribe();
        instance.subs = null;
      }
      instance.context = null;
      instance.prefs = null;
      instance.editor = null;
      instance.rxPrefs = null;
      instance = null;
    }
  }

  public static boolean isFirstTime() {
    boolean firstTime = instance.prefs.getBoolean(KEY_FIRST_TIME, true);
    instance.editor.putBoolean(KEY_FIRST_TIME, false).commit();
    return firstTime;
  }

  //
  /////// GETTERS AND SETTERS OF THEME PROPERTIES
  //

  private void invalidateStatusBar() {
    final int color =
        prefs.getInt(KEY_STATUS_BAR_COLOR, resolveColor(context, R.attr.colorPrimaryDark));
    Util.setStatusBarColorCompat(context, color);
    final int mode = prefs.getInt(KEY_LIGHT_STATUS_MODE, AutoSwitchMode.AUTO);
    switch (mode) {
      case AutoSwitchMode.OFF:
        setLightStatusBarCompat(context, false);
        break;
      case AutoSwitchMode.ON:
        setLightStatusBarCompat(context, true);
        break;
      default:
        setLightStatusBarCompat(context, Util.isColorLight(color));
        break;
    }
  }

  @CheckResult
  public Aesthetic activityTheme(@StyleRes int theme) {
    editor.putInt(KEY_ACTIVITY_THEME, theme);
    return this;
  }

  @CheckResult
  public Observable<Integer> activityTheme() {
    return rxPrefs
        .getInteger(KEY_ACTIVITY_THEME, 0)
        .asObservable()
        .filter(next -> next != 0 && next != lastActivityTheme);
  }

  @CheckResult
  public Aesthetic isDark(boolean isDark) {
    editor.putBoolean(KEY_IS_DARK, isDark);
    return this;
  }

  @CheckResult
  public Observable<Boolean> isDark() {
    return rxPrefs.getBoolean(KEY_IS_DARK, false).asObservable();
  }

  @CheckResult
  public Aesthetic primaryColor(@ColorInt int color) {
    // needs to be committed immediately so that for statusBarColorAuto() and other auto methods
    editor.putInt(KEY_PRIMARY_COLOR, color).commit();
    return this;
  }

  @CheckResult
  public Aesthetic primaryColorRes(@ColorRes int color) {
    return primaryColor(ContextCompat.getColor(context, color));
  }

  @CheckResult
  public Observable<Integer> primaryColor() {
    return rxPrefs
        .getInteger(KEY_PRIMARY_COLOR, resolveColor(context, R.attr.colorPrimary))
        .asObservable();
  }

  @CheckResult
  public Aesthetic accentColor(@ColorInt int color) {
    editor.putInt(KEY_ACCENT_COLOR, color);
    return this;
  }

  @CheckResult
  public Aesthetic accentColorRes(@ColorRes int color) {
    return accentColor(ContextCompat.getColor(context, color));
  }

  @CheckResult
  public Observable<Integer> accentColor() {
    return rxPrefs
        .getInteger(KEY_ACCENT_COLOR, resolveColor(context, R.attr.colorAccent))
        .asObservable();
  }

  @CheckResult
  public Aesthetic primaryTextColor(@ColorInt int color) {
    editor.putInt(KEY_PRIMARY_TEXT_COLOR, color);
    return this;
  }

  @CheckResult
  public Aesthetic primaryTextColorRes(@ColorRes int color) {
    return primaryTextColor(ContextCompat.getColor(context, color));
  }

  @CheckResult
  public Observable<Integer> primaryTextColor() {
    return rxPrefs
        .getInteger(KEY_PRIMARY_TEXT_COLOR, resolveColor(context, android.R.attr.textColorPrimary))
        .asObservable();
  }

  @CheckResult
  public Aesthetic secondaryTextColor(@ColorInt int color) {
    editor.putInt(KEY_SECONDARY_TEXT_COLOR, color);
    return this;
  }

  @CheckResult
  public Aesthetic secondaryTextColorRes(@ColorRes int color) {
    return secondaryTextColor(ContextCompat.getColor(context, color));
  }

  @CheckResult
  public Observable<Integer> secondaryTextColor() {
    return rxPrefs
        .getInteger(
            KEY_SECONDARY_TEXT_COLOR, resolveColor(context, android.R.attr.textColorSecondary))
        .asObservable();
  }

  @CheckResult
  public Aesthetic primaryTextInverseColor(@ColorInt int color) {
    editor.putInt(KEY_PRIMARY_TEXT_INVERSE_COLOR, color);
    return this;
  }

  @CheckResult
  public Aesthetic primaryTextColorInverseRes(@ColorRes int color) {
    return primaryTextInverseColor(ContextCompat.getColor(context, color));
  }

  @CheckResult
  public Observable<Integer> primaryTextInverseColor() {
    return rxPrefs
        .getInteger(
            KEY_PRIMARY_TEXT_INVERSE_COLOR,
            resolveColor(context, android.R.attr.textColorPrimaryInverse))
        .asObservable();
  }

  @CheckResult
  public Aesthetic secondaryTextInverseColor(@ColorInt int color) {
    editor.putInt(KEY_SECONDARY_TEXT_INVERSE_COLOR, color);
    return this;
  }

  @CheckResult
  public Aesthetic secondaryTextInverseColorRes(@ColorRes int color) {
    return secondaryTextInverseColor(ContextCompat.getColor(context, color));
  }

  @CheckResult
  public Observable<Integer> secondaryTextInverseColor() {
    return rxPrefs
        .getInteger(
            KEY_SECONDARY_TEXT_INVERSE_COLOR,
            resolveColor(context, android.R.attr.textColorSecondaryInverse))
        .asObservable();
  }

  @CheckResult
  public Aesthetic windowBgColor(@ColorInt int color) {
    editor.putInt(KEY_WINDOW_BG_COLOR, color);
    return this;
  }

  @CheckResult
  public Aesthetic windowBgColorRes(@ColorRes int color) {
    return windowBgColor(ContextCompat.getColor(context, color));
  }

  @CheckResult
  public Observable<Integer> windowBgColor() {
    return rxPrefs
        .getInteger(KEY_WINDOW_BG_COLOR, resolveColor(context, android.R.attr.windowBackground))
        .asObservable();
  }

  @CheckResult
  public Aesthetic statusBarColor(@ColorInt int color) {
    editor.putInt(KEY_STATUS_BAR_COLOR, color);
    return this;
  }

  @CheckResult
  public Aesthetic statusBarColorRes(@ColorRes int color) {
    return statusBarColor(ContextCompat.getColor(context, color));
  }

  @CheckResult
  public Aesthetic statusBarColorAuto() {
    editor.putInt(
        KEY_STATUS_BAR_COLOR,
        Util.darkenColor(
            prefs.getInt(KEY_PRIMARY_COLOR, resolveColor(context, R.attr.colorPrimary))));
    return this;
  }

  @CheckResult
  public Observable<Integer> statusBarColor() {
    return rxPrefs
        .getInteger(KEY_STATUS_BAR_COLOR, resolveColor(context, R.attr.colorPrimaryDark))
        .asObservable();
  }

  @CheckResult
  public Aesthetic navBarColor(@ColorInt int color) {
    editor.putInt(KEY_NAV_BAR_COLOR, color);
    return this;
  }

  @CheckResult
  public Aesthetic navBarColorRes(@ColorRes int color) {
    return navBarColor(ContextCompat.getColor(context, color));
  }

  @CheckResult
  public Aesthetic navBarColorAuto() {
    int color = prefs.getInt(KEY_PRIMARY_COLOR, resolveColor(context, R.attr.colorPrimary));
    editor.putInt(KEY_NAV_BAR_COLOR, isColorLight(color) ? Color.BLACK : color);
    return this;
  }

  @CheckResult
  public Observable<Integer> navBarColor() {
    return rxPrefs.getInteger(KEY_NAV_BAR_COLOR, Color.BLACK).asObservable();
  }

  @CheckResult
  public Aesthetic lightStatusBarMode(@AutoSwitchMode int mode) {
    editor.putInt(KEY_LIGHT_STATUS_MODE, mode);
    return this;
  }

  @CheckResult
  public Observable<Integer> lightStatusBarMode() {
    return rxPrefs.getInteger(KEY_LIGHT_STATUS_MODE, AutoSwitchMode.AUTO).asObservable();
  }

  public void apply() {
    editor.commit();
  }
}