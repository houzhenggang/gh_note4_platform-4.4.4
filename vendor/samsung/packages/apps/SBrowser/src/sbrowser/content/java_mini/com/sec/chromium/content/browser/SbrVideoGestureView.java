// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.sec.chromium.content.browser;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.GeneralUtil;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

import com.samsung.android.feature.FloatingFeature;
//import com.samsung.android.sdk.multiwindow.SMultiWindowActivity;
import com.sec.android.app.CscFeature;
import com.sec.android.app.CscFeatureTagCallDialerLogSettings;
import com.sec.android.app.sbrowser.R;
//import com.sec.android.touchwiz.widget.TwSeekBar;

import java.util.Formatter;
import java.util.List;
import java.util.Locale;

public class SbrVideoGestureView {
    private static final String TAG = "SbrVideoGestureView";
    private static final String SCREEN_AUTO_BRIGHTNESS_DETAIL ="auto_brightness_detail";
    private static final int UNDEFINED = -1;
    private static final int FALSE = 0;
    private static final int TRUE = 1;
    private int GESTURE_MAX_PROGRESS = 255;
    private Window mWindow = null;
    private Context mContext = null;
    private FrameLayout mParentView = null;
    private View mGestureView = null;
    private RelativeLayout mBrightnessGestureLayout = null;
    private TextView mBrightnessText = null;
//    private TwSeekBar mBrightnessSeekBar = null;
    private VideoBrightnessUtil mBrightUtil = null;
    private RelativeLayout mVolumeGestureLayout = null;
    private ImageView mVolumeImage = null;
    private ImageView mBrightnessImage = null;
    private TextView mVolumeText = null;
//    private TwSeekBar mVolumeSeekBar = null;
    private PopupWindow mVolumePopup = null;
    private PopupWindow mBrightnessPopup = null;
    private Bitmap mBitmap = null;
    // Utils for System settings
    private VideoAudioUtil mAudioUtil = null;

    public SbrVideoGestureView(Window window, Context context) {
        Log.d(TAG, "[html5media] SbrVideoGestureView created.");
        mContext = context;
        mWindow = window;
        GESTURE_MAX_PROGRESS =
                mContext.getResources().getInteger(R.integer.gesture_max_range);
        mAudioUtil = new VideoAudioUtil(context);
        mBrightUtil = new VideoBrightnessUtil(mWindow, mContext);
    }

    public void addViewTo(View view) {
        if (mContext == null) {
            Log.d(TAG, "[html5media] addViewTo : context is null");
            return;
        }
        LayoutInflater inflate =
                (LayoutInflater) mContext
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mParentView = (FrameLayout) view;
        mGestureView =
                inflate.inflate(R.layout.videoplayer_gesture_layout, null);
        initGestureView();
    }

    public void showVolume() {
        if (mAudioUtil.isAllSoundOff(mContext)) {
            mAudioUtil.volumeSame(); // just to create system warning toast
                                     // popup
            return;
        }
        int vol = mAudioUtil.getCurrentVolume();
        int maxVol = mAudioUtil.getMaxVolume();
        float currentLevel =
                (float) (((float) vol * (float) GESTURE_MAX_PROGRESS) / (float) maxVol);
        setVolumeUI(vol, (int) currentLevel);
    }

    public void setVolume(int level) {
        if (mAudioUtil.isAllSoundOff(mContext)) {
            mAudioUtil.volumeSame(); // just to create system warning toast
                                     // popup
            return;
        }
//        float vol = mAudioUtil.getCurrentVolume();
//        int maxVol = mAudioUtil.getMaxVolume();
        
//        int currentLevel = mVolumeSeekBar.getProgress();
//        currentLevel = currentLevel + level;
//        vol =
//                (float) maxVol
//                        * (((float) currentLevel) / ((float) GESTURE_MAX_PROGRESS));
//        setVolumeUI((int) vol, currentLevel);
    }

