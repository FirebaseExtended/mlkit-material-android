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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.google.firebase.ml.md.R;

/** Represents a detected object by drawing a circle dot at the center of object's bounding box. */
public class StaticObjectDotView extends View {

  private static final long DOT_SELECTION_ANIMATOR_DURATION_MS = 116;
  private static final long DOT_DESELECTION_ANIMATOR_DURATION_MS = 67;

  private final Paint paint;
  private final int unselectedDotRadius;
  private final int radiusOffsetRange;

  private float currentRadiusOffset;

  public StaticObjectDotView(Context context) {
    this(context, /* selected= */ false);
  }

  public StaticObjectDotView(Context context, boolean selected) {
    super(context);

    paint = new Paint();
    paint.setStyle(Paint.Style.FILL);

    unselectedDotRadius =
        context.getResources().getDimensionPixelOffset(R.dimen.static_image_dot_radius_unselected);
    int selectedDotRadius =
        context.getResources().getDimensionPixelOffset(R.dimen.static_image_dot_radius_selected);
    radiusOffsetRange = selectedDotRadius - unselectedDotRadius;

    currentRadiusOffset = selected ? radiusOffsetRange : 0;
  }

  public void playAnimationWithSelectedState(boolean selected) {
    ValueAnimator radiusOffsetAnimator;
    if (selected) {
      radiusOffsetAnimator =
          ValueAnimator.ofFloat(0f, radiusOffsetRange)
              .setDuration(DOT_SELECTION_ANIMATOR_DURATION_MS);
      radiusOffsetAnimator.setStartDelay(DOT_DESELECTION_ANIMATOR_DURATION_MS);
    } else {
      radiusOffsetAnimator =
          ValueAnimator.ofFloat(radiusOffsetRange, 0f)
              .setDuration(DOT_DESELECTION_ANIMATOR_DURATION_MS);
    }
    radiusOffsetAnimator.setInterpolator(new FastOutSlowInInterpolator());
    radiusOffsetAnimator.addUpdateListener(
        animation -> {
          currentRadiusOffset = (float) animation.getAnimatedValue();
          invalidate();
        });
    radiusOffsetAnimator.start();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    float cx = getWidth() / 2f;
    float cy = getHeight() / 2f;
    paint.setColor(Color.WHITE);
    canvas.drawCircle(cx, cy, unselectedDotRadius + currentRadiusOffset, paint);
  }
}
