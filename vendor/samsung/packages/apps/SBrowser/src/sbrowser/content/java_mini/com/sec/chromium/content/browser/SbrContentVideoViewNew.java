// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.sec.chromium.content.browser;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.MediaController;
import org.chromium.base.CalledByNative;
import org.chromium.content.browser.ContentVideoViewClient;

import com.sec.android.app.sbrowser.R;
import com.sec.android.app.sbrowser.SBrowserMainActivity;

public class SbrContentVideoViewNew extends SbrContentVideoView implements SbrVideoControllerView.MediaPlayerControl{
    private static final String TAG = "SbrContentVideoViewNew";
    private static final String SCREEN_AUTO_BRIGHTNESS_DETAIL = "auto_brightness_detail";

    private static final long KEY_LONG_PRESS_TIME = 500L;

    private static final int START_GESTURE = 0;
    private static final int SET_BACKGROUND_TRANSPARENT = 1;
    private static final int MOTION_START_GESTURE_THRESHOLD = 3;

    SbrVideoControllerView mMediaController;
    
    private SbrVideoLockCtrl mVideoLockCtrl;

    private ContentObserver mRotationObserver = null;

    private ContentObserver mObserverAutoBrightness = null;

    private int mCurrentBufferPercentage;

    private int mScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

    private int mXTouchPos = 0;
    private int mYTouchPos = 0;
    private int mDownYPos = 0;
    private int mGestureThreshold = 5;

    private enum VideoGestureMode {
        MODE_UNDEFINED, VOLUME_MODE, BRIGHTNESS_MODE
    }

    private VideoGestureMode mVideoGestureMode = VideoGestureMode.MODE_UNDEFINED;
    private boolean mIsVideoGestureStart = false;
    private SbrVideoGestureView mVideoGestureView = null;

    SbrContentVideoViewNew(Context context, long nativeContentVideoView,
            ContentVideoViewClient client, int width, int height) {
        super(context, nativeContentVideoView, client, width, height);
        
        mMediaController = new SbrVideoControllerView(context);
        mMediaController.setMediaPlayer(this);
        mMediaController.setAnchorView(this);

        mVideoLockCtrl = new SbrVideoLockCtrl(context);
        mGestureThreshold =  (int) (MOTION_START_GESTURE_THRESHOLD * context.getResources().getDisplayMetrics().density);

        mCurrentBufferPercentage = 0;
        registerContentObserver();
    }

