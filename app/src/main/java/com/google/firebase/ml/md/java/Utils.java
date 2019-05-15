/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.ml.md.java;

import static androidx.core.content.ContextCompat.checkSelfPermission;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.hardware.Camera;
import android.net.Uri;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.exifinterface.media.ExifInterface;
import com.google.firebase.ml.md.java.camera.CameraSizePair;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Utility class to provide helper methods. */
public class Utils {

  /**
   * If the absolute difference between aspect ratios is less than this tolerance, they are
   * considered to be the same aspect ratio.
   */
  public static final float ASPECT_RATIO_TOLERANCE = 0.01f;

  static final int REQUEST_CODE_PHOTO_LIBRARY = 1;

  private static final String TAG = "Utils";

  static void requestRuntimePermissions(Activity activity) {
    List<String> allNeededPermissions = new ArrayList<>();
    for (String permission : getRequiredPermissions(activity)) {
      if (checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
        allNeededPermissions.add(permission);
      }
    }

    if (!allNeededPermissions.isEmpty()) {
      ActivityCompat.requestPermissions(
          activity, allNeededPermissions.toArray(new String[0]), /* requestCode= */ 0);
    }
  }

  static boolean allPermissionsGranted(Context context) {
    for (String permission : getRequiredPermissions(context)) {
      if (checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private static String[] getRequiredPermissions(Context context) {
    try {
      PackageInfo info =
          context
              .getPackageManager()
              .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
      String[] ps = info.requestedPermissions;
      return (ps != null && ps.length > 0) ? ps : new String[0];
    } catch (Exception e) {
      return new String[0];
    }
  }

  public static boolean isPortraitMode(Context context) {
    return context.getResources().getConfiguration().orientation
        == Configuration.ORIENTATION_PORTRAIT;
  }

  /**
   * Generates a list of acceptable preview sizes. Preview sizes are not acceptable if there is not
   * a corresponding picture size of the same aspect ratio. If there is a corresponding picture size
   * of the same aspect ratio, the picture size is paired up with the preview size.
   *
   * <p>This is necessary because even if we don't use still pictures, the still picture size must
   * be set to a size that is the same aspect ratio as the preview size we choose. Otherwise, the
   * preview images may be distorted on some devices.
   */
  public static List<CameraSizePair> generateValidPreviewSizeList(Camera camera) {
    Camera.Parameters parameters = camera.getParameters();
    List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
    List<Camera.Size> supportedPictureSizes = parameters.getSupportedPictureSizes();
    List<CameraSizePair> validPreviewSizes = new ArrayList<>();
    for (Camera.Size previewSize : supportedPreviewSizes) {
      float previewAspectRatio = (float) previewSize.width / (float) previewSize.height;

      // By looping through the picture sizes in order, we favor the higher resolutions.
      // We choose the highest resolution in order to support taking the full resolution
      // picture later.
      for (Camera.Size pictureSize : supportedPictureSizes) {
        float pictureAspectRatio = (float) pictureSize.width / (float) pictureSize.height;
        if (Math.abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
          validPreviewSizes.add(new CameraSizePair(previewSize, pictureSize));
          break;
        }
      }
    }

    // If there are no picture sizes with the same aspect ratio as any preview sizes, allow all of
    // the preview sizes and hope that the camera can handle it.  Probably unlikely, but we still
    // account for it.
    if (validPreviewSizes.size() == 0) {
      Log.w(TAG, "No preview sizes have a corresponding same-aspect-ratio picture size.");
      for (Camera.Size previewSize : supportedPreviewSizes) {
        // The null picture size will let us know that we shouldn't set a picture size.
        validPreviewSizes.add(new CameraSizePair(previewSize, null));
      }
    }

    return validPreviewSizes;
  }

  public static Bitmap getCornerRoundedBitmap(Bitmap srcBitmap, int cornerRadius) {
    Bitmap dstBitmap =
        Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(dstBitmap);
    Paint paint = new Paint();
    paint.setAntiAlias(true);
    RectF rectF = new RectF(0, 0, srcBitmap.getWidth(), srcBitmap.getHeight());
    canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);
    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    canvas.drawBitmap(srcBitmap, 0, 0, paint);
    return dstBitmap;
  }

  static void openImagePicker(Activity activity) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("image/*");
    activity.startActivityForResult(intent, REQUEST_CODE_PHOTO_LIBRARY);
  }

  static Bitmap loadImage(Context context, Uri imageUri, int maxImageDimension) throws IOException {
    InputStream inputStreamForSize = null;
    InputStream inputStreamForImage = null;
    try {
      inputStreamForSize = context.getContentResolver().openInputStream(imageUri);
      BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(inputStreamForSize, /* outPadding= */ null, opts);
      int inSampleSize =
          Math.max(opts.outWidth / maxImageDimension, opts.outHeight / maxImageDimension);

      opts = new BitmapFactory.Options();
      opts.inSampleSize = inSampleSize;
      inputStreamForImage = context.getContentResolver().openInputStream(imageUri);
      Bitmap decodedBitmap =
          BitmapFactory.decodeStream(inputStreamForImage, /* outPadding= */ null, opts);
      return maybeTransformBitmap(context.getContentResolver(), imageUri, decodedBitmap);

    } finally {
      if (inputStreamForSize != null) {
        inputStreamForSize.close();
      }
      if (inputStreamForImage != null) {
        inputStreamForImage.close();
      }
    }
  }

  private static Bitmap maybeTransformBitmap(ContentResolver resolver, Uri uri, Bitmap bitmap) {
    int orientation = getExifOrientationTag(resolver, uri);
    Matrix matrix = new Matrix();
    switch (orientation) {
      case ExifInterface.ORIENTATION_UNDEFINED:
      case ExifInterface.ORIENTATION_NORMAL:
        // Set the matrix to be null to skip the image transform.
        matrix = null;
        break;
      case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
        matrix = new Matrix();
        matrix.postScale(-1.0f, 1.0f);
        break;
      case ExifInterface.ORIENTATION_ROTATE_90:
        matrix.postRotate(90);
        break;
      case ExifInterface.ORIENTATION_TRANSPOSE:
        matrix.postRotate(90.0f);
        matrix.postScale(-1.0f, 1.0f);
        break;
      case ExifInterface.ORIENTATION_ROTATE_180:
        matrix.postRotate(180.0f);
        break;
      case ExifInterface.ORIENTATION_FLIP_VERTICAL:
        matrix.postScale(1.0f, -1.0f);
        break;
      case ExifInterface.ORIENTATION_ROTATE_270:
        matrix.postRotate(-90.0f);
        break;
      case ExifInterface.ORIENTATION_TRANSVERSE:
        matrix.postRotate(-90.0f);
        matrix.postScale(-1.0f, 1.0f);
        break;
      default:
        // Set the matrix to be null to skip the image transform.
        matrix = null;
        break;
    }

    if (matrix != null) {
      return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    } else {
      return bitmap;
    }
  }

  private static int getExifOrientationTag(ContentResolver resolver, Uri imageUri) {
    if (!ContentResolver.SCHEME_CONTENT.equals(imageUri.getScheme())
        && !ContentResolver.SCHEME_FILE.equals(imageUri.getScheme())) {
      return 0;
    }

    ExifInterface exif = null;
    try (InputStream inputStream = resolver.openInputStream(imageUri)) {
      if (inputStream != null) {
        exif = new ExifInterface(inputStream);
      }
    } catch (IOException e) {
      Log.e(TAG, "Failed to open file to read rotation meta data: " + imageUri, e);
    }

    return exif !=  null
        ? exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        : ExifInterface.ORIENTATION_UNDEFINED;
  }
}
