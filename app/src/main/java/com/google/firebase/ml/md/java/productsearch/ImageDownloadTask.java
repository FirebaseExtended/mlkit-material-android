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

package com.google.firebase.ml.md.java.productsearch;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import java.io.InputStream;
import java.net.URL;

/** Async task to download the image and then feed into the provided image view. */
class ImageDownloadTask extends AsyncTask<String, Void, Bitmap> {
  private static final String TAG = "ImageDownloadTask";

  private final ImageView imageView;
  private final int maxImageWidth;

  ImageDownloadTask(ImageView imageView, int maxImageWidth) {
    this.imageView = imageView;
    this.maxImageWidth = maxImageWidth;
  }

  @Override
  @Nullable
  protected Bitmap doInBackground(String... urls) {
    if (TextUtils.isEmpty(urls[0])) {
      return null;
    }

    Bitmap bitmap = null;
    try {
      InputStream inputStream = new URL(urls[0]).openStream();
      bitmap = BitmapFactory.decodeStream(inputStream);
      inputStream.close();
    } catch (Exception e) {
      Log.e(TAG, "Image download failed: " + urls[0]);
    }

    if (bitmap != null && bitmap.getWidth() > maxImageWidth) {
      int dstHeight = (int) ((float) maxImageWidth / bitmap.getWidth() * bitmap.getHeight());
      bitmap = Bitmap.createScaledBitmap(bitmap, maxImageWidth, dstHeight, /* filter= */ false);
    }
    return bitmap;
  }

  @Override
  protected void onPostExecute(@Nullable Bitmap result) {
    if (result != null) {
      imageView.setImageBitmap(result);
    }
  }
}
