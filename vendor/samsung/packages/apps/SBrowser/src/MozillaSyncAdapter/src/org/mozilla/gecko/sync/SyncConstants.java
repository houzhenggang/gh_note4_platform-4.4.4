/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.sync;

import org.mozilla.gecko.background.common.GlobalConstants;

/**
 * Preprocessed class for storing preprocessed values specific to Android Sync.
 */
public class SyncConstants {
  public static final String GLOBAL_LOG_TAG = "FxSync";
  public static final String SYNC_MAJOR_VERSION  = "1";
  public static final String SYNC_MINOR_VERSION  = "0";
  public static final String SYNC_VERSION_STRING = SYNC_MAJOR_VERSION + "." +
                                                   GlobalConstants.MOZ_APP_VERSION + "." +
                                                   SYNC_MINOR_VERSION;

  public static final String SYNC_USER_AGENT = "Firefox AndroidSync " +
                                               SYNC_VERSION_STRING + " (" +
                                               GlobalConstants.MOZ_APP_DISPLAYNAME + ")";

//  public static final String ACCOUNTTYPE_SYNC = "org.mozilla.firefox_sync";
public static final String ACCOUNTTYPE_SYNC = "org.mozilla.firefox";

  /**
   * Bug 790931: this action is broadcast when an Android Sync Account is
   * deleted.  This allows each installed Firefox to delete any Sync Account
   * pickle file and to (try to) wipe its client record from the Sync server.
   * <p>
   * It is protected by signing-level permission PER_ACCOUNT_TYPE_PERMISSION and
   * can be received only by Firefox versions sharing the same Android Sync
   * Account type.
   * <p>
   * See {@link org.mozilla.gecko.sync.setup.SyncAccounts#makeSyncAccountDeletedIntent(android.content.Context, android.accounts.AccountManager, android.accounts.Account)}
   * for contents of the intent.
   */
//  public static final String SYNC_ACCOUNT_DELETED_ACTION = "org.mozilla.firefox_sync.accounts.SYNC_ACCOUNT_DELETED_ACTION";
public static final String SYNC_ACCOUNT_DELETED_ACTION = "org.mozilla.firefox.accounts.SYNC_ACCOUNT_DELETED_ACTION";

  /**
   * Bug 790931: version number of contents of SYNC_ACCOUNT_DELETED_ACTION
   * intent.
   * <p>
   * See {@link org.mozilla.gecko.sync.setup.SyncAccounts#makeSyncAccountDeletedIntent(android.content.Context, android.accounts.AccountManager, android.accounts.Account)}
   * for contents of the intent.
   */
  public static final long SYNC_ACCOUNT_DELETED_INTENT_VERSION = 1;

  /**
   * Bug 790931: this signing-level permission protects broadcast intents that
   * should be received only by Firefox versions sharing the same Android Sync
   * Account type.
   */
//  public static final String PER_ACCOUNT_TYPE_PERMISSION = "org.mozilla.firefox_sync.permission.PER_ACCOUNT_TYPE";
  public static final String PER_ACCOUNT_TYPE_PERMISSION = "org.mozilla.firefox.permission.PER_ACCOUNT_TYPE";
 
 
  public static final String DEFAULT_AUTH_SERVER = "https://auth.services.mozilla.com/";

  // Used for BackoffHandler storage for Sync 1.1's SyncAdapter.
  public static final String BACKOFF_PREF_SUFFIX_11 = "sync";
  
   public static final int SIGN_IN_SUCCESS = 1;
  public static final int SIGN_IN_FAILURE = 2;
  public static final int SIGN_OUT = 3;
  
  
  public static final String SIGN_OUT_ACTION = "android.intent.action.MOZILLA_FIREFOX_ACCOUNT_SIGNOUT";
  public static final String SIGN_IN_ACTION = "android.intent.action.MOZILLA_FIREFOX_ACCOUNT_SIGN_IN";
}