    private void setVolumeUI(int vol, int level) {
        StringBuilder mFormatBuilder = new StringBuilder();
        Formatter mFormatter =
                new Formatter(mFormatBuilder, Locale.getDefault());
        int maxVol = mAudioUtil.getMaxVolume();
        if (vol < 0) {
            vol = 0;
        } else if (vol > maxVol) {
            vol = maxVol;
        }
        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }
        if (vol == 0) {
            mBitmap =
                    BitmapFactory.decodeResource(mContext.getResources(),
                            R.drawable.video_popup_icon_mute_n);
        } else {
            mBitmap =
                    BitmapFactory.decodeResource(mContext.getResources(),
                            R.drawable.video_popup_icon_volume_n);
        }
        mVolumeImage.setImageBitmap(mBitmap);
        mAudioUtil.setVolume((int) vol);
        mFormatBuilder.setLength(0);
        int volume = mAudioUtil.getCurrentVolume();
        mVolumeText.setText(mFormatter.format("%d", volume).toString());
        mFormatter.close();
        if (vol <= volume)
//            mVolumeSeekBar.setProgress(level);
        setVolumeBarVisible();
    }

    public void showBrightness() {
        int level = mBrightUtil.getSystemBrightnessLevel();
        int maxLevel = mBrightUtil.getBrightnessRange();
        float currentLevel =
                (float) level
                        * ((float) GESTURE_MAX_PROGRESS / (float) maxLevel);
        setBrightnessBarUI((int) currentLevel);
        setBrightnessBarVisible();
    }

    public void setBrightness(int level) {
        if (mBrightUtil == null)
            return;
        setBrightnessBarVisible();
//        int maxVol = mBrightUtil.getBrightnessRange();
//        int currentLevel = mBrightnessSeekBar.getProgress();
//        currentLevel = currentLevel + level;
//        float val =
//                (float) maxVol
//                        * ((float) currentLevel / (float) GESTURE_MAX_PROGRESS);
//        mBrightUtil.setBrightness((int) val);
//        setBrightnessBarUI(currentLevel);
    }

    public void syncBrightnessWithSystemLevel() {
        if (mBrightUtil == null)
            return;
        mBrightUtil.setSystemBrightnessLevel();
        mBrightUtil.resetWindowBrightness();
    }

    public boolean isShowing() {
        return (mVolumePopup != null && mVolumePopup.isShowing() || mBrightnessPopup != null
                && mBrightnessPopup.isShowing());
    }

    public void hide() {
        setVolumeBarGone();
        setBrightnessBarGone();
    }

    private void initGestureView() {
        Log.d(TAG, "[html5media] initGestureView");
        if (mGestureView == null) {
            Log.d(TAG, "[html5media] initGestureView : mGestureView is null");
            return;
        }
        mVolumePopup = new PopupWindow(mContext);
        mVolumePopup.setAnimationStyle(android.R.style.Animation_Toast);
        mBrightnessPopup = new PopupWindow(mContext);
        mBrightnessPopup.setAnimationStyle(android.R.style.Animation_Toast);
        // Brightness
        mBrightnessGestureLayout =
                (RelativeLayout) mGestureView
                        .findViewById(R.id.brightness_gesture_vertical);
        // mBrightnessSeekBar = (TwSeekBar)
        // mGestureView.findViewById(R.id.brightness_gesture_seekbar);
        mBrightnessText =
                (TextView) mGestureView
                        .findViewById(R.id.brightness_gesture_text);
        mBrightnessImage =
                (ImageView) mGestureView
                        .findViewById(R.id.brightness_gesture_img);
//        if (mBrightnessSeekBar != null) {
//            mBrightnessSeekBar.setMax(GESTURE_MAX_PROGRESS);
//            mBrightnessSeekBar.setProgress(mBrightUtil
//                    .getSystemBrightnessLevel());
//        }
        // Volume
        mVolumeGestureLayout =
                (RelativeLayout) mGestureView
                        .findViewById(R.id.volume_gesture_vertical);
        mVolumeImage =
                (ImageView) mGestureView.findViewById(R.id.volume_gesture_img);
        mVolumeText =
                (TextView) mGestureView.findViewById(R.id.volume_gesture_text);
        // mVolumeSeekBar = (TwSeekBar)
        // mGestureView.findViewById(R.id.volume_gesture_seekbar);
        /*if (mVolumeSeekBar != null) {
            mVolumeSeekBar.setMax(GESTURE_MAX_PROGRESS);
            mVolumeSeekBar.setProgress(mAudioUtil.getPreviousVolume());
        }*/
    }

    private void setBrightnessBarUI(int level) {
        if (mBrightUtil == null) {
            Log.d(TAG, "[html5media] setBrightnessBar : mBrightUtil is null");
            return;
        }
        if (level > GESTURE_MAX_PROGRESS)
            level = GESTURE_MAX_PROGRESS;
        else if (level < 0)
            level = 0;
        setBrightnessText(level);
//        mBrightnessSeekBar.setProgress(level);
    }

    private void setBrightnessText(int level) {
        StringBuilder mFormatBuilder = new StringBuilder();
        Formatter mFormatter =
                new Formatter(mFormatBuilder, Locale.getDefault());
        int percent =
                (int) (((float) level / (float) GESTURE_MAX_PROGRESS) * 100);
        mFormatBuilder.setLength(0);
        mBrightnessText.setText(mFormatter.format("%d", percent).toString());
        mFormatter.close();
        updateBrightnessIcon(percent);
    }

    private void setBrightnessBarVisible() {
        if (mGestureView != null)
            mGestureView.setVisibility(View.VISIBLE);
        setVolumeBarGone();
        mBrightnessGestureLayout.setVisibility(View.VISIBLE);
        if (mGestureView != null) {
            mGestureView.findViewById(R.id.brightness_gesture_vertical)
                    .setVisibility(View.VISIBLE);
            mGestureView.findViewById(R.id.brightness_gesture_img_layout)
                    .setVisibility(View.VISIBLE);
            mGestureView.findViewById(R.id.brightness_gesture_img)
                    .setVisibility(View.VISIBLE);
            mGestureView.findViewById(R.id.brightness_gesture_seekbar_layout)
                    .setVisibility(View.VISIBLE);
            // mGestureView.findViewById(R.id.brightness_gesture_seekbar).setVisibility(View.VISIBLE);
        }
        if (mBrightnessPopup != null && mGestureView != null) {
            mBrightnessPopup.setContentView(mGestureView);
            RelativeLayout.LayoutParams paramBrightness =
                    (LayoutParams) mGestureView.findViewById(
                            R.id.brightness_gesture_vertical).getLayoutParams();
            mBrightnessPopup.setWidth(paramBrightness.width
                    + paramBrightness.leftMargin);
            mBrightnessPopup.setHeight(paramBrightness.height);
            mBrightnessPopup.setFocusable(false);
            mBrightnessPopup.setBackgroundDrawable(new BitmapDrawable());
            int marginTop =
                    GeneralUtil.isTablet() == true ? 0 : (int) mContext
                            .getResources().getDimension(
                                    R.dimen.vgl_gesture_layout_marginTop);
            int gravity =
                    GeneralUtil.isTablet() == true
                        ? (Gravity.LEFT | Gravity.CENTER_VERTICAL)
                        : (Gravity.LEFT | Gravity.TOP);
            if (isMultiWindowRunning()) {
                int windowHeight = mWindow.getDecorView().getHeight();
                if (windowHeight < paramBrightness.height) {
                    if (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                        if (isTopLocationMultiwindow())
                            gravity = Gravity.LEFT | Gravity.TOP;
                        else
                            gravity = Gravity.LEFT | Gravity.BOTTOM;
                        marginTop = 0;
                    }
                }
            }
            mBrightnessPopup.showAtLocation(mParentView, gravity, 0, marginTop);
        }
    }

    private void setBrightnessBarGone() {
        mBrightnessGestureLayout.setVisibility(View.GONE);
        if (mGestureView != null) {
            mGestureView.findViewById(R.id.brightness_gesture_vertical)
                    .setVisibility(View.GONE);
            mGestureView.findViewById(R.id.brightness_gesture_img_layout)
                    .setVisibility(View.GONE);
            mGestureView.findViewById(R.id.brightness_gesture_img)
                    .setVisibility(View.GONE);
            mGestureView.findViewById(R.id.brightness_gesture_seekbar_layout)
                    .setVisibility(View.GONE);
            // mGestureView.findViewById(R.id.brightness_gesture_seekbar).setVisibility(View.GONE);
        }
        if (mBrightnessPopup != null && mBrightnessPopup.isShowing())
            mBrightnessPopup.dismiss();
    }

    private void setVolumeBarVisible() {
        mAudioUtil.dismissVolumePanel();
        if (mGestureView != null)
            mGestureView.setVisibility(View.VISIBLE);
        setBrightnessBarGone();
        mVolumeGestureLayout.setVisibility(View.VISIBLE);
        if (mGestureView != null) {
            mGestureView.findViewById(R.id.volume_gesture_vertical)
                    .setVisibility(View.VISIBLE);
            mGestureView.findViewById(R.id.volume_gesture_img_layout)
                    .setVisibility(View.VISIBLE);
            mGestureView.findViewById(R.id.volume_gesture_img).setVisibility(
                    View.VISIBLE);
            mGestureView.findViewById(R.id.volume_gesture_text_layout)
                    .setVisibility(View.VISIBLE);
            mGestureView.findViewById(R.id.volume_gesture_text).setVisibility(
                    View.VISIBLE);
            mGestureView.findViewById(R.id.volume_gesture_seekbar_layout)
                    .setVisibility(View.VISIBLE);
            // mGestureView.findViewById(R.id.volume_gesture_seekbar).setVisibility(View.VISIBLE);
        }
        if (mVolumePopup != null && mGestureView != null) {
            mVolumePopup.setContentView(mGestureView);
            RelativeLayout.LayoutParams rp =
                    (LayoutParams) mGestureView.findViewById(
                            R.id.volume_gesture_vertical).getLayoutParams();
            mVolumePopup.setWidth(rp.width + rp.rightMargin);
            mVolumePopup.setHeight(rp.height);
            mVolumePopup.setFocusable(false);
            mVolumePopup.setBackgroundDrawable(new BitmapDrawable());
            int marginTop =
                    GeneralUtil.isTablet() ? 0 : (int) mContext.getResources()
                            .getDimension(R.dimen.vgl_gesture_layout_marginTop);
            int gravity =
                    GeneralUtil.isTablet()
                        ? (Gravity.RIGHT | Gravity.CENTER_VERTICAL)
                        : (Gravity.RIGHT | Gravity.TOP);
            if (isMultiWindowRunning()) {
                int windowHeight = mWindow.getDecorView().getHeight();
                if (windowHeight < rp.height) {
                    if (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                        if (isTopLocationMultiwindow()) {
                            gravity = Gravity.RIGHT | Gravity.TOP;
                        } else {
                            gravity = Gravity.RIGHT | Gravity.BOTTOM;
                        }
                        marginTop = 0;
                    }
                }
            }
            mVolumePopup.showAtLocation(mParentView, gravity, 0, marginTop);
        }
    }

    private void setVolumeBarGone() {
        mVolumeGestureLayout.setVisibility(View.GONE);
        if (mGestureView != null) {
            mGestureView.findViewById(R.id.volume_gesture_vertical)
                    .setVisibility(View.GONE);
            mGestureView.findViewById(R.id.volume_gesture_img_layout)
                    .setVisibility(View.GONE);
            mGestureView.findViewById(R.id.volume_gesture_img).setVisibility(
                    View.GONE);
            mGestureView.findViewById(R.id.volume_gesture_text_layout)
                    .setVisibility(View.GONE);
            mGestureView.findViewById(R.id.volume_gesture_text).setVisibility(
                    View.GONE);
            mGestureView.findViewById(R.id.volume_gesture_seekbar_layout)
                    .setVisibility(View.GONE);
            // mGestureView.findViewById(R.id.volume_gesture_seekbar).setVisibility(View.GONE);
        }
        if (mVolumePopup != null && mVolumePopup.isShowing())
            mVolumePopup.dismiss();
    }

    public void releaseView() {
        Log.d(TAG, "[html5media] releaseView");
        hide();
        mGestureView = null;
        mParentView = null;
    }

    private void updateBrightnessIcon(int percent) {
        if (mBrightnessImage != null) {
            if (mBitmap != null) {
                mBitmap.recycle();
                mBitmap = null;
            }
            if (percent == 100)
                mBitmap =
                        BitmapFactory.decodeResource(mContext.getResources(),
                                R.drawable.video_popup_icon_no_lightness_10_n);
            else if (percent > 90)
                mBitmap =
                        BitmapFactory.decodeResource(mContext.getResources(),
                                R.drawable.video_popup_icon_no_lightness_09_n);
            else if (percent > 80)
                mBitmap =
                        BitmapFactory.decodeResource(mContext.getResources(),
                                R.drawable.video_popup_icon_no_lightness_08_n);
            else if (percent > 70)
                mBitmap =
                        BitmapFactory.decodeResource(mContext.getResources(),
                                R.drawable.video_popup_icon_no_lightness_07_n);
            else if (percent > 60)
                mBitmap =
                        BitmapFactory.decodeResource(mContext.getResources(),
                                R.drawable.video_popup_icon_no_lightness_06_n);
            else if (percent > 50)
                mBitmap =
                        BitmapFactory.decodeResource(mContext.getResources(),
                                R.drawable.video_popup_icon_no_lightness_05_n);
            else if (percent > 40)
                mBitmap =
                        BitmapFactory.decodeResource(mContext.getResources(),
                                R.drawable.video_popup_icon_no_lightness_04_n);
            else if (percent > 30)
                mBitmap =
                        BitmapFactory.decodeResource(mContext.getResources(),
                                R.drawable.video_popup_icon_no_lightness_03_n);
            else if (percent > 20)
                mBitmap =
                        BitmapFactory.decodeResource(mContext.getResources(),
                                R.drawable.video_popup_icon_no_lightness_02_n);
            else if (percent > 10)
                mBitmap =
                        BitmapFactory.decodeResource(mContext.getResources(),
                                R.drawable.video_popup_icon_no_lightness_01_n);
            else
                mBitmap =
                        BitmapFactory.decodeResource(mContext.getResources(),
                                R.drawable.video_popup_icon_no_lightness_00_n);
            mBrightnessImage.setImageBitmap(mBitmap);
        }
    }

    private boolean isMultiWindowRunning() {
        return "1".equals(SystemProperties.get("sys.multiwindow.running"));
    }

    private boolean isTopLocationMultiwindow() {
       // if (new SMultiWindowActivity((Activity) mContext).getZoneInfo() == SMultiWindowActivity.ZONE_A) {
       //     return true;
      //  }
        return false;
    }

    protected boolean keyVolumeDown() {
        if (mAudioUtil != null && mContext != null) {
            if (mAudioUtil.isAllSoundOff(mContext)) {
                mAudioUtil.volumeSame(); // just to create system warning toast
                                         // popup
                return false;
            } else {
                mAudioUtil.volumeDown();
            }
        }
        return true;
    }

    protected boolean keyVolumeUp() {
        if (mAudioUtil != null && mContext != null) {
            if (mAudioUtil.isAllSoundOff(mContext)) {
                mAudioUtil.volumeSame(); // just to create system warning toast
                                         // popup
                return false;
            } else {
                mAudioUtil.volumeUp();
            }
        }
        return true;
    }

    private class VideoAudioUtil {
        private AudioManager mAudioManager = null;

        public VideoAudioUtil(Context context) {
            mAudioManager =
                    (AudioManager) context
                            .getSystemService(Context.AUDIO_SERVICE);
        }

        public void volumeUp() {
            if (mAudioManager != null)
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE, 0);
        }

        public void volumeDown() {
            if (mAudioManager != null)
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER, 0);
        }

        public void volumeSame() {
            if (mAudioManager != null)
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_SAME, 0);
        }

        public void setVolume(int level) {
            if (mAudioManager != null)
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level,
                        0);
        }

        public int getCurrentVolume() {
            return mAudioManager != null ? mAudioManager
                    .getStreamVolume(AudioManager.STREAM_MUSIC) : -1;
        }

        public int getMaxVolume() {
            return mAudioManager != null ? mAudioManager
                    .getStreamMaxVolume(AudioManager.STREAM_MUSIC) : -1;
        }

        public int getPreviousVolume() {
            if (mAudioManager == null)
                return -1;
            /*
             * return mAudioManager.isStreamMute(AudioManager.STREAM_MUSIC) ?
             * mAudioManager.getLastAudibleStreamVolume(AudioManager.STREAM_MUSIC
             * ) : mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
             */
            return mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }

        public void dismissVolumePanel() {
//            if (mAudioManager != null)
//                mAudioManager.dismissVolumePanel();
        }

        public boolean isAllSoundOff(Context context) {
            return (Settings.System.getInt(context.getContentResolver(),
                    "all_sound_off", 0) == 1);
        }
    }

    protected class VideoBrightnessUtil {
        private boolean mSystemAutoBrightness = false;
        private boolean mIsSupportAutoBrightnessDetail = true;
        private boolean mIsFolderType = false;
        private int mSystemBrightnessLevel = -1;
        private int mMaxBrightness = 255;
        private int mMinBrightness = 20;
        private int mBrightnessRange = 235;
        private int mMaxAutoBrightness = 200;
        private int mAutoBrightSupported;
        private Window mWindow = null;
        private Context mContext = null;

        public VideoBrightnessUtil(Window window, Context context) {
            mWindow = window;
            mContext = context;
            mSystemBrightnessLevel = 0;
            mMaxBrightness = getMaximumScreenBrightnessSetting();
            mMinBrightness = getMinimumScreenBrightnessSetting();
            mBrightnessRange = mMaxBrightness - mMinBrightness;
            mAutoBrightSupported = UNDEFINED;
            getSystemBrightnessLevelandSaveinLocal(context);
            mIsSupportAutoBrightnessDetail = isSupportAutoBrightnessDetail();
            mIsFolderType = isFolderType();
        }

        private float getWindowBrightnessValue(int brightness) {
            return (float) ((float) (brightness + mMinBrightness) / (float) mMaxBrightness);
        }

        public boolean isLightSensorAvailable(Context context) {
            boolean available = false;
            SensorManager sensorMgr =
                    (SensorManager) context
                            .getSystemService(Context.SENSOR_SERVICE);
            List<Sensor> sensorList = sensorMgr.getSensorList(Sensor.TYPE_ALL);
            for (int i = 0; i < sensorList.size(); i++) {
                int sensorType = sensorList.get(i).getType();
                if (sensorType == Sensor.TYPE_LIGHT) {
                    available = true;
                }
            }
            return available;
        }

        public boolean isAutoBrightnessSupported() {
            if (mAutoBrightSupported == UNDEFINED) {
                mAutoBrightSupported =
                        isLightSensorAvailable(mContext) ? TRUE : FALSE;
            }
            return mAutoBrightSupported == TRUE;
        }

        public int getSystemBrightnessLevel() {
            getSystemBrightnessLevelandSaveinLocal(mContext);
            return mSystemBrightnessLevel;
        }

        private void getSystemBrightnessLevelandSaveinLocal(Context context) {
            int autobright = -1;
            if (isAutoBrightnessSupported()) {
                try {
                    autobright =
                            Settings.System.getInt(
                                    context.getContentResolver(),
                                    Settings.System.SCREEN_BRIGHTNESS_MODE);
                } catch (SettingNotFoundException e) {
                    e.printStackTrace();
                }
            }
            mSystemAutoBrightness =
                    (autobright == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            try {
                if (mIsSupportAutoBrightnessDetail && mSystemAutoBrightness)
                    mSystemBrightnessLevel =
                            Settings.System.getInt(
                                    context.getContentResolver(),
                                    SCREEN_AUTO_BRIGHTNESS_DETAIL);
                else
                    mSystemBrightnessLevel =
                            Settings.System.getInt(
                                    context.getContentResolver(),
                                    Settings.System.SCREEN_BRIGHTNESS)
                                    - mMinBrightness;
            } catch (SettingNotFoundException snfe) {
                snfe.printStackTrace();
            }
        }

        public int getBrightnessRange() {
            if (mIsSupportAutoBrightnessDetail && mSystemAutoBrightness) {
                return mMaxAutoBrightness;
            } else {
                return mBrightnessRange;
            }
        }

        public int getMaximumScreenBrightnessSetting() {
            int val = 0;//PowerManager.BRIGHTNESS_ON;
//            if (mContext != null) {
//                PowerManager power =
//                        (PowerManager) mContext
//                                .getSystemService(Context.POWER_SERVICE);
//                val = power.getMaximumScreenBrightnessSetting();
//            }
            return val;
        }

        public int getMinimumScreenBrightnessSetting() {
            int val = 40;
 //           if (mContext != null) {
 //               PowerManager power =
 //                       (PowerManager) mContext
 //                               .getSystemService(Context.POWER_SERVICE);
 //               val = power.getMinimumScreenBrightnessSetting();
 //           }
            return val;
        }

        // SystemBrightness -> Min 20 ~ 255 MAX (Progress : 0 ~ 235)
        public int setBrightness(int level) {
            mSystemBrightnessLevel = level;
            if (mSystemBrightnessLevel < 0)
                mSystemBrightnessLevel = 0;
            if (mIsSupportAutoBrightnessDetail && mSystemAutoBrightness) {
                if (mSystemBrightnessLevel > mMaxAutoBrightness)
                    mSystemBrightnessLevel = mMaxAutoBrightness;
            } else {
                if (mSystemBrightnessLevel > mBrightnessRange)
                    mSystemBrightnessLevel = mBrightnessRange;
            }
            setWindowBrightness();
            setSystemBrightnessLevel();
            return mSystemBrightnessLevel;
        }

        private void setWindowBrightness() {
            if (mWindow == null || mContext == null)
                return;
            Configuration config = mContext.getResources().getConfiguration();
            if (mIsFolderType
                    && config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES
                    && mSystemAutoBrightness) {
                Log.d(TAG, "[html5media] Folder closed!");
                float brightVal =
                        getWindowBrightnessValue(mSystemBrightnessLevel);
                WindowManager.LayoutParams lp = mWindow.getAttributes();
                lp.screenBrightness = brightVal;
                mWindow.setAttributes(lp);
                if (mSystemAutoBrightness)
                    Settings.System.putInt(mContext.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            } else if (mIsSupportAutoBrightnessDetail && mSystemAutoBrightness) {
                Settings.System.putInt(mContext.getContentResolver(),
                        SCREEN_AUTO_BRIGHTNESS_DETAIL, mSystemBrightnessLevel);
            } else {
                float brightVal =
                        getWindowBrightnessValue(mSystemBrightnessLevel);
                WindowManager.LayoutParams lp = mWindow.getAttributes();
                lp.screenBrightness = brightVal;
                mWindow.setAttributes(lp);
            }
        }

        public void setSystemBrightnessLevel() {
            if (mContext != null)
                setSystemBrightnessLevel(mContext);
        }

        public void setSystemBrightnessLevel(Context context) {
            if (mIsSupportAutoBrightnessDetail && mSystemAutoBrightness) {
                try {
                    Settings.System.putInt(mContext.getContentResolver(),
                            SCREEN_AUTO_BRIGHTNESS_DETAIL,
                            mSystemBrightnessLevel);
                } catch (SecurityException doe) {
                    doe.printStackTrace();
                }
            } else {
                try {
                    PowerManager power =
                            (PowerManager) context
                                    .getSystemService(Context.POWER_SERVICE);
                    int brightness = mSystemBrightnessLevel + mMinBrightness;
                    final ContentResolver resolver =
                            mContext.getContentResolver();
                    if (power != null)
//                        power.setBacklightBrightness(brightness);
                    if (mSystemAutoBrightness)
                        Settings.System.putInt(resolver,
                                Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(resolver,
                            Settings.System.SCREEN_BRIGHTNESS, brightness);
                } catch (SecurityException doe) {
                    doe.printStackTrace();
                }
            }
        }

        public void resetWindowBrightness() {
            if (mWindow != null) {
                Log.d(TAG, "[html5media] call resetWindowBrightness");
                resetWindowBrightness(mWindow);
            }
        }

        public void resetWindowBrightness(Window window) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.screenBrightness = -1f;
            window.setAttributes(lp);
        }

        private boolean isSupportAutoBrightnessDetail() {
/*            if ("off"
                    .equals(CscFeature
                            .getInstance()
                            .getString(
                                    CscFeatureTagCallDialerLogSettings.TAG_CSCFEATURE_SETTINGS_CONFIG_AUTOMATIC_BRIGHTNESS_DETAIL))
                    || !FloatingFeature
                            .getInstance()
                            .getEnableStatus(
                                    "SEC_FLOATING_FEATURE_SETTINGS_SUPPORT_AUTOMATIC_BRIGHTNESS_DETAIL")) {
                return true;
            }
*/            return true;
        }

        private boolean isFolderType() {
//            if (mContext != null && mContext.getPackageManager() != null) {
//                return mContext.getPackageManager().hasSystemFeature(
//                        PackageManager.FEATURE_FOLDER_TYPE);
//            }
            return false;
        }
    }
}