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

import android.hardware.Camera;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.images.Size;

/**
 * Stores a preview size and a corresponding same-aspect-ratio picture size. To avoid distorted
 * preview images on some devices, the picture size must be set to a size that is the same aspect
 * ratio as the preview size or the preview may end up being distorted. If the picture size is null,
 * then there is no picture size with the same aspect ratio as the preview size.
 */
public class CameraSizePair {
  public final Size preview;
  @Nullable
  public final Size picture;

  public CameraSizePair(@NonNull Camera.Size previewSize, @Nullable Camera.Size pictureSize) {
    preview = new Size(previewSize.width, previewSize.height);
    picture = pictureSize != null ? new Size(pictureSize.width, pictureSize.height) : null;
  }

  public CameraSizePair(Size previewSize, @Nullable Size pictureSize) {
    preview = previewSize;
    picture = pictureSize;
  }
}
