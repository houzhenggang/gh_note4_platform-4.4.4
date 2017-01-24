/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.fxa.sync;

import org.mozilla.gecko.background.common.log.Logger;
import org.mozilla.gecko.fxa.authenticator.AndroidFxAccount;
import org.mozilla.gecko.fxa.login.State;
import org.mozilla.gecko.fxa.login.State.Action;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;

import com.sec.android.app.sbrowser.R;
import com.sec.android.app.sbrowser.firefox.FxAccountConfirmAccountActivity;
import com.sec.android.app.sbrowser.firefox.FxAccountGetStartedActivity;

/**
 * Abstraction that manages notifications shown or hidden for a Firefox Account.
 * <p>
 * In future, we anticipate this tracking things like:
 * <ul>
 * <li>new engines to offer to Sync;</li>
 * <li>service interruption updates;</li>
 * <li>messages from other clients.</li>
 * </ul>
 */
public class FxAccountNotificationManager {
  private static final String LOG_TAG = FxAccountNotificationManager.class.getSimpleName();

  protected final int notificationId;

  public FxAccountNotificationManager(int notificationId) {
    this.notificationId = notificationId;
  }

  /**
   * Reflect new Firefox Account state to the notification manager: show or hide
   * notifications reflecting the state of a Firefox Account.
   *
   * @param context
   *          Android context.
   * @param fxAccount
   *          Firefox Account to reflect to the notification manager.
   */
  protected void update(Context context, AndroidFxAccount fxAccount,Bundle extra) {
    final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

    final State state = fxAccount.getState();
    final Action action = state.getNeededAction();
    if (action == Action.None) {
      Logger.info(LOG_TAG, "State " + state.getStateLabel() + " needs no action; cancelling any existing notification.");
      notificationManager.cancel(notificationId);
      return;
    }

    final String title = extra.getString("notification_icon_title");
    final String text = extra.getString("notification_icon_text");
    Logger.info(LOG_TAG, "State " + state.getStateLabel() + " needs action; offering notification with title: " + title);
    Logger.info(LOG_TAG, "And text: " + text);
    Intent notificationIntent = new Intent(context, FxAccountGetStartedActivity.class);
    switch (state.getNeededAction()) {
    case NeedsUpgrade:
    	 Log.e(LOG_TAG, "NeedsUpgrade Notification NOT HANDLED ");
      break;
    case NeedsPassword:
      Log.e(LOG_TAG, "NeedsPassword Notification NOT HANDLED ");
      break;
    case NeedsVerification:
    	notificationIntent = new Intent(context, FxAccountConfirmAccountActivity.class);
      break;
    default:
    	notificationIntent = new Intent(context, FxAccountGetStartedActivity.class);
    }
    final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

    final Builder builder = new NotificationCompat.Builder(context);
    builder
    .setContentTitle(title)
    .setContentText(text)
    .setSmallIcon(R.drawable.fxaccount_icon)
    .setAutoCancel(true)
    .setContentIntent(pendingIntent);
    notificationManager.notify(notificationId, builder.build());
  }
}