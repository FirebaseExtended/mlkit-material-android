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

package com.google.firebase.ml.md.java.objectdetection;

import android.os.CountDownTimer;
import androidx.annotation.Nullable;
import com.google.firebase.ml.md.java.camera.GraphicOverlay;
import com.google.firebase.ml.md.java.settings.PreferenceUtils;

/**
 * Controls the progress of object confirmation before performing additional operation on the
 * detected object.
 */
class ObjectConfirmationController {

  private final CountDownTimer countDownTimer;

  @Nullable
  private Integer objectId = null;
  private float progress = 0;

  /**
   * @param graphicOverlay Used to refresh camera overlay when the confirmation progress updates.
   */
  ObjectConfirmationController(GraphicOverlay graphicOverlay) {
    long confirmationTimeMs = PreferenceUtils.getConfirmationTimeMs(graphicOverlay.getContext());
    countDownTimer =
        new CountDownTimer(confirmationTimeMs, /* countDownInterval= */ 20) {
          @Override
          public void onTick(long millisUntilFinished) {
            progress = (float) (confirmationTimeMs - millisUntilFinished) / confirmationTimeMs;
            graphicOverlay.invalidate();
          }

          @Override
          public void onFinish() {
            progress = 1;
          }
        };
  }

  void confirming(Integer objectId) {
    if (objectId.equals(this.objectId)) {
      // Do nothing if it's already in confirming.
      return;
    }

    reset();
    this.objectId = objectId;
    countDownTimer.start();
  }

  boolean isConfirmed() {
    return Float.compare(progress, 1) == 0;
  }

  void reset() {
    countDownTimer.cancel();
    objectId = null;
    progress = 0;
  }

  /** Returns the confirmation progress described as a float value in the range of [0, 1]. */
  float getProgress() {
    return progress;
  }
}
