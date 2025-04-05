package de.dennisguse.opentracks.ui.markers;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.os.Environment;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.util.FileUtils;

public class MarkerUtils {

    private static final String TAG = FileUtils.class.getSimpleName();

    static final int ICON_ID = R.drawable.ic_marker_orange_pushpin_with_shadow;

    private static final String JPEG_EXTENSION = "jpeg";

    private MarkerUtils() {
    }

    public static Drawable getDefaultPhoto(@NonNull Context context) {
        return ContextCompat.getDrawable(context, ICON_ID);
    }

    /**
     * Sends a take picture request to the camera app.
     * The picture is then stored in the track's folder.
     *
     * @param context the context
     * @param trackId the track id
     */
    static Pair<Intent, Uri> createTakePictureIntent(Context context, Track.Id trackId) {
        File dir = FileUtils.getPhotoDir(context, trackId);

        String fileName = SimpleDateFormat.getDateTimeInstance().format(new Date());
        File file = new File(dir, FileUtils.buildUniqueFileName(dir, fileName, JPEG_EXTENSION));

        Uri photoUri = FileProvider.getUriForFile(context, FileUtils.FILEPROVIDER, file);
        Log.d(TAG, "Taking photo to URI: " + photoUri);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        return new Pair<>(intent, photoUri);
    }

    @VisibleForTesting(otherwise = 3)
    public static String getImageUrl(Context context, Track.Id trackId) {
        File dir = FileUtils.getPhotoDir(context, trackId);

        String fileName = SimpleDateFormat.getDateTimeInstance().format(new Date());
        File file = new File(dir, FileUtils.buildUniqueFileName(dir, fileName, JPEG_EXTENSION));

        return file.getAbsolutePath();
    }

    /**
     * Checks that there is a file inside track photo directory whose name is the same that uri file.
     * If there is a file inside photo directory whose name is the same that uri then returns File. Otherwise returns null.
     *
     * @param context the Context.
     * @param trackId the id of the Track.
     * @param uri     the uri to check.
     * @return File object or null.
     */
    public static File getPhotoFileIfExists(Context context, Track.Id trackId, Uri uri) {
        if (uri == null) {
            Log.w(TAG, "URI object is null.");
            return null;
        }

        String filename = uri.getLastPathSegment();
        if (filename == null) {
            Log.w(TAG, "External photo contains no filename.");
            return null;
        }

        File dir = FileUtils.getPhotoDir(context, trackId);
        File file = new File(dir, filename);
        if (!file.exists()) {
            return null;
        }

        return file;
    }

    @Nullable
    public static File buildInternalPhotoFile(Context context, Track.Id trackId, @NonNull Uri fileNameUri) {
        if (fileNameUri == null) {
            Log.w(TAG, "URI object is null.");
            return null;
        }

        String filename = fileNameUri.getLastPathSegment();
        if (filename == null) {
            Log.w(TAG, "External photo contains no filename.");
            return null;
        }

        File dir = FileUtils.getPhotoDir(context, trackId);
        return new File(dir, filename);
    }

    public static String sanitizeTrackId(Track.Id trackId) {
        if (trackId == null) {
            return "unknown"; // Use a default value for null
        }

        // Ensure only alphanumeric characters and avoid path traversal characters
        return  trackId.toString().replaceAll("[^a-zA-Z0-9]", "_");
    }

    public static File createImageFile(Context context, Track.Id trackId) throws IOException {
        // Sanitize the trackId to avoid path traversal
        String sanitizedTrackId = sanitizeTrackId(trackId);

        // Define the directory where the image will be saved (app-specific directory)
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null) {
            throw new IOException("Storage directory not available");
        }

        // Create a unique file name for the photo using the sanitized trackId
        String photoFileName = "photo_" + sanitizedTrackId + ".jpg";
        File imageFile = new File(storageDir, photoFileName);

        // Make sure the parent directory exists
        if (!imageFile.getParentFile().exists()) {
            imageFile.getParentFile().mkdirs(); // Create directories if needed
        }

        return imageFile;
    }
}
