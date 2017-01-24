// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.app.enterprise.BrowserPolicy;
import android.app.enterprise.EnterpriseDeviceManager;
import android.content.Context;
import android.widget.Button;

import org.chromium.chrome.R;

/**
 * An infobar that presents the user with up to 2 buttons.
 */
public abstract class TwoButtonInfoBar extends InfoBar {
    // The string value should always be in sync with
    // IDS_POPUPS_BLOCKED_INFOBAR_BUTTON_SHOW inside the native .grd
    // As we don't have any information in application about type of infobar.
    // And as per the native below string is only used in case of blocked
    // infobar popup.
    private static final String BLOCKED_INFOBAR_BUTTON_SHOW = "Always show";

    public TwoButtonInfoBar(InfoBarListeners.Dismiss dismissListener, int backgroundType,
            int iconDrawableId) {
        super(dismissListener, backgroundType, iconDrawableId);
    }

    /**
     * Creates controls for the current InfoBar.
     * @param layout InfoBarLayout to find controls in.
     */
    @Override
    public void createContent(InfoBarLayout layout) {
        Context context = layout.getContext();
        //WTL_EDM_START
        BrowserPolicy policy = null;
        EnterpriseDeviceManager edm = (EnterpriseDeviceManager) context
                .getSystemService(EnterpriseDeviceManager.ENTERPRISE_POLICY_SERVICE);
        boolean popupSetting = true;
        if(edm != null)
            policy = edm.getBrowserPolicy();

        if(policy != null)
            popupSetting = policy.getPopupsSetting();

        if(getSecondaryButtonText(context).isEmpty()
                && !getPrimaryButtonText(context).isEmpty()
                && getPrimaryButtonText(context).equals(BLOCKED_INFOBAR_BUTTON_SHOW)
                && popupSetting == false)
            return;
        //WTL_EDM_END
        layout.addButtons(getPrimaryButtonText(context), getSecondaryButtonText(context));
    }

    @Override
    public void setControlsEnabled(boolean state) {
        super.setControlsEnabled(state);

        // Handle the buttons.
        ContentWrapperView wrapper = getContentWrapper(false);
        if (wrapper != null) {
            Button primary = (Button) wrapper.findViewById(R.id.button_primary);
            Button secondary = (Button) wrapper.findViewById(R.id.button_secondary);
            if (primary != null) primary.setEnabled(state);
            if (secondary != null) secondary.setEnabled(state);
        }
    }
}
