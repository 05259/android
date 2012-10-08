/* ownCloud Android client application
 *   Copyright (C) 2012  Bartek Przybylski
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
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

import java.io.File;

import com.owncloud.android.AccountUtils;
import com.owncloud.android.authenticator.AccountAuthenticator;
import com.owncloud.android.db.DbHandler;
import com.owncloud.android.files.services.FileUploader;

import android.accounts.Account;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.State;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.webkit.MimeTypeMap;

public class PhotoTakenBroadcastReceiver extends BroadcastReceiver {

    public static String INSTANT_UPLOAD_DIR = "/InstantUpload/";
    private static String TAG = "PhotoTakenBroadcastReceiver";
    private static final String[] CONTENT_PROJECTION = { Media.DATA, Media.DISPLAY_NAME, Media.MIME_TYPE, Media.SIZE };
    private static String NEW_PHOTO_ACTION = "com.android.camera.NEW_PICTURE";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean iu_enabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("instant_uploading", false);

        if (!iu_enabled) {
            Log.d(TAG, "Instant upload disabled, abording uploading");
            return;
        }
        
        if (intent.getAction().equals(android.net.ConnectivityManager.CONNECTIVITY_ACTION)) {
            handleConnectivityAction(context, intent);
        } else if (intent.getAction().equals(NEW_PHOTO_ACTION)) {
            handleNewPhotoAction(context, intent);
        } else {
            Log.e(TAG, "Incorrect intent sent: " + intent.getAction());
        }
    }

    private void handleNewPhotoAction(Context context, Intent intent) {
        Account account = AccountUtils.getCurrentOwnCloudAccount(context);
        if (account == null) {
            Log.w(TAG, "No owncloud account found for instant upload, aborting");
            return;
        }

        Cursor c = context.getContentResolver().query(intent.getData(), CONTENT_PROJECTION, null, null, null);
        
        if (!c.moveToFirst()) {
            Log.e(TAG, "Couldn't resolve given uri!");
            return;
        }

        boolean iu_via_wifi = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("instant_upload_on_wifi", false);
        boolean is_conn_via_wifi = false;        
        if (iu_via_wifi) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && cm.getActiveNetworkInfo() != null &&
                cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI &&
                cm.getActiveNetworkInfo().getState() == State.CONNECTED)
                is_conn_via_wifi = true;
        }

        
        String file_path = c.getString(c.getColumnIndex(Media.DATA));
        String file_name = c.getString(c.getColumnIndex(Media.DISPLAY_NAME));
        String mime_type = c.getString(c.getColumnIndex(Media.MIME_TYPE));
        //long file_size = c.getLong(c.getColumnIndex(Media.SIZE));

        c.close();
        
        if (!isOnline(context) || (iu_via_wifi && !is_conn_via_wifi)) {
            DbHandler db = new DbHandler(context);
            db.putFileForLater(file_path, account.name);
            db.close();
            return;
        }

        /*
        Intent upload_intent = new Intent(context, InstantUploadService.class);
        upload_intent.putExtra(InstantUploadService.KEY_ACCOUNT, account);
        upload_intent.putExtra(InstantUploadService.KEY_FILE_PATH, file_path);
        upload_intent.putExtra(InstantUploadService.KEY_DISPLAY_NAME, file_name);
        upload_intent.putExtra(InstantUploadService.KEY_FILE_SIZE, file_size);
        upload_intent.putExtra(InstantUploadService.KEY_MIME_TYPE, mime_type);
        
        context.startService(upload_intent);
        */
        
        Intent i = new Intent(context, FileUploader.class);
        i.putExtra(FileUploader.KEY_ACCOUNT, account);
        i.putExtra(FileUploader.KEY_LOCAL_FILE, file_path);
        i.putExtra(FileUploader.KEY_REMOTE_FILE, INSTANT_UPLOAD_DIR + "/" + file_name);
        i.putExtra(FileUploader.KEY_UPLOAD_TYPE, FileUploader.UPLOAD_SINGLE_FILE);
        i.putExtra(FileUploader.KEY_MIME_TYPE, mime_type);
        i.putExtra(FileUploader.KEY_INSTANT_UPLOAD, true);
        context.startService(i);
        
    }

    private void handleConnectivityAction(Context context, Intent intent) {
        if (!intent.hasExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY) ||
            isOnline(context)) {
            DbHandler db = new DbHandler(context);
            Cursor c = db.getAwaitingFiles();
            if (c.moveToFirst()) {
                do {
                    String account_name = c.getString(c.getColumnIndex("account"));
                    String file_path = c.getString(c.getColumnIndex("path"));
                    File f = new File(file_path);
                    if (f.exists()) {
                        //Intent upload_intent = new Intent(context, InstantUploadService.class);
                        Account account = new Account(account_name, AccountAuthenticator.ACCOUNT_TYPE);
                        
                        String mimeType = null;
                        try {
                            mimeType = MimeTypeMap.getSingleton()
                                    .getMimeTypeFromExtension(
                                            f.getName().substring(f.getName().lastIndexOf('.') + 1));
                        
                        } catch (IndexOutOfBoundsException e) {
                            Log.e(TAG, "Trying to find out MIME type of a file without extension: " + f.getName());
                        }
                        if (mimeType == null)
                            mimeType = "application/octet-stream";

                        /*
                        upload_intent.putExtra(InstantUploadService.KEY_ACCOUNT, account);
                        upload_intent.putExtra(InstantUploadService.KEY_FILE_PATH, file_path);
                        upload_intent.putExtra(InstantUploadService.KEY_DISPLAY_NAME, f.getName());
                        upload_intent.putExtra(InstantUploadService.KEY_FILE_SIZE, f.length());
                        upload_intent.putExtra(InstantUploadService.KEY_MIME_TYPE, mimeType);
                        
                        context.startService(upload_intent);
                        */
                        
                        Intent i = new Intent(context, FileUploader.class);
                        i.putExtra(FileUploader.KEY_ACCOUNT, account);
                        i.putExtra(FileUploader.KEY_LOCAL_FILE, file_path);
                        i.putExtra(FileUploader.KEY_REMOTE_FILE, INSTANT_UPLOAD_DIR + f.getName());
                        i.putExtra(FileUploader.KEY_UPLOAD_TYPE, FileUploader.UPLOAD_SINGLE_FILE);
                        i.putExtra(FileUploader.KEY_INSTANT_UPLOAD, true);
                        context.startService(i);
                        
                    } else {
                        Log.w(TAG, "Instant upload file " + f.getName() + " dont exist anymore");
                    }
                } while(c.moveToNext());
                c.close();
            }
            db.clearFiles();
            db.close();
        }
        
    }

    private boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }
    
}
