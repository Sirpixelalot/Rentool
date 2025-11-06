package com.renpytool;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class RpaFileHelper {

    private static final String TAG = "RpaFileHelper";

    /**
     * Get a file path from a Uri. This will get the file path using the URI
     * authority and path.
     */
    public static String getPath(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }

        // Try direct file path first
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        // For content URIs, we need to handle them differently
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            // Check if it's a document URI
            if (DocumentsContract.isDocumentUri(context, uri)) {
                return getPathFromDocumentUri(context, uri);
            } else {
                return getPathFromContentUri(context, uri);
            }
        }

        return null;
    }

    private static String getPathFromDocumentUri(Context context, Uri uri) {
        try {
            String documentId = DocumentsContract.getDocumentId(uri);

            // Check if it's from external storage
            if (isExternalStorageDocument(uri)) {
                final String[] split = documentId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return android.os.Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            }
            // Check if it's from downloads provider
            else if (isDownloadsDocument(uri)) {
                return getPathFromDownloadsDocument(context, documentId);
            }
            // Check if it's from media provider
            else if (isMediaDocument(uri)) {
                return getPathFromMediaDocument(context, documentId);
            }

            // Fallback: try to get path from content URI
            return getPathFromContentUri(context, uri);

        } catch (Exception e) {
            Log.e(TAG, "Error getting path from document URI", e);
            // Fallback: copy to temp file
            return copyUriToTempFile(context, uri);
        }
    }

    private static String getPathFromContentUri(Context context, Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting path from content URI", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Fallback: copy to temp file
        return copyUriToTempFile(context, uri);
    }

    private static String getPathFromDownloadsDocument(Context context, String documentId) {
        try {
            if (documentId.startsWith("raw:")) {
                return documentId.replaceFirst("raw:", "");
            }

            // Try to get the file from Downloads directory
            final File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);

            // If documentId is a number, it's likely a MediaStore ID
            if (documentId.matches("\\d+")) {
                Uri contentUri = Uri.parse("content://downloads/public_downloads");
                contentUri = Uri.withAppendedPath(contentUri, documentId);
                return getPathFromContentUri(context, contentUri);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting path from downloads document", e);
        }
        return null;
    }

    private static String getPathFromMediaDocument(Context context, String documentId) {
        try {
            final String[] split = documentId.split(":");
            final String type = split[0];
            final String id = split[1];

            Uri contentUri = null;
            if ("image".equals(type)) {
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else if ("video".equals(type)) {
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else if ("audio".equals(type)) {
                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }

            if (contentUri != null) {
                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{id};
                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting path from media document", e);
        }
        return null;
    }

    private static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int columnIndex = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting data column", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * Copy URI content to a temporary file and return the path
     */
    private static String copyUriToTempFile(Context context, Uri uri) {
        try {
            DocumentFile documentFile = DocumentFile.fromSingleUri(context, uri);
            if (documentFile == null) {
                return null;
            }

            String fileName = documentFile.getName();
            if (fileName == null) {
                // Get filename from URI
                Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            if (nameIndex >= 0) {
                                fileName = cursor.getString(nameIndex);
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }
                if (fileName == null) {
                    fileName = "temp_file";
                }
            }

            File tempDir = new File(context.getCacheDir(), "rpa_temp");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            File tempFile = new File(tempDir, fileName);

            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return null;
            }

            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            inputStream.close();
            outputStream.close();

            return tempFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "Error copying URI to temp file", e);
            return null;
        }
    }

    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}
