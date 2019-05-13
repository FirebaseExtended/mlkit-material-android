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
import androidx.core.content.ContextCompat;
import com.google.firebase.ml.md.java.camera.GraphicOverlay;
import com.google.firebase.ml.md.java.camera.GraphicOverlay.Graphic;
import com.google.firebase.ml.md.R;
import com.google.firebase.ml.md.java.camera.CameraReticleAnimator;

/**
 * A camera reticle that locates at the center of canvas to indicate the system is active but has
 * not recognized an object yet.
 */
class ObjectReticleGraphic extends Graphic {

  private final CameraReticleAnimator animator;

  private final Paint outerRingFillPaint;
  private final Paint outerRingStrokePaint;
  private final Paint innerRingStrokePaint;
  private final Paint ripplePaint;
  private final int outerRingFillRadius;
  private final int outerRingStrokeRadius;
  private final int innerRingStrokeRadius;
  private final int rippleSizeOffset;
  private final int rippleStrokeWidth;
  private final int rippleAlpha;

  ObjectReticleGraphic(GraphicOverlay overlay, CameraReticleAnimator animator) {
    super(overlay);
    this.animator = animator;

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

    innerRingStrokePaint = new Paint();
    innerRingStrokePaint.setStyle(Style.STROKE);
    innerRingStrokePaint.setStrokeWidth(
        resources.getDimensionPixelOffset(R.dimen.object_reticle_inner_ring_stroke_width));
    innerRingStrokePaint.setStrokeCap(Cap.ROUND);
    innerRingStrokePaint.setColor(ContextCompat.getColor(context, R.color.white));

    ripplePaint = new Paint();
    ripplePaint.setStyle(Style.STROKE);
    ripplePaint.setColor(ContextCompat.getColor(context, R.color.reticle_ripple));

    outerRingFillRadius =
        resources.getDimensionPixelOffset(R.dimen.object_reticle_outer_ring_fill_radius);
    outerRingStrokeRadius =
        resources.getDimensionPixelOffset(R.dimen.object_reticle_outer_ring_stroke_radius);
    innerRingStrokeRadius =
        resources.getDimensionPixelOffset(R.dimen.object_reticle_inner_ring_stroke_radius);
    rippleSizeOffset = resources.getDimensionPixelOffset(R.dimen.object_reticle_ripple_size_offset);
    rippleStrokeWidth =
        resources.getDimensionPixelOffset(R.dimen.object_reticle_ripple_stroke_width);
    rippleAlpha = ripplePaint.getAlpha();
  }

  @Override
  public void draw(Canvas canvas) {
    float cx = canvas.getWidth() / 2f;
    float cy = canvas.getHeight() / 2f;
    canvas.drawCircle(cx, cy, outerRingFillRadius, outerRingFillPaint);
    canvas.drawCircle(cx, cy, outerRingStrokeRadius, outerRingStrokePaint);
    canvas.drawCircle(cx, cy, innerRingStrokeRadius, innerRingStrokePaint);

    // Draws the ripple to simulate the breathing animation effect.
    ripplePaint.setAlpha((int) (rippleAlpha * animator.getRippleAlphaScale()));
    ripplePaint.setStrokeWidth(rippleStrokeWidth * animator.getRippleStrokeWidthScale());
    float radius = outerRingStrokeRadius + rippleSizeOffset * animator.getRippleSizeScale();
    canvas.drawCircle(cx, cy, radius, ripplePaint);
  }
}
