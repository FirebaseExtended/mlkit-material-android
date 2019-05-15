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

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import androidx.core.content.ContextCompat;
import com.google.firebase.ml.md.java.camera.GraphicOverlay;
import com.google.firebase.ml.md.java.camera.GraphicOverlay.Graphic;
import com.google.firebase.ml.md.R;
import com.google.firebase.ml.md.java.settings.PreferenceUtils;

/**
 * Similar to the camera reticle but with additional progress ring to indicate an object is getting
 * confirmed for a follow up processing, e.g. product search.
 */
public class ObjectConfirmationGraphic extends Graphic {

  private final ObjectConfirmationController confirmationController;

  private final Paint outerRingFillPaint;
  private final Paint outerRingStrokePaint;
  private final Paint innerRingPaint;
  private final Paint progressRingStrokePaint;
  private final int outerRingFillRadius;
  private final int outerRingStrokeRadius;
  private final int innerRingStrokeRadius;

  ObjectConfirmationGraphic(
      GraphicOverlay overlay, ObjectConfirmationController confirmationController) {
    super(overlay);

    this.confirmationController = confirmationController;

    Resources resources = overlay.getResources();
    outerRingFillPaint = new Paint();
    outerRingFillPaint.setStyle(Style.FILL);
    outerRingFillPaint.setColor(
        ContextCompat.getColor(context, R.color.object_reticle_outer_ring_fill));

    outerRingStrokePaint = new Paint();
    outerRingStrokePaint.setStyle(Style.STROKE);
    outerRingStrokePaint.setStrokeWidth(
        resources.getDimensionPixelOffset(R.dimen.object_reticle_outer_ring_stroke_width));
    outerRingStrokePaint.setStrokeCap(Cap.ROUND);
    outerRingStrokePaint.setColor(
        ContextCompat.getColor(context, R.color.object_reticle_outer_ring_stroke));

    progressRingStrokePaint = new Paint();
    progressRingStrokePaint.setStyle(Style.STROKE);
    progressRingStrokePaint.setStrokeWidth(
        resources.getDimensionPixelOffset(R.dimen.object_reticle_outer_ring_stroke_width));
    progressRingStrokePaint.setStrokeCap(Cap.ROUND);
    progressRingStrokePaint.setColor(ContextCompat.getColor(context, R.color.white));

    if (PreferenceUtils.isMultipleObjectsMode(overlay.getContext())) {
      innerRingPaint = new Paint();
      innerRingPaint.setStyle(Style.FILL);
      innerRingPaint.setColor(ContextCompat.getColor(context, R.color.object_reticle_inner_ring));
    } else {
      innerRingPaint = new Paint();
      innerRingPaint.setStyle(Style.STROKE);
      innerRingPaint.setStrokeWidth(
          resources.getDimensionPixelOffset(R.dimen.object_reticle_inner_ring_stroke_width));
      innerRingPaint.setStrokeCap(Cap.ROUND);
      innerRingPaint.setColor(ContextCompat.getColor(context, R.color.white));
    }

    outerRingFillRadius =
        resources.getDimensionPixelOffset(R.dimen.object_reticle_outer_ring_fill_radius);
    outerRingStrokeRadius =
        resources.getDimensionPixelOffset(R.dimen.object_reticle_outer_ring_stroke_radius);
    innerRingStrokeRadius =
        resources.getDimensionPixelOffset(R.dimen.object_reticle_inner_ring_stroke_radius);
  }

  @Override
  public void draw(Canvas canvas) {
    float cx = canvas.getWidth() / 2f;
    float cy = canvas.getHeight() / 2f;
    canvas.drawCircle(cx, cy, outerRingFillRadius, outerRingFillPaint);
    canvas.drawCircle(cx, cy, outerRingStrokeRadius, outerRingStrokePaint);
    canvas.drawCircle(cx, cy, innerRingStrokeRadius, innerRingPaint);

    RectF progressRect =
        new RectF(
            cx - outerRingStrokeRadius,
            cy - outerRingStrokeRadius,
            cx + outerRingStrokeRadius,
            cy + outerRingStrokeRadius);
    float sweepAngle = confirmationController.getProgress() * 360;
    canvas.drawArc(
        progressRect,
        /* startAngle= */ 0,
        sweepAngle,
        /* useCenter= */ false,
        progressRingStrokePaint);
  }
}
