/* ownCloud Android client application
 *   Copyright (C) 2011  Bartek Przybylski
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

package eu.alefzero.owncloud.syncadapter;

import java.io.IOException;
import java.io.ObjectInputStream.GetField;
import java.util.Vector;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;

import android.accounts.Account;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;
import eu.alefzero.owncloud.datamodel.FileDataStorageManager;
import eu.alefzero.owncloud.datamodel.OCFile;
import eu.alefzero.owncloud.files.services.FileDownloader;
import eu.alefzero.webdav.WebdavEntry;

/**
 * SyncAdapter implementation for syncing sample SyncAdapter contacts to the
 * platform ContactOperations provider.
 * 
 * @author Bartek Przybylski
 */
public class FileSyncAdapter extends AbstractOwnCloudSyncAdapter {

    private final static String TAG = "FileSyncAdapter"; 
    
    private long mCurrentSyncTime;
    
    public FileSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    @Override
    public synchronized void onPerformSync(Account account, Bundle extras,
            String authority, ContentProviderClient provider,
            SyncResult syncResult) {

        this.setAccount(account);
        this.setContentProvider(provider);
        this.setStorageManager(new FileDataStorageManager(account,
                getContentProvider()));
        
        Log.d(TAG, "syncing owncloud account " + account.name);

        sendStickyBroadcast(true, null);  // message to signal the start to the UI

        PropFindMethod query;
        try {
            mCurrentSyncTime = System.currentTimeMillis();
            query = new PropFindMethod(getUri().toString() + "/");
            getClient().executeMethod(query);
            MultiStatus resp = null;
            resp = query.getResponseBodyAsMultiStatus();

            if (resp.getResponses().length > 0) {
                WebdavEntry we = new WebdavEntry(resp.getResponses()[0], getUri().getPath());
                OCFile file = fillOCFile(we);
                file.setParentId(0);
                getStorageManager().saveFile(file);
                fetchData(getUri().toString(), syncResult, file.getFileId(), account);
            }
        } catch (OperationCanceledException e) {
            e.printStackTrace();
        } catch (AuthenticatorException e) {
            syncResult.stats.numAuthExceptions++;
            e.printStackTrace();
        } catch (IOException e) {
            syncResult.stats.numIoExceptions++;
            e.printStackTrace();
        } catch (DavException e) {
            syncResult.stats.numIoExceptions++;
            e.printStackTrace();
        } catch (Throwable t) {
            // TODO update syncResult
            Log.e(TAG, "problem while synchronizing owncloud account " + account.name, t);
            t.printStackTrace();
        }
        sendStickyBroadcast(false, null);        
    }

    private void fetchData(String uri, SyncResult syncResult, long parentId, Account account) {
        try {
            Log.v(TAG, "syncing: fetching " + uri);
            
            // remote request 
            PropFindMethod query = new PropFindMethod(uri);
            getClient().executeMethod(query);
            MultiStatus resp = null;
            
            resp = query.getResponseBodyAsMultiStatus();
            
            // insertion of updated files
            for (int i = 1; i < resp.getResponses().length; ++i) {
                WebdavEntry we = new WebdavEntry(resp.getResponses()[i], getUri().getPath());
                OCFile file = fillOCFile(we);
                file.setParentId(parentId);
                if (getStorageManager().getFileByPath(file.getRemotePath()) != null &&
                    getStorageManager().getFileByPath(file.getRemotePath()).keepInSync() &&
                    file.getModificationTimestamp() > getStorageManager().getFileByPath(file.getRemotePath())
                                                                         .getModificationTimestamp()) {
                    Intent intent = new Intent(this.getContext(), FileDownloader.class);
                    intent.putExtra(FileDownloader.EXTRA_ACCOUNT, getAccount());
                    intent.putExtra(FileDownloader.EXTRA_FILE_PATH, file.getURLDecodedRemotePath());
                    intent.putExtra(FileDownloader.EXTRA_REMOTE_PATH, file.getRemotePath());
                    intent.putExtra(FileDownloader.EXTRA_FILE_SIZE, file.getFileLength());
                    file.setKeepInSync(true);
                    getContext().startService(intent);
                }
                if (getStorageManager().getFileByPath(file.getRemotePath()) != null)
                    file.setKeepInSync(getStorageManager().getFileByPath(file.getRemotePath()).keepInSync());
                getStorageManager().saveFile(file);
                if (parentId == 0)
                    parentId = file.getFileId();
            }
            
            // removal of old files
            Vector<OCFile> files = getStorageManager().getDirectoryContent(
                    getStorageManager().getFileById(parentId));
            OCFile file;
            for (int i=0; i < files.size(); ) {
                file = files.get(i);
                if (file.getLastSyncDate() != mCurrentSyncTime && file.getLastSyncDate() != 0) {
                    getStorageManager().removeFile(file);
                    files.remove(i);
                } else {
                    i++;
                }
            }
            
            // synchronized folder -> notice to UI
            sendStickyBroadcast(true, getStorageManager().getFileById(parentId).getRemotePath());

            // recursive fetch
            for (OCFile newFile : files) {
                if (newFile.getMimetype().equals("DIR")) {
                    fetchData(getUri().toString() + newFile.getRemotePath(), syncResult, newFile.getFileId(), account);
                }
            }


        } catch (OperationCanceledException e) {
            e.printStackTrace();
        } catch (AuthenticatorException e) {
            syncResult.stats.numAuthExceptions++;
            e.printStackTrace();
        } catch (IOException e) {
            syncResult.stats.numIoExceptions++;
            e.printStackTrace();
        } catch (DavException e) {
            syncResult.stats.numIoExceptions++;
            e.printStackTrace();
        } catch (Throwable t) {
            // TODO update syncResult
            Log.e(TAG, "problem while synchronizing owncloud account " + account.name, t);
            t.printStackTrace();
        }
    }

    private OCFile fillOCFile(WebdavEntry we) {
        OCFile file = new OCFile(we.path());
        file.setCreationTimestamp(we.createTimestamp());
        file.setFileLength(we.contentLength());
        file.setMimetype(we.contentType());
        file.setModificationTimestamp(we.modifiedTimesamp());
        file.setLastSyncDate(mCurrentSyncTime);
        return file;
    }
    
    
    private void sendStickyBroadcast(boolean inProgress, String dirRemotePath) {
        Intent i = new Intent(FileSyncService.SYNC_MESSAGE);
        i.putExtra(FileSyncService.IN_PROGRESS, inProgress);
        i.putExtra(FileSyncService.ACCOUNT_NAME, getAccount().name);
        if (dirRemotePath != null) {
            i.putExtra(FileSyncService.SYNC_FOLDER_REMOTE_PATH, dirRemotePath);
        }
        getContext().sendStickyBroadcast(i);
    }

}
