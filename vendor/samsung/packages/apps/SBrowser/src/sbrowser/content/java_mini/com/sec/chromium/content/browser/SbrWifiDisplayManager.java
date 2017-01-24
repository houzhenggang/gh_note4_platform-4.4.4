/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sec.chromium.content.browser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
//import android.hardware.display.DisplayManager.WfdAppState;
import android.hardware.display.WifiDisplay;
import android.hardware.display.WifiDisplayStatus;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.sec.android.app.sbrowser.R;
import com.sec.android.app.sbrowser.SBrowserMainActivity;
import com.sec.chromium.content.browser.SbrVideoControllerView.MediaPlayerControl;
public class SbrWifiDisplayManager{
    private static final String TAG = "SbrWifiDisplayManager";
    
    private Context                 mContext;
    private AlertDialog             mDialog = null;
    protected DisplayManager        mDisplayManager = null;
    protected WifiDisplayStatus     mWifiDisplayStatus = null;
    private final IntentFilter      mIntentFilter = new IntentFilter();
    private boolean                 bRegistered = false;
    private boolean                 bConnecting = false;
    private boolean                 bWasPlaying = false;
    private ChangePlayerAdapter     mChangePlayerAdapter = null;
    private ArrayList<ChangePlayer> mPlayerList = null;
    private ProgressBar             mRefreshProgressBar = null;
    private MediaPlayerControl      mPlayer;
    
    public static final int         DISMISS_PROGRESS_ICON = 100;
    public static final int         UPDATE_CHANGE_PLAYER_LIST = 200;
    public static final int         PROCEED_CHANGE_PLAYER_SHOW = 300;
    
