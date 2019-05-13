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

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import androidx.core.view.animation.PathInterpolatorCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.google.firebase.ml.md.java.camera.GraphicOverlay;

/**
 * Custom animator for the object dot.
 */
class ObjectDotAnimator {

  // All these time constants are in millisecond unit.
  private static final long DURATION_DOT_SCALE_UP_MS = 217;
  private static final long DURATION_DOT_SCALE_DOWN_MS = 783;
  private static final long DURATION_DOT_FADE_IN_MS = 150;
  private static final long START_DELAY_DOT_SCALE_DOWN_MS = 217;

  private final AnimatorSet animatorSet;

  private float radiusScale = 0f;
  private float alphaScale = 0f;

  ObjectDotAnimator(final GraphicOverlay graphicOverlay) {
    ValueAnimator dotScaleUpAnimator =
        ValueAnimator.ofFloat(0f, 1.3f).setDuration(DURATION_DOT_SCALE_UP_MS);
    dotScaleUpAnimator.setInterpolator(new FastOutSlowInInterpolator());
    dotScaleUpAnimator.addUpdateListener(
        animation -> {
          radiusScale = (float) animation.getAnimatedValue();
          graphicOverlay.postInvalidate();
        });

    ValueAnimator dotScaleDownAnimator =
        ValueAnimator.ofFloat(1.3f, 1f).setDuration(DURATION_DOT_SCALE_DOWN_MS);
    dotScaleDownAnimator.setStartDelay(START_DELAY_DOT_SCALE_DOWN_MS);
    dotScaleDownAnimator.setInterpolator(PathInterpolatorCompat.create(0.4f, 0f, 0f, 1f));
    dotScaleDownAnimator.addUpdateListener(
        animation -> {
          radiusScale = (float) animation.getAnimatedValue();
          graphicOverlay.postInvalidate();
        });

    ValueAnimator dotFadeInAnimator =
        ValueAnimator.ofFloat(0f, 1f).setDuration(DURATION_DOT_FADE_IN_MS);
    dotFadeInAnimator.addUpdateListener(
        animation -> {
          alphaScale = (float) animation.getAnimatedValue();
          graphicOverlay.postInvalidate();
        });

    animatorSet = new AnimatorSet();
    animatorSet.playTogether(dotScaleUpAnimator, dotScaleDownAnimator, dotFadeInAnimator);
  }

  /** Returns the scale value of dot radius ranges in [0, 1]. */
  float getRadiusScale() {
    return radiusScale;
  }

  /** Returns the scale value of dot alpha ranges in [0, 1]. */
  float getAlphaScale() {
    return alphaScale;
  }

  void start() {
    if (!animatorSet.isRunning()) {
      animatorSet.start();
    }
  }

  void cancel() {
    animatorSet.cancel();
    radiusScale = 0f;
    alphaScale = 0f;
  }
}