    @Override
    protected void openVideo() {
        super.openVideo();
        mCurrentBufferPercentage = 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        toggleMediaControlsVisiblity();
        if (SbrVideoLockCtrl.getLockState() && mVideoLockCtrl != null) {
            mVideoLockCtrl.showLockIcon();
        }
        return false;
    }
    
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_POWER:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    long pressTime = event.getEventTime() - event.getDownTime();
                    if (pressTime < KEY_LONG_PRESS_TIME) {
                        Log.d(TAG,"[html5media] KeyEvent.KEYCODE_POWER for full-screen video LockMode");
                        if (mVideoLockCtrl != null) {
                            if (SbrVideoLockCtrl.getLockState()) {
                                mVideoLockCtrl.toggleFullscreenLock(false);
                            } else {
                                finishUpVideoGesture();
                                mVideoLockCtrl.toggleFullscreenLock(true);
                            }
                        }
                        return true;
                    }
                }
                return false;
            case KeyEvent.KEYCODE_BACK:
                if (getSurfaceState()) {
                    if (SbrVideoLockCtrl.getLockState() && mVideoLockCtrl != null) {
                        mVideoLockCtrl.showLockIcon();
                        return true;
                    }
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        exitFullscreen(false);
                        return true;
                    }
                }
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_APP_SWITCH:
                if (SbrVideoLockCtrl.getLockState() && mVideoLockCtrl != null) {
                    mVideoLockCtrl.showLockIcon();
                    return true;
                }
            default:
                return super.dispatchKeyEvent(event);
        }
    }
    
    @Override
    protected void onUpdateMediaMetadata(
            int videoWidth,
            int videoHeight,
            int duration,
            boolean canPause,
            boolean canSeekBack,
            boolean canSeekForward) {
        super.onUpdateMediaMetadata(videoWidth, videoHeight, duration,
                canPause, canSeekBack, canSeekForward);

        if (mMediaController == null) return;

        mMediaController.setEnabled(true);

        // If paused , should show the controller forever.
        if (isPlaying()) {
            mMediaController.show();
        } else {
            mMediaController.show(0);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        setOverlayVideoMode(true);
        super.surfaceCreated(holder);
        
        if(mHandler != null)
            mHandler.sendEmptyMessageDelayed(SET_BACKGROUND_TRANSPARENT, 200);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        setOverlayVideoMode(false);
        unRegisterContentObserver();

        super.surfaceDestroyed(holder);
        
        if (mHandler != null)
            mHandler.removeMessages(SET_BACKGROUND_TRANSPARENT);
    }

    @Override
    protected void showContentVideoView() {
        SurfaceView surfaceView = getSurfaceView();
        surfaceView.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                boolean isKeyCodeSupported = (
                        keyCode != KeyEvent.KEYCODE_BACK &&
                        keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                        keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                        keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
                        keyCode != KeyEvent.KEYCODE_CALL &&
                        keyCode != KeyEvent.KEYCODE_MENU &&
                        keyCode != KeyEvent.KEYCODE_SEARCH &&
                        keyCode != KeyEvent.KEYCODE_ENDCALL &&
                        keyCode != KeyEvent.KEYCODE_ESCAPE);
                if (event.getAction() == KeyEvent.ACTION_UP)
                    finishUpVideoGesture();
                if (isInPlaybackState() && isKeyCodeSupported && mMediaController != null) {
                    if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                        if (isPlaying()) {
                            pause();
                            mMediaController.show();
                        } else {
                            start();
                            mMediaController.hide();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                        if (!isPlaying()) {
                            start();
                            mMediaController.hide();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                            || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                        if (isPlaying()) {
                            pause();
                            mMediaController.show();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_POWER &&
                            event.getAction() == KeyEvent.ACTION_UP) {
                        long pressTime = event.getEventTime() - event.getDownTime();
                        if (pressTime < KEY_LONG_PRESS_TIME) {
                            Log.d(TAG,"[html5media] KeyEvent.KEYCODE_POWER for full-screen video LockMode");
                            if (mVideoLockCtrl != null) {
                                if (SbrVideoLockCtrl.getLockState()) {
                                    mVideoLockCtrl.toggleFullscreenLock(false);
                                } else {
                                    finishUpVideoGesture();
                                    mVideoLockCtrl.toggleFullscreenLock(true);
                                }
                            }
                            return true;
                        }
                        return false;
                    } else if (keyCode == KeyEvent.KEYCODE_HOME ||
                            keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                        if (SbrVideoLockCtrl.getLockState() && mVideoLockCtrl != null) {
                            mVideoLockCtrl.showLockIcon();
                            return true;
                        }
                    } else {
                        //toggleMediaControlsVisiblity();
                    }
                } else if (keyCode == KeyEvent.KEYCODE_BACK &&
                        event.getAction() == KeyEvent.ACTION_UP) {
                    if (SbrVideoLockCtrl.getLockState() && mVideoLockCtrl != null) {
                        mVideoLockCtrl.showLockIcon();
                        return true;
                    }
                    Log.d(TAG,"[html5media] onkey. back");
                    exitFullscreen(false);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SEARCH) {
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_ESCAPE){
                    Log.d(TAG,"[html5media] onkey. escape");
                    exitFullscreen(false);
                    return true;
                }
                return false;
            }
        });
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!getSurfaceState()) {
                    return true;
                }
                if (SbrVideoLockCtrl.getLockState() && mVideoLockCtrl != null) {
                    mVideoLockCtrl.showLockIcon();
                    return true;
                }
                final int action = event.getAction();
                switch (action) {
                    case MotionEvent.ACTION_POINTER_2_DOWN:
                        Log.d(TAG, "[html5media] OnTouchListener - MotionEvent.ACTION_POINTER_2_DOWN");
                        if (mHandler != null)
                            mHandler.removeMessages(START_GESTURE);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        //It should pass, if start point was the touch area of notification(2 times of height). 
                        if (mDownYPos < getContext().getResources().getDimension(R.dimen.notification_height) * 2) {
                            break;
                        }
                        
                        if (!mIsVideoGestureStart 
                                && (mVideoGestureMode == VideoGestureMode.MODE_UNDEFINED)
                                && !(event.getPointerCount() >= 2)) {
                            int ty = (int) event.getY();
                            int tx = (int) event.getX();
                            int tmoveYVal = Math.abs(mYTouchPos - ty);
                            int tmoveXVal = Math.abs(mXTouchPos - tx);
                            if (tmoveYVal >= mGestureThreshold && tmoveYVal > tmoveXVal * 2) {
                                int screenWidth = ((SBrowserMainActivity) getContext()).getWindow().getDecorView().getWidth();
                                mVideoGestureMode = ((mXTouchPos > screenWidth / 2) ? VideoGestureMode.VOLUME_MODE : VideoGestureMode.BRIGHTNESS_MODE);

                                startShowGesture();
                                mHandler.sendEmptyMessageDelayed(START_GESTURE, 50);
                            }
                        } else if (mIsVideoGestureStart && mVideoGestureView != null && mVideoGestureView.isShowing()) {
                            int y = (int) event.getY();
                            int moveVal = (mYTouchPos - y);

                            if (mVideoGestureMode == VideoGestureMode.VOLUME_MODE) {
                                mVideoGestureView.setVolume(moveVal);
                            } else if (mVideoGestureMode == VideoGestureMode.BRIGHTNESS_MODE)
                                mVideoGestureView.setBrightness(moveVal);
                        }                        
                        mXTouchPos = (int) event.getX();
                        mYTouchPos = (int) event.getY();
                        break;
                    case MotionEvent.ACTION_DOWN:
                        mXTouchPos = (int) event.getX();
                        mYTouchPos = mDownYPos = (int) event.getY();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        if (finishUpVideoGesture()) {
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!finishUpVideoGesture() && !SbrVideoLockCtrl.getLockState() && isInPlaybackState() && mMediaController != null) {
                            toggleMediaControlsVisiblity();
                        }
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.requestFocus();

        super.showContentVideoView();
    }

    @Override
    protected void onBufferingUpdate(int percent) {
        super.onBufferingUpdate(percent);
        mCurrentBufferPercentage = percent;
    }

    private void toggleMediaControlsVisiblity() {
        if (mMediaController.isShowing()) {
            mMediaController.hide();
        } else {
            mMediaController.show();
        }
    }

    @Override
    protected void onMediaInterrupted() {
        if (mMediaController != null) {
            mMediaController.updatePausePlay();
        }
    }

    @Override
    protected void onStart() {
        if (mMediaController != null && mMediaController.isShowing()) {
            mMediaController.hideForced();
            mMediaController.showForced();
        }
    }

    @CalledByNative
    protected void destroyContentVideoView(boolean nativeViewDestroyed) {
        finishUpVideoGesture();
        if (mVideoGestureView != null) {
            mVideoGestureView.releaseView();
            mVideoGestureView = null;
        }
        if (mMediaController != null) {
            SBrowserMainActivity activity = (SBrowserMainActivity)getContext();
            if(mMediaController.isAutoRotation())
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            else
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            
            mMediaController.setEnabled(false);
            mMediaController.hide();
            mMediaController.disconnectWifiDisplay();
            mMediaController = null;
        }

        mVideoLockCtrl.releaseLockCtrlView();
        unRegisterContentObserver();

        super.destroyContentVideoView(nativeViewDestroyed);
    }

    @Override
    protected void onUpdateCCVisibility(int status) {
        // if(mMediaController != null)
           // mMediaController.updateClosedCaptionBtn(status);
    }

    public boolean isLandscape(Context context) {
        if (context == null) return mScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || mScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;

        final Configuration configuration = context.getResources().getConfiguration();
        if (mScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            mScreenOrientation = configuration.orientation;
        }

        return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void registerContentObserver() {
        if(getContext() == null) return;
        Log.d(TAG, "[html5media] registerContentObserver");

        // Auto Rotation Change Observer
        if (mRotationObserver == null) {
            mRotationObserver = new ContentObserver(new Handler()) {
                public void onChange(boolean selfChange) {
                    Log.d(TAG, "[html5media] registerContentObserver onChange()");
                    SBrowserMainActivity activity = (SBrowserMainActivity)getContext();
                    if(mMediaController != null){
                        mMediaController.updateAutoRotationBtn();
                        if(mMediaController.isAutoRotation()){
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        }
                        else{
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                        }
                    }
                }
            };
            ContentResolver cr = getContext().getContentResolver();
            Uri tmp = Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION);
            if(cr != null)
                cr.registerContentObserver(tmp, false, mRotationObserver);
        }
        else{
            Log.d(TAG,"[html5media] registerContentObserver. mRotationObserver is already registered.");
        }
        if (mObserverAutoBrightness == null) {
                mObserverAutoBrightness = new ContentObserver(new Handler()) {
                    public void onChange(boolean selfChange) {
                        SBrowserMainActivity activity = (SBrowserMainActivity) getContext();
                        if (activity != null) {
                            Window window = activity.getWindow();
                            if (window != null) {
                                WindowManager.LayoutParams lp = window.getAttributes();
                                if (lp != null) {
                                    lp.screenBrightness = -1f;
                                    window.setAttributes(lp);
                                }
                            }
                        }
                    }
                };
            ContentResolver cr = getContext().getContentResolver();
            Uri tmp = Settings.System.getUriFor(SCREEN_AUTO_BRIGHTNESS_DETAIL);
            if (cr != null)
                cr.registerContentObserver(tmp, false, mObserverAutoBrightness);
            Log.d(TAG, "[html5media] registerContentObserver - mObserverAutoBrightness is now registered");
        } else {
            Log.d(TAG, "[html5media] registerContentObserver - mObserverAutoBrightness already exists");
        }
    }

    private void unRegisterContentObserver() {
        if(getContext() == null || mRotationObserver == null) return;

        Log.d(TAG, "[html5media] unregisterContentObserver");
        ContentResolver cr = getContext().getContentResolver();
        if (cr != null && mRotationObserver != null) {
            cr.unregisterContentObserver(mRotationObserver);
            mRotationObserver = null;
        }
        if (cr != null && mObserverAutoBrightness != null) {
            cr.unregisterContentObserver(mObserverAutoBrightness);
            mObserverAutoBrightness = null;
        }
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case START_GESTURE:
                    mIsVideoGestureStart = true;
                    break;
                case SET_BACKGROUND_TRANSPARENT:
                    setBackgroundTransparent();
                default:
                    break;
            }
        }
    };

    public void attachVideoGestureView() {
        if (mVideoGestureView == null) {
            mVideoGestureView = new SbrVideoGestureView(((SBrowserMainActivity) getContext()).getWindow(), getContext());
            mVideoGestureView.addViewTo(this);
        }
    }

    public void startShowGesture() {
        mHandler.removeMessages(START_GESTURE);

        if (mVideoGestureView == null)
            attachVideoGestureView();

        if (mVideoGestureView != null) {
            if (mMediaController != null && mMediaController.isShowing())
                mMediaController.hide();

            if (mVideoGestureMode == VideoGestureMode.VOLUME_MODE)
                mVideoGestureView.showVolume();
            else
                mVideoGestureView.showBrightness();
        }
    }

    // returns false if GestureView not used
    public boolean finishUpVideoGesture() {
        if (mHandler != null && mHandler.hasMessages(START_GESTURE))
            mHandler.removeMessages(START_GESTURE);
        
        mIsVideoGestureStart = false;
        
        if (mVideoGestureView != null && mVideoGestureView.isShowing()) {
            mVideoGestureView.hide();
            if (mVideoGestureMode == VideoGestureMode.BRIGHTNESS_MODE)
                mVideoGestureView.syncBrightnessWithSystemLevel();

            mVideoGestureMode = VideoGestureMode.MODE_UNDEFINED;
            return true;
        }
        mVideoGestureMode = VideoGestureMode.MODE_UNDEFINED;
        return false;
    }

    // Implement SbrVideoControllerView.MediaPlayerControl
    @Override
    public boolean canPause() {
        return true;
    }
    @Override
    public boolean canSeekBackward() {
        return true;
    }
    @Override
    public boolean canSeekForward() {
        return true;
    }
    @Override
    public int getBufferPercentage() {
        return mCurrentBufferPercentage;
    }
    @Override
    public int getCurrentPosition() {
        return super.getCurrentPosition();
    }
    @Override
    public int getDuration() {
        return super.getDuration();
    }
    @Override
    public boolean isPlaying() {
        return super.isPlaying();
    }
    @Override
    public void pause() {
        super.pause();
    }
    @Override
    public void seekTo(int i) {
        super.seekTo(i);
    }
    @Override
    public void start() {
        super.start();
    }
    @Override
    public boolean isFullScreen() {
        return false;
    }
    @Override
    public void toggleFullScreen() {
    }
    @Override
    public void toggleScreenOrientation(){
        SBrowserMainActivity activity = (SBrowserMainActivity)getContext();
        if(mScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED){
            mScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            activity.setRequestedOrientation(mScreenOrientation);
            return;
        }
        if(mScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE){
            mScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }
    // End SbrVideoControllerView.MediaPlayerControl
}
