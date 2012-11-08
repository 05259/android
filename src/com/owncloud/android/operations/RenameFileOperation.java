/* ownCloud Android client application
 *   Copyright (C) 2012 Bartek Przybylski
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

package com.owncloud.android.operations;

import java.io.File;
import java.io.IOException;

import org.apache.jackrabbit.webdav.client.methods.DavMethodBase;
//import org.apache.jackrabbit.webdav.client.methods.MoveMethod;

import android.util.Log;

import com.owncloud.android.datamodel.DataStorageManager;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.files.services.FileDownloader;
import com.owncloud.android.operations.RemoteOperationResult.ResultCode;

import eu.alefzero.webdav.WebdavClient;
import eu.alefzero.webdav.WebdavUtils;

/**
 * Remote operation performing the rename of a remote file (or folder?) in the ownCloud server.
 * 
 * @author David A. Velasco
 */
public class RenameFileOperation extends RemoteOperation {
    
    private static final String TAG = RemoveFileOperation.class.getSimpleName();

    private static final int RENAME_READ_TIMEOUT = 10000;
    private static final int RENAME_CONNECTION_TIMEOUT = 5000;
    

    private OCFile mFile;
    private String mNewName;
    private DataStorageManager mStorageManager;
    
    
    /**
     * Constructor
     * 
     * @param file                  OCFile instance describing the remote file or folder to rename
     * @param newName               New name to set as the name of file.
     * @param storageManager        Reference to the local database corresponding to the account where the file is contained. 
     */
    public RenameFileOperation(OCFile file, String newName, DataStorageManager storageManager) {
        mFile = file;
        mNewName = newName;
        mStorageManager = storageManager;
    }
  
    public OCFile getFile() {
        return mFile;
    }
    
    
    /**
     * Performs the rename operation.
     * 
     * @param   client      Client object to communicate with the remote ownCloud server.
     */
    @Override
    protected RemoteOperationResult run(WebdavClient client) {
        RemoteOperationResult result = null;
        
        LocalMoveMethod move = null;
        String newRemotePath = null;
        try {
            if (mNewName.equals(mFile.getFileName())) {
                return new RemoteOperationResult(ResultCode.OK);
            }
        
            String parent = (new File(mFile.getRemotePath())).getParent();
            parent = (parent.endsWith(OCFile.PATH_SEPARATOR)) ? parent : parent + OCFile.PATH_SEPARATOR; 
            newRemotePath =  parent + mNewName;
            
            // check if the new name is valid in the local file system
            if (!isValidNewName()) {
                return new RemoteOperationResult(ResultCode.INVALID_LOCAL_FILE_NAME);
            }
        
            // check if a file with the new name already exists
            if (client.existsFile(newRemotePath) ||                             // remote check could fail by network failure, or by indeterminate behavior of HEAD for folders ... 
                    mStorageManager.getFileByPath(newRemotePath) != null) {     // ... so local check is convenient
                return new RemoteOperationResult(ResultCode.INVALID_OVERWRITE);
            }
            move = new LocalMoveMethod( client.getBaseUri() + WebdavUtils.encodePath(mFile.getRemotePath()),
                                        client.getBaseUri() + WebdavUtils.encodePath(newRemotePath));
            int status = client.executeMethod(move, RENAME_READ_TIMEOUT, RENAME_CONNECTION_TIMEOUT);
            if (move.succeeded()) {

                // create new OCFile instance for the renamed file
                /*OCFile newFile = new OCFile(mStorageManager.getFileById(mFile.getParentId()).getRemotePath() + mNewName;   // TODO - NOT CREATE NEW OCFILE; ADD setFileName METHOD 
                OCFile oldFile = mFile;
                mFile = newFile; */
                mFile.setFileName(mNewName);
                
                // try to rename the local copy of the file
                if (mFile.isDown()) {
                    File f = new File(mFile.getStoragePath());
                    String newStoragePath = f.getParent() + mNewName;
                    if (f.renameTo(new File(newStoragePath))) {
                        mFile.setStoragePath(newStoragePath);
                    }
                    // else - NOTHING: the link to the local file is kept although the local name can't be updated
                    // TODO - study conditions when this could be a problem
                }
                
                //mStorageManager.removeFile(oldFile, false);
                mStorageManager.saveFile(mFile);
             
            /* 
             *} else if (mFile.isDirectory() && (status == 207 || status >= 500)) {
             *   // TODO 
             *   // if server fails in the rename of a folder, some children files could have been moved to a folder with the new name while some others
             *   // stayed in the old folder;
             *   //
             *   // easiest and heaviest solution is synchronizing the parent folder (or the full account);
             *   //
             *   // a better solution is synchronizing the folders with the old and new names;
             *}
             */
                
            }
            
            move.getResponseBodyAsString(); // exhaust response, although not interesting
            result = new RemoteOperationResult(move.succeeded(), status);
            Log.i(TAG, "Rename " + mFile.getRemotePath() + " to " + newRemotePath + ": " + result.getLogMessage());
            
        } catch (Exception e) {
            result = new RemoteOperationResult(e);
            Log.e(TAG, "Rename " + mFile.getRemotePath() + " to " + ((newRemotePath==null) ? mNewName : newRemotePath) + ": " + result.getLogMessage(), e);
            
        } finally {
            if (move != null)
                move.releaseConnection();
        }
        return result;
    }

    
    /**
     * Checks if the new name to set is valid in the file system 
     * 
     * The only way to be sure is trying to create a file with that name. It's made in the temporal directory
     * for downloads, out of any account, and then removed. 
     * 
     * IMPORTANT: The test must be made in the same file system where files are download. The internal storage
     * could be formatted with a different file system.
     * 
     * TODO move this method, and maybe FileDownload.get***Path(), to a class with utilities specific for the interactions with the file system
     * 
     * @return      'True' if a temporal file named with the name to set could be created in the file system where 
     *              local files are stored.
     */
    private boolean isValidNewName() {
        // check tricky names
        if (mNewName == null || mNewName.length() <= 0 || mNewName.contains(File.separator) || mNewName.contains("%")) { 
            return false;
        }
        // create a test file
        String tmpFolder = FileDownloader.getTemporalPath("");
        File testFile = new File(tmpFolder + mNewName);
        try {
            testFile.createNewFile();   // return value is ignored; it could be 'false' because the file already existed, that doesn't invalidate the name
        } catch (IOException e) {
            Log.i(TAG, "Test for validity of name " + mNewName + " in the file system failed");
            return false;
        }
        boolean result = (testFile.exists() && testFile.isFile());
        
        // cleaning ; result is ignored, since there is not much we could do in case of failure, but repeat and repeat...
        testFile.delete();
        
        return result;
    }



    // move operation - TODO: find out why org.apache.jackrabbit.webdav.client.methods.MoveMethod is not used instead �?
    private class LocalMoveMethod extends DavMethodBase {

        public LocalMoveMethod(String uri, String dest) {
            super(uri);
            addRequestHeader(new org.apache.commons.httpclient.Header("Destination", dest));
        }

        @Override
        public String getName() {
            return "MOVE";
        }

        @Override
        protected boolean isSuccess(int status) {
            return status == 201 || status == 204;
        }
            
    }
    

}
