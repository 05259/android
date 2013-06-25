/* ownCloud Android client application
 *   Copyright (C) 2012  Bartek Przybylski
 *   Copyright (C) 2012-2013 ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.owncloud.android.files;

import android.accounts.Account;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.State;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Images.Media;

import com.owncloud.android.AccountUtils;
import com.owncloud.android.Log_OC;
import com.owncloud.android.files.services.FileUploader;
import com.owncloud.android.utils.FileStorageUtils;

public class InstantUploadBroadcastReceiver extends BroadcastReceiver {

    private static String TAG = "PhotoTakenBroadcastReceiver";
    private static final String[] CONTENT_PROJECTION = { Media.DATA, Media.DISPLAY_NAME, Media.MIME_TYPE, Media.SIZE };
    private static String NEW_PHOTO_ACTION = "com.android.camera.NEW_PICTURE";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log_OC.d(TAG, "Received: " + intent.getAction());
        if (intent.getAction().equals(android.net.ConnectivityManager.CONNECTIVITY_ACTION)) {
            handleConnectivityAction(context, intent);
        } else if (intent.getAction().equals(NEW_PHOTO_ACTION)) {
            handleNewPhotoAction(context, intent);
        } else {
            Log_OC.e(TAG, "Incorrect intent sent: " + intent.getAction());
        }
    }

    private void handleNewPhotoAction(Context context, Intent intent) {
        if (!instantUploadEnabled(context)) {
            Log_OC.d(TAG, "Instant upload disabled, abording uploading");
            return;
        }

        Account account = AccountUtils.getCurrentOwnCloudAccount(context);
        if (account == null) {
            Log_OC.w(TAG, "No owncloud account found for instant upload, aborting");
            return;
        }

        Cursor c = context.getContentResolver().query(intent.getData(), CONTENT_PROJECTION, null, null, null);

        if (!c.moveToFirst()) {
            Log_OC.e(TAG, "Couldn't resolve given uri: " + intent.getDataString());
            return;
        }

        String file_path = c.getString(c.getColumnIndex(Media.DATA));
        String file_name = c.getString(c.getColumnIndex(Media.DISPLAY_NAME));
        String mime_type = c.getString(c.getColumnIndex(Media.MIME_TYPE));

        c.close();
        Log_OC.e(TAG, file_path + "");


        // register for upload finishe message
        // there is a litte problem with android API, we can register for
        // particular
        // intent in registerReceiver but we cannot unregister from precise
        // intent
        // we can unregister from entire listenings but thats suck a bit.
        // On the other hand this might be only for dynamicly registered
        // broadcast receivers, needs investigation.
        IntentFilter filter = new IntentFilter(FileUploader.ACTION_UPLOAD_FINISHED);
        context.getApplicationContext().registerReceiver(this, filter);

        Intent i = new Intent(context, FileUploader.class);
        i.setAction(FileUploader.ACTION_ADD_UPLOAD);
        i.putExtra(FileUploader.EXTRA_ACCOUNT, account);
        i.putExtra(FileUploader.EXTRA_LOCAL_PATH, file_path);
        i.putExtra(FileUploader.EXTRA_REMOTE_PATH, FileStorageUtils.getInstantUploadFilePath(context, file_name));
        i.putExtra(FileUploader.EXTRA_UPLOAD_TYPE, FileUploader.UPLOAD_TYPE_SINGLE_FILE);
        i.putExtra(FileUploader.EXTRA_MIME_TYPE, mime_type);
        i.putExtra(FileUploader.EXTRA_INSTANT_UPLOAD, true);
        context.startService(i);

    }

    private void handleConnectivityAction(Context context, Intent intent) {

        if (!intent.hasExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY)
                && isOnline(context)
                && (!instantUploadViaWiFiOnly(context) || (instantUploadViaWiFiOnly(context) == isConnectedViaWiFi(context) == true))) {      
            
            // Restart Offline Uploads
            Log_OC.w(TAG, "Restart Offline Uploads");
            Intent i = new Intent(context, FileUploader.class);
            i.setAction(FileUploader.ACTION_RESUME_UPLOADS);
            context.startService(i);
        }

    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }

    public static boolean isConnectedViaWiFi(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm != null && cm.getActiveNetworkInfo() != null
                && cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI
                && cm.getActiveNetworkInfo().getState() == State.CONNECTED;
    }

    public static boolean instantUploadEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("instant_uploading", false);
    }

    public static boolean instantUploadViaWiFiOnly(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("instant_upload_on_wifi", false);
    }
}