    private final Handler           mPopupHandler = new Handler() {
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case DISMISS_PROGRESS_ICON:
                    mPopupHandler.removeMessages(DISMISS_PROGRESS_ICON);
                    stopScanWifiDisplays();
                    mRefreshProgressBar.setVisibility(View.GONE);
                    break;
                case UPDATE_CHANGE_PLAYER_LIST:
                    mPopupHandler.removeMessages(UPDATE_CHANGE_PLAYER_LIST);
                    updateWifiDisplayList();
                    break;
                case PROCEED_CHANGE_PLAYER_SHOW:
                    mPopupHandler.removeMessages(PROCEED_CHANGE_PLAYER_SHOW);
                    showDevicePopup(bWasPlaying);
                    break;
                default:
                    break;
            }
        };
    };
    public SbrWifiDisplayManager(Context context){
        mContext = context;
        mDisplayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
    }
    
    public void showDevicePopup(boolean wasPlaying){
        bWasPlaying = wasPlaying;
        AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View bodyLayout = inflater.inflate(R.layout.video_change_player, null);
        ListView mDeviceListView = (ListView) bodyLayout.findViewById(R.id.device_list);
        ((TextView)bodyLayout.findViewById(R.id.allshare_title)).setText(R.string.video_available_devices_cap);
        
        dialog.setTitle(mContext.getString(R.string.video_select_device));
        dialog.setView(bodyLayout);
        mRefreshProgressBar = (ProgressBar) bodyLayout.findViewById(R.id.allshare_refresh_progressbar);
        mRefreshProgressBar.setVisibility(View.VISIBLE);
        dialog.setPositiveButton(R.string.accessibility_button_refresh, null);
        dialog.setNegativeButton(R.string.bookmark_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int arg1) {
                dialog.dismiss();
            }
        });
        makeWfdDeviceList();
        
        mChangePlayerAdapter = new ChangePlayerAdapter(mContext, mPlayerList);
        if(mPlayerList == null) return;
        
        mDeviceListView.setAdapter(mChangePlayerAdapter);
        mDeviceListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(parent.getCount() > position) {
                    Log.d(TAG, "[html5media] showDevicePopup. onItemClick. : " + position);
                    if(position > -1)
                        selectWfdDevice(position);
                }
                if(mDialog != null)
                    mDialog.dismiss();
            }
        });
        mDialog = dialog.create();
        mDialog.setOnShowListener(new OnShowListener() {
            public void onShow(DialogInterface dialog) {
                registerWifiDisplayReceiver();
                mPopupHandler.removeCallbacksAndMessages(null);
                mPopupHandler.sendMessageDelayed(mPopupHandler.obtainMessage(DISMISS_PROGRESS_ICON), 10000);
                
                mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        mPopupHandler.removeMessages(DISMISS_PROGRESS_ICON);
                        updateWifiDisplayList();
                        mRefreshProgressBar.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
        mDialog.setCanceledOnTouchOutside(true);
        mDialog.show();
    }
    public void scanWifiDisplays() {
        Log.d(TAG, "[html5media] scanWifiDisplays");
//        if(mDisplayManager != null) {
//            mDisplayManager.setActivityState(WfdAppState.SETUP);
//            if(mDisplayManager.getWifiDisplayStatus().getActiveDisplayState() != WifiDisplayStatus.DISPLAY_STATE_CONNECTED) {
//               mDisplayManager.scanWifiDisplays();
//            }
//        }
    }
    public void stopScanWifiDisplays() {
        Log.d(TAG, "[html5media] stopScanWifiDisplays");
//        if(mDisplayManager != null) {
//            if(mDisplayManager.getWifiDisplayStatus().getActiveDisplayState() == WifiDisplayStatus.SCAN_STATE_NOT_SCANNING) {
//                mDisplayManager.stopScanWifiDisplays();
//            }
//        }
    }
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
//            String action = intent.getAction();
//            if(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED.equals(action)) {
//                WifiDisplayStatus status = (WifiDisplayStatus) intent.getParcelableExtra(DisplayManager.EXTRA_WIFI_DISPLAY_STATUS);
//                Log.d(TAG, "[html5media] WFD status changed. scan : " + status.getScanState() + ", activeDisplay : " + status.getActiveDisplayState());
//                mWifiDisplayStatus = status;
//                if(mWifiDisplayStatus != null && status.getActiveDisplayState() == WifiDisplayStatus.DISPLAY_STATE_CONNECTED){
                    stopScanWifiDisplays();
                    if(mPlayer != null && bWasPlaying)
                        mPlayer.start();
                    return;
//                }
//                else
//                    updateWifiDisplayList();
            }
//        }
    };
    public void registerWifiDisplayReceiver() {
//        mIntentFilter.addAction(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED);
        mContext.registerReceiver(mReceiver, mIntentFilter);
        bRegistered = true;
    }
    public void unregisterWifiDisplayReceiver() {
        if(bRegistered) {
            bRegistered = false;
            if(mReceiver != null) {
                mContext.unregisterReceiver(mReceiver);
            }
        }
    }
    public void disconnectWifiDisplay(){
        Log.d(TAG, "[html5media] disconnectWifiDisplay");
//        if(mDisplayManager != null)
//            mDisplayManager.disconnectWifiDisplayExt();
    }
    private void makeWfdDeviceList() {
        List<Parcelable> availableDisplays = new ArrayList<Parcelable>();
        String wfdName = null;
        String deviceType = null;
        WifiDisplay display = null;
        String[] tokens = null;
        if(mPlayerList == null) {
            mPlayerList = new ArrayList<ChangePlayer>();
        } else {
            mPlayerList.clear();
        }
        if(mWifiDisplayStatus != null) {
            for (WifiDisplay d : mWifiDisplayStatus.getDisplays()) {
                if(d.isAvailable()) availableDisplays.add(d);
            }
        }
        else
            return;
        Iterator<Parcelable> iter = availableDisplays.iterator();
        while (iter.hasNext()) {
            display = (WifiDisplay)iter.next();
            wfdName = display.getDeviceName();
            if(TextUtils.isEmpty(wfdName)) {
                wfdName = display.getDeviceAddress();
            }
            Log.d(TAG, "[html5media] makeWfdDeviceList. deviceName : [" + wfdName + "]");
//            deviceType = display.getPrimaryDeviceType();
            if(deviceType != null) {
                Log.d(TAG, "[html5media] makeWfdDeviceList. primaryDeviceType : " + deviceType);
                tokens = deviceType.split("-");
            }
            mPlayerList.add(new ChangePlayer(wfdName));
        }
    }
    private void selectWfdDevice(int which) {
        String wfdName = null;
        WifiDisplay display = null;
        String connectDeviceName = String.format("%s", mPlayerList.get(which).getDeviceName());
        List<Parcelable> availableDisplays = new ArrayList<Parcelable>();
        Log.d(TAG, "[html5media] selectWfdDevice. mConnectDeviceName : " + connectDeviceName);
        if(mWifiDisplayStatus != null) {
            for (WifiDisplay d : mWifiDisplayStatus.getDisplays()) {
                if(d.isAvailable()) availableDisplays.add(d);
            }
        }
        
        for (Parcelable d : availableDisplays) {
            display = (WifiDisplay)d;
            wfdName = display.getDeviceName();
            if(mPlayerList.get(which).getDeviceName().equals(wfdName)) {
                if(mDisplayManager != null) {
//                    mDisplayManager.setActivityState(WfdAppState.RESUME);
                    Log.d(TAG, "[html5media]connectWifiDisplay. deviceAddress : " + display.getDeviceAddress());
//                    mDisplayManager.connectWifiDisplayWithMode(WifiDisplayStatus.CONN_STATE_NORMAL, display.getDeviceAddress());
                    stopScanWifiDisplays();
                    break;
                }
                break;
            }
        }
    }
    public void updateWifiDisplayList() {
        if(mDialog != null && mDialog.isShowing() && mPlayerList != null && mChangePlayerAdapter != null) {
            makeWfdDeviceList();
            ((SBrowserMainActivity)mContext).runOnUiThread(new Runnable() {
                public void run() {
                    mChangePlayerAdapter.notifyDataSetChanged();
                }
            });
        }
    }
    public void setMediaPlayer(MediaPlayerControl player){
        mPlayer = player;
    }
    
    public class ChangePlayer {
        private String mDeviceName;
        public ChangePlayer(String name) {
            mDeviceName = name;
        }
        public String getDeviceName() {
            return mDeviceName;
        }
    }
    
    public class ChangePlayerAdapter extends ArrayAdapter<ChangePlayer> {
        private LayoutInflater vi;
        private String deviceName;        
        private String DEVICE_NAME = "device_name";
        public ChangePlayerAdapter(Context context, ArrayList<ChangePlayer> items) {
            super(context, 0, items);
            vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            deviceName = Settings.System.getString(context.getContentResolver(), DEVICE_NAME);
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            ViewHolder holder = null;
            
            if(v == null) {
                v = vi.inflate(R.layout.videoplayer_change_player_list, null);
                holder = new ViewHolder();
                holder.dmrName = (TextView) v.findViewById(R.id.playername);
                holder.dmrDescription = (TextView) v.findViewById(R.id.playerdescription);
                v.setTag(holder);
            } else {
                holder = (ViewHolder) v.getTag();
            }
            
            CheckedTextView checkIcon = (CheckedTextView) v.findViewById(R.id.selected_player_check);
            if(position == 0) {
                checkIcon.setChecked(true);
            } else {
                checkIcon.setChecked(false);
            }
            ChangePlayer deviceItem = getItem(position);
            if(deviceItem != null) {
                if(holder.dmrName != null) {
                    holder.dmrName.setText(deviceItem.getDeviceName());
                }
                if(holder.dmrDescription != null) {
                    holder.dmrDescription.setText(R.string.changeplayer_descrpition_mirroron);
                }
            }
            return v;
        }
        private class ViewHolder {
            public TextView dmrName;            
            public TextView dmrDescription;
        }
    }
}
