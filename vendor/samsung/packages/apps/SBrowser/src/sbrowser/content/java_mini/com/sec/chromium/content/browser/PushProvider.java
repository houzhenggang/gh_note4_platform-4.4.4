// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.util.Log;

import com.sec.android.app.sbrowser.webpushnotifications.controller.GWClient;
import com.sec.android.app.sbrowser.webpushnotifications.model.PushRegistration;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.util.ArrayList;
import java.util.List;


// This is the implementation of the C++ counterpart PushProvider.
@JNINamespace("content")
class PushProvider {
    private static final String TAG = "PushProvider";
    private long mNativePushProviderAndroid;

    String removeSlashFromOrigin(String origin) {
        if (origin.charAt(origin.length()-1) != '/')
            return origin;

        Log.e(TAG, "registerPush : " + origin.substring(0, origin.length()-1));
        return origin.substring(0, origin.length()-1);
    }

    @CalledByNative
    static PushProvider create(long nativePushProviderAndroid) {
        return new PushProvider(nativePushProviderAndroid);
    }

    @CalledByNative
    void register(final Context context, String origin, long requestId) {
        GWClient.PushCallback callback = new PushCallbackImpl(this, requestId);
        Log.e(TAG, "registerPush : " + removeSlashFromOrigin(origin));

        GWClient.getGWClientInstance(context).register(removeSlashFromOrigin(origin), callback);
    }

    @CalledByNative
    void unregister(final Context context, String origin, long requestId) {
        GWClient.PushCallback callback = new PushCallbackImpl(this, requestId);
        Log.e(TAG, "unregisterPush : " + removeSlashFromOrigin(origin));

        GWClient.getGWClientInstance(context).unregister(removeSlashFromOrigin(origin), callback);
    }

    @CalledByNative
    boolean isRegistered(final Context context, String origin) {
        return  GWClient.getGWClientInstance(context).isRegistered(removeSlashFromOrigin(origin));
    }

    private PushProvider(long nativePushProviderAndroid) {
        mNativePushProviderAndroid = nativePushProviderAndroid;
    }

    private native void nativeDidRegister(long nativePushProviderAndroid, int result, PushRegistration pushRegistration, long requestId);
    private native void nativeDidUnregister(long nativePushProviderAndroid, int result, long requestId);

    public class PushCallbackImpl implements GWClient.PushCallback {
        private PushProvider mProvider;
        private long mRequestId;

        public PushCallbackImpl(PushProvider provider, long requestId) {
            mProvider =  provider;
            mRequestId = requestId;
        }

        // result = 1 : success, result = 0 : error
        @Override
        public void didRegister(int result, PushRegistration pushregistration) {
            mProvider.nativeDidRegister(mProvider.mNativePushProviderAndroid, result, pushregistration, mRequestId);
        }

        @Override
        public void didUnregister(int result, String pushRegisterationId) {
            Log.e("PushProvider", "didUnregister : " + result);
            mProvider.nativeDidUnregister(mProvider.mNativePushProviderAndroid, result, mRequestId);
        }
    }
}
