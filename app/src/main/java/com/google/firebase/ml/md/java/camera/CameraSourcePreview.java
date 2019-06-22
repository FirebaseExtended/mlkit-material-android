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

package com.google.firebase.ml.md.java.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.images.Size;
import com.google.firebase.ml.md.R;
import com.google.firebase.ml.md.java.Utils;
import java.io.IOException;

/** Preview the camera image in the screen. */
public class CameraSourcePreview extends FrameLayout {
  private static final String TAG = "CameraSourcePreview";

  private final SurfaceView surfaceView;
  private GraphicOverlay graphicOverlay;
  private boolean startRequested = false;
  private boolean surfaceAvailable = false;
  private CameraSource cameraSource;
  private Size cameraPreviewSize;

  public CameraSourcePreview(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);

    surfaceView = new SurfaceView(context);
    surfaceView.getHolder().addCallback(new SurfaceCallback());
    addView(surfaceView);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    graphicOverlay = findViewById(R.id.camera_preview_graphic_overlay);
  }

  public void start(CameraSource cameraSource) throws IOException {
    this.cameraSource = cameraSource;
    startRequested = true;
    startIfReady();
  }

  public void stop() {
    if (cameraSource != null) {
      cameraSource.stop();
      cameraSource = null;
      startRequested = false;
    }
  }

  private void startIfReady() throws IOException {
    if (startRequested && surfaceAvailable) {
      cameraSource.start(surfaceView.getHolder());
      requestLayout();

      if (graphicOverlay != null) {
        graphicOverlay.setCameraInfo(cameraSource);
        graphicOverlay.clear();
      }
      startRequested = false;
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int layoutWidth = right - left;
    int layoutHeight = bottom - top;

    if (cameraSource != null && cameraSource.getPreviewSize() != null) {
      cameraPreviewSize = cameraSource.getPreviewSize();
    }

    float previewSizeRatio = (float) layoutWidth / layoutHeight;
    if (cameraPreviewSize != null) {
      if (Utils.isPortraitMode(getContext())) {
        // Camera's natural orientation is landscape, so need to swap width and height.
        previewSizeRatio = (float) cameraPreviewSize.getHeight() / cameraPreviewSize.getWidth();
      } else {
        previewSizeRatio = (float) cameraPreviewSize.getWidth() / cameraPreviewSize.getHeight();
      }
    }

    // Match the width of the child view to its parent.
    int childWidth = layoutWidth;
    int childHeight = (int) (childWidth / previewSizeRatio);
    if (childHeight <= layoutHeight) {
      for (int i = 0; i < getChildCount(); ++i) {
        getChildAt(i).layout(0, 0, childWidth, childHeight);
      }
    } else {
      // When the child view is too tall to be fitted in its parent: If the child view is static
      // overlay view container (contains views such as bottom prompt chip), we apply the size of
      // the parent view to it. Otherwise, we offset the top/bottom position equally to position it
      // in the center of the parent.
      int excessLenInHalf = (childHeight - layoutHeight) / 2;
      for (int i = 0; i < getChildCount(); ++i) {
        View childView = getChildAt(i);
        if (childView.getId() == R.id.static_overlay_container) {
          childView.layout(0, 0, childWidth, layoutHeight);
        } else {
          childView.layout(0, -excessLenInHalf, childWidth, layoutHeight + excessLenInHalf);
        }
      }
    }

    try {
      startIfReady();
    } catch (IOException e) {
      Log.e(TAG, "Could not start camera source.", e);
    }
  }

  private class SurfaceCallback implements SurfaceHolder.Callback {
    @Override
    public void surfaceCreated(SurfaceHolder surface) {
      surfaceAvailable = true;
      try {
        startIfReady();
      } catch (IOException e) {
        Log.e(TAG, "Could not start camera source.", e);
      }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surface) {
      surfaceAvailable = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
  }
}
