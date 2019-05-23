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

package com.google.firebase.ml.md.java;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.common.collect.ImmutableList;
import com.google.firebase.ml.md.R;
import com.google.firebase.ml.md.java.productsearch.BottomSheetScrimView;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.objects.FirebaseVisionObject;
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetector;
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions;
import com.google.firebase.ml.md.java.objectdetection.DetectedObject;
import com.google.firebase.ml.md.java.objectdetection.StaticObjectDotView;
import com.google.firebase.ml.md.java.productsearch.PreviewCardAdapter;
import com.google.firebase.ml.md.java.productsearch.Product;
import com.google.firebase.ml.md.java.productsearch.ProductAdapter;
import com.google.firebase.ml.md.java.productsearch.SearchEngine;
import com.google.firebase.ml.md.java.productsearch.SearchEngine.SearchResultListener;
import com.google.firebase.ml.md.java.productsearch.SearchedObject;
import java.io.IOException;
import java.util.List;
import java.util.TreeMap;

/** Demonstrates the object detection and visual search workflow using static image. */
public class StaticObjectDetectionActivity extends AppCompatActivity
    implements View.OnClickListener, PreviewCardAdapter.CardItemListener, SearchResultListener {

  private static final String TAG = "StaticObjectActivity";
  private static final int MAX_IMAGE_DIMENSION = 1024;

  private final TreeMap<Integer, SearchedObject> searchedObjectMap = new TreeMap<>();

  private View loadingView;
  private Chip bottomPromptChip;
  private ImageView inputImageView;
  private RecyclerView previewCardCarousel;
  private ViewGroup dotViewContainer;

  private BottomSheetBehavior<View> bottomSheetBehavior;
  private BottomSheetScrimView bottomSheetScrimView;
  private TextView bottomSheetTitleView;
  private RecyclerView productRecyclerView;

  private Bitmap inputBitmap;
  private SearchedObject searchedObjectForBottomSheet;
  private int dotViewSize;
  private int detectedObjectNum = 0;
  private int currentSelectedObjectIndex = 0;

  private FirebaseVisionObjectDetector detector;
  private SearchEngine searchEngine;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    searchEngine = new SearchEngine(getApplicationContext());

    setContentView(R.layout.activity_static_object);

    loadingView = findViewById(R.id.loading_view);
    loadingView.setOnClickListener(this);

    bottomPromptChip = findViewById(R.id.bottom_prompt_chip);
    inputImageView = findViewById(R.id.input_image_view);

    previewCardCarousel = findViewById(R.id.card_recycler_view);
    previewCardCarousel.setHasFixedSize(true);
    previewCardCarousel.setLayoutManager(
        new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
    previewCardCarousel.addItemDecoration(new CardItemDecoration(getResources()));

    dotViewContainer = findViewById(R.id.dot_view_container);
    dotViewSize = getResources().getDimensionPixelOffset(R.dimen.static_image_dot_view_size);

    setUpBottomSheet();

    findViewById(R.id.close_button).setOnClickListener(this);
    findViewById(R.id.photo_library_button).setOnClickListener(this);

    detector =
        FirebaseVision.getInstance()
            .getOnDeviceObjectDetector(
                new FirebaseVisionObjectDetectorOptions.Builder()
                    .setDetectorMode(FirebaseVisionObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .enableMultipleObjects()
                    .build());
    if (getIntent().getData() != null) {
      detectObjects(getIntent().getData());
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    try {
      detector.close();
    } catch (IOException e) {
      Log.e(TAG, "Failed to close the detector!", e);
    }
    searchEngine.shutdown();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == Utils.REQUEST_CODE_PHOTO_LIBRARY
        && resultCode == Activity.RESULT_OK
        && data != null
        && data.getData() != null) {
      detectObjects(data.getData());
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public void onBackPressed() {
    if (bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    } else {
      super.onBackPressed();
    }
  }

  @Override
  public void onClick(View view) {
    int id = view.getId();
    if (id == R.id.close_button) {
      onBackPressed();
    } else if (id == R.id.photo_library_button) {
      Utils.openImagePicker(this);
    } else if (id == R.id.bottom_sheet_scrim_view) {
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }
  }

  @Override
  public void onPreviewCardClicked(SearchedObject searchedObject) {
    showSearchResults(searchedObject);
  }

  private void showSearchResults(SearchedObject searchedObject) {
    searchedObjectForBottomSheet = searchedObject;
    List<Product> productList = searchedObject.getProductList();
    bottomSheetTitleView.setText(
        getResources()
            .getQuantityString(
                R.plurals.bottom_sheet_title, productList.size(), productList.size()));
    productRecyclerView.setAdapter(new ProductAdapter(productList));
    bottomSheetBehavior.setPeekHeight(((View) inputImageView.getParent()).getHeight() / 2);
    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
  }

  private void setUpBottomSheet() {
    bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));
    bottomSheetBehavior.setBottomSheetCallback(
        new BottomSheetBehavior.BottomSheetCallback() {
          @Override
          public void onStateChanged(@NonNull View bottomSheet, int newState) {
            Log.d(TAG, "Bottom sheet new state: " + newState);
            bottomSheetScrimView.setVisibility(
                newState == BottomSheetBehavior.STATE_HIDDEN ? View.GONE : View.VISIBLE);
          }

          @Override
          public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            if (Float.isNaN(slideOffset)) {
              return;
            }

            int collapsedStateHeight =
                Math.min(bottomSheetBehavior.getPeekHeight(), bottomSheet.getHeight());
            bottomSheetScrimView.updateWithThumbnailTranslate(
                searchedObjectForBottomSheet.getObjectThumbnail(),
                collapsedStateHeight,
                slideOffset,
                bottomSheet);
          }
        });
    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

    bottomSheetScrimView = findViewById(R.id.bottom_sheet_scrim_view);
    bottomSheetScrimView.setOnClickListener(this);

    bottomSheetTitleView = findViewById(R.id.bottom_sheet_title);
    productRecyclerView = findViewById(R.id.product_recycler_view);
    productRecyclerView.setHasFixedSize(true);
    productRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    productRecyclerView.setAdapter(new ProductAdapter(ImmutableList.of()));
  }

  private void detectObjects(Uri imageUri) {
    inputImageView.setImageDrawable(null);
    bottomPromptChip.setVisibility(View.GONE);
    previewCardCarousel.setAdapter(new PreviewCardAdapter(ImmutableList.of(), this));
    previewCardCarousel.clearOnScrollListeners();
    dotViewContainer.removeAllViews();
    currentSelectedObjectIndex = 0;

    try {
      inputBitmap = Utils.loadImage(this, imageUri, MAX_IMAGE_DIMENSION);
    } catch (IOException e) {
      Log.e(TAG, "Failed to load file: " + imageUri, e);
      showBottomPromptChip("Failed to load file!");
      return;
    }

    inputImageView.setImageBitmap(inputBitmap);
    loadingView.setVisibility(View.VISIBLE);
    FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(inputBitmap);
    detector
        .processImage(image)
        .addOnSuccessListener(objects -> onObjectsDetected(image, objects))
        .addOnFailureListener(e -> onObjectsDetected(image, ImmutableList.of()));
  }

  @MainThread
  private void onObjectsDetected(FirebaseVisionImage image, List<FirebaseVisionObject> objects) {
    detectedObjectNum = objects.size();
    Log.d(TAG, "Detected objects num: " + detectedObjectNum);
    if (detectedObjectNum == 0) {
      loadingView.setVisibility(View.GONE);
      showBottomPromptChip(getString(R.string.static_image_prompt_detected_no_results));
    } else {
      searchedObjectMap.clear();
      for (int i = 0; i < objects.size(); i++) {
        searchEngine.search(new DetectedObject(objects.get(i), i, image), /* listener= */ this);
      }
    }
  }

  @Override
  public void onSearchCompleted(DetectedObject object, List<Product> productList) {
    Log.d(TAG, "Search completed for object index: " + object.getObjectIndex());
    searchedObjectMap.put(
        object.getObjectIndex(), new SearchedObject(getResources(), object, productList));
    if (searchedObjectMap.size() < detectedObjectNum) {
      // Hold off showing the result until the search of all detected objects completes.
      return;
    }

    showBottomPromptChip(getString(R.string.static_image_prompt_detected_results));
    loadingView.setVisibility(View.GONE);
    previewCardCarousel.setAdapter(
        new PreviewCardAdapter(ImmutableList.copyOf(searchedObjectMap.values()), this));
    previewCardCarousel.addOnScrollListener(
        new RecyclerView.OnScrollListener() {
          @Override
          public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            Log.d(TAG, "New card scroll state: " + newState);
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
              for (int i = 0; i < recyclerView.getChildCount(); i++) {
                View childView = recyclerView.getChildAt(i);
                if (childView.getX() >= 0) {
                  int cardIndex = recyclerView.getChildAdapterPosition(childView);
                  if (cardIndex != currentSelectedObjectIndex) {
                    selectNewObject(cardIndex);
                  }
                  break;
                }
              }
            }
          }
        });

    for (SearchedObject searchedObject : searchedObjectMap.values()) {
      StaticObjectDotView dotView = createDotView(searchedObject);
      dotView.setOnClickListener(
          v -> {
            if (searchedObject.getObjectIndex() == currentSelectedObjectIndex) {
              showSearchResults(searchedObject);
            } else {
              selectNewObject(searchedObject.getObjectIndex());
              showSearchResults(searchedObject);
              previewCardCarousel.smoothScrollToPosition(searchedObject.getObjectIndex());
            }
          });

      dotViewContainer.addView(dotView);
      AnimatorSet animatorSet =
          ((AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.static_image_dot_enter));
      animatorSet.setTarget(dotView);
      animatorSet.start();
    }
  }

  private StaticObjectDotView createDotView(SearchedObject searchedObject) {
    float viewCoordinateScale;
    float horizontalGap;
    float verticalGap;
    float inputImageViewRatio = (float) inputImageView.getWidth() / inputImageView.getHeight();
    float inputBitmapRatio = (float) inputBitmap.getWidth() / inputBitmap.getHeight();
    if (inputBitmapRatio <= inputImageViewRatio) { // Image content fills height
      viewCoordinateScale = (float) inputImageView.getHeight() / inputBitmap.getHeight();
      horizontalGap =
          (inputImageView.getWidth() - inputBitmap.getWidth() * viewCoordinateScale) / 2;
      verticalGap = 0;
    } else { // Image content fills width
      viewCoordinateScale = (float) inputImageView.getWidth() / inputBitmap.getWidth();
      horizontalGap = 0;
      verticalGap =
          (inputImageView.getHeight() - inputBitmap.getHeight() * viewCoordinateScale) / 2;
    }

    Rect boundingBox = searchedObject.getBoundingBox();
    RectF boxInViewCoordinate =
        new RectF(
            boundingBox.left * viewCoordinateScale + horizontalGap,
            boundingBox.top * viewCoordinateScale + verticalGap,
            boundingBox.right * viewCoordinateScale + horizontalGap,
            boundingBox.bottom * viewCoordinateScale + verticalGap);
    boolean initialSelected = (searchedObject.getObjectIndex() == 0);
    StaticObjectDotView dotView = new StaticObjectDotView(this, initialSelected);
    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(dotViewSize, dotViewSize);
    PointF dotCenter =
        new PointF(
            (boxInViewCoordinate.right + boxInViewCoordinate.left) / 2,
            (boxInViewCoordinate.bottom + boxInViewCoordinate.top) / 2);
    layoutParams.setMargins(
        (int) (dotCenter.x - dotViewSize / 2f), (int) (dotCenter.y - dotViewSize / 2f), 0, 0);
    dotView.setLayoutParams(layoutParams);
    return dotView;
  }

  private void selectNewObject(int objectIndex) {
    StaticObjectDotView dotViewToDeselect =
        (StaticObjectDotView) dotViewContainer.getChildAt(currentSelectedObjectIndex);
    dotViewToDeselect.playAnimationWithSelectedState(false);

    currentSelectedObjectIndex = objectIndex;

    StaticObjectDotView selectedDotView =
        (StaticObjectDotView) dotViewContainer.getChildAt(currentSelectedObjectIndex);
    selectedDotView.playAnimationWithSelectedState(true);
  }

  private void showBottomPromptChip(String message) {
    bottomPromptChip.setVisibility(View.VISIBLE);
    bottomPromptChip.setText(message);
  }

  private static class CardItemDecoration extends RecyclerView.ItemDecoration {

    private final int cardSpacing;

    private CardItemDecoration(Resources resources) {
      cardSpacing = resources.getDimensionPixelOffset(R.dimen.preview_card_spacing);
    }

    @Override
    public void getItemOffsets(
        @NonNull Rect outRect,
        @NonNull View view,
        @NonNull RecyclerView parent,
        @NonNull RecyclerView.State state) {
      int adapterPosition = parent.getChildAdapterPosition(view);
      outRect.left = adapterPosition == 0 ? cardSpacing * 2 : cardSpacing;
      if (parent.getAdapter() != null
          && adapterPosition == parent.getAdapter().getItemCount() - 1) {
        outRect.right = cardSpacing;
      }
    }
  }
}
