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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.Rect;
import com.google.firebase.ml.md.java.camera.GraphicOverlay;
import com.google.firebase.ml.md.java.camera.GraphicOverlay.Graphic;
import com.google.firebase.ml.md.R;

/** A dot to indicate a detected object used by multiple objects detection mode. */
class ObjectDotGraphic extends Graphic {

  private final ObjectDotAnimator animator;
  private final Paint paint;
  private final PointF center;
  private final int dotRadius;
  private final int dotAlpha;

  ObjectDotGraphic(GraphicOverlay overlay, DetectedObject object, ObjectDotAnimator animator) {
    super(overlay);

    this.animator = animator;

    Rect box = object.getBoundingBox();
    center =
        new PointF(
            overlay.translateX((box.left + box.right) / 2f),
            overlay.translateY((box.top + box.bottom) / 2f));

    paint = new Paint();
    paint.setStyle(Style.FILL);
    paint.setColor(Color.WHITE);

    dotRadius = context.getResources().getDimensionPixelOffset(R.dimen.object_dot_radius);
    dotAlpha = paint.getAlpha();
  }

  @Override
  public void draw(Canvas canvas) {
    paint.setAlpha((int) (dotAlpha * animator.getAlphaScale()));
    canvas.drawCircle(center.x, center.y, dotRadius * animator.getRadiusScale(), paint);
  }
}
