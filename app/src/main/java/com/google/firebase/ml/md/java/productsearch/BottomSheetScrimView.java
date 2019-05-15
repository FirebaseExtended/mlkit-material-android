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

import static com.google.common.base.Preconditions.checkArgument;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import com.google.firebase.ml.md.R;

/** Draws the scrim of bottom sheet with object thumbnail highlighted. */
public class BottomSheetScrimView extends View {

  private static final float DOWN_PERCENT_TO_HIDE_THUMBNAIL = 0.42f;

  private final Paint scrimPaint;
  private final Paint thumbnailPaint;
  private final Paint boxPaint;
  private final int thumbnailHeight;
  private final int thumbnailMargin;
  private final int boxCornerRadius;

  private Bitmap thumbnailBitmap;
  private RectF thumbnailRect;
  private float downPercentInCollapsed;

  public BottomSheetScrimView(Context context, AttributeSet attrs) {
    super(context, attrs);

    Resources resources = context.getResources();
    scrimPaint = new Paint();
    scrimPaint.setColor(ContextCompat.getColor(context, R.color.dark));

    thumbnailPaint = new Paint();

    boxPaint = new Paint();
    boxPaint.setStyle(Paint.Style.STROKE);
    boxPaint.setStrokeWidth(
        resources.getDimensionPixelOffset(R.dimen.object_thumbnail_stroke_width));
    boxPaint.setColor(Color.WHITE);

    thumbnailHeight = resources.getDimensionPixelOffset(R.dimen.object_thumbnail_height);
    thumbnailMargin = resources.getDimensionPixelOffset(R.dimen.object_thumbnail_margin);
    boxCornerRadius = resources.getDimensionPixelOffset(R.dimen.bounding_box_corner_radius);
  }

  /**
   * Translates the object thumbnail up or down along with bottom sheet's sliding movement, with
   * keeping thumbnail size fixed.
   */
  public void updateWithThumbnailTranslate(
      Bitmap thumbnailBitmap, int collapsedStateHeight, float slideOffset, View bottomSheet) {
    this.thumbnailBitmap = thumbnailBitmap;

    float currentSheetHeight;
    if (slideOffset < 0) {
      downPercentInCollapsed = -slideOffset;
      currentSheetHeight = collapsedStateHeight * (1 + slideOffset);
    } else {
      downPercentInCollapsed = 0;
      currentSheetHeight =
          collapsedStateHeight + (bottomSheet.getHeight() - collapsedStateHeight) * slideOffset;
    }

    float thumbnailWidth =
        (float) thumbnailBitmap.getWidth() / thumbnailBitmap.getHeight() * thumbnailHeight;
    thumbnailRect = new RectF();
    thumbnailRect.left = thumbnailMargin;
    thumbnailRect.top = getHeight() - currentSheetHeight - thumbnailMargin - thumbnailHeight;
    thumbnailRect.right = thumbnailRect.left + thumbnailWidth;
    thumbnailRect.bottom = thumbnailRect.top + thumbnailHeight;

    invalidate();
  }

  /**
   * Translates the object thumbnail from original bounding box location to at where the bottom
   * sheet is settled as COLLAPSED state, with its size scales gradually.
   *
   * <p>It's only used by sliding the sheet up from hidden state to collapsed state.
   */
  public void updateWithThumbnailTranslateAndScale(
      Bitmap thumbnailBitmap, int collapsedStateHeight, float slideOffset, RectF srcThumbnailRect) {
    checkArgument(
        slideOffset <= 0,
        "Scale mode works only when the sheet is between hidden and collapsed states.");

    this.thumbnailBitmap = thumbnailBitmap;
    this.downPercentInCollapsed = 0;

    float dstX = thumbnailMargin;
    float dstY = getHeight() - collapsedStateHeight - thumbnailMargin - thumbnailHeight;
    float dstHeight = thumbnailHeight;
    float dstWidth = srcThumbnailRect.width() / srcThumbnailRect.height() * dstHeight;
    RectF dstRect = new RectF(dstX, dstY, dstX + dstWidth, dstY + dstHeight);

    float progressToCollapsedState = 1 + slideOffset;
    thumbnailRect = new RectF();
    thumbnailRect.left =
        srcThumbnailRect.left + (dstRect.left - srcThumbnailRect.left) * progressToCollapsedState;
    thumbnailRect.top =
        srcThumbnailRect.top + (dstRect.top - srcThumbnailRect.top) * progressToCollapsedState;
    thumbnailRect.right =
        srcThumbnailRect.right
            + (dstRect.right - srcThumbnailRect.right) * progressToCollapsedState;
    thumbnailRect.bottom =
        srcThumbnailRect.bottom
            + (dstRect.bottom - srcThumbnailRect.bottom) * progressToCollapsedState;

    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    // Draws the dark background.
    canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
    if (thumbnailBitmap != null && downPercentInCollapsed < DOWN_PERCENT_TO_HIDE_THUMBNAIL) {
      int alpha = (int) ((1 - (downPercentInCollapsed / DOWN_PERCENT_TO_HIDE_THUMBNAIL)) * 255);

      // Draws the object thumbnail.
      thumbnailPaint.setAlpha(alpha);
      canvas.drawBitmap(thumbnailBitmap, /* src= */ null, thumbnailRect, thumbnailPaint);

      // Draws the bounding box.
      boxPaint.setAlpha(alpha);
      canvas.drawRoundRect(thumbnailRect, boxCornerRadius, boxCornerRadius, boxPaint);
    }
  }
}
