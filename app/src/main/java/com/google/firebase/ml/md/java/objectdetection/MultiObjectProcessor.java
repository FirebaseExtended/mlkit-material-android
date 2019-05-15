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

import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Log;
import androidx.annotation.MainThread;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.objects.FirebaseVisionObject;
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetector;
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions;
import com.google.firebase.ml.md.java.camera.CameraReticleAnimator;
import com.google.firebase.ml.md.java.camera.GraphicOverlay;
import com.google.firebase.ml.md.R;
import com.google.firebase.ml.md.java.camera.WorkflowModel;
import com.google.firebase.ml.md.java.camera.FrameProcessorBase;
import com.google.firebase.ml.md.java.settings.PreferenceUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/** A processor to run object detector in multi-objects mode. */
public class MultiObjectProcessor extends FrameProcessorBase<List<FirebaseVisionObject>> {

  private static final String TAG = "MultiObjectProcessor";

  private final WorkflowModel workflowModel;
  private final ObjectConfirmationController confirmationController;
  private final CameraReticleAnimator cameraReticleAnimator;
  private final int objectSelectionDistanceThreshold;
  private final FirebaseVisionObjectDetector detector;
  // Each new tracked object plays appearing animation exactly once.
  private final Map<Integer, ObjectDotAnimator> objectDotAnimatorMap = new HashMap<>();

  public MultiObjectProcessor(GraphicOverlay graphicOverlay, WorkflowModel workflowModel) {
    this.workflowModel = workflowModel;
    this.confirmationController = new ObjectConfirmationController(graphicOverlay);
    this.cameraReticleAnimator = new CameraReticleAnimator(graphicOverlay);
    this.objectSelectionDistanceThreshold =
        graphicOverlay
            .getResources()
            .getDimensionPixelOffset(R.dimen.object_selection_distance_threshold);

    FirebaseVisionObjectDetectorOptions.Builder optionsBuilder =
        new FirebaseVisionObjectDetectorOptions.Builder()
            .setDetectorMode(FirebaseVisionObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects();
    if (PreferenceUtils.isClassificationEnabled(graphicOverlay.getContext())) {
      optionsBuilder.enableClassification();
    }
    this.detector = FirebaseVision.getInstance().getOnDeviceObjectDetector(optionsBuilder.build());
  }

  @Override
  public void stop() {
    try {
      detector.close();
    } catch (IOException e) {
      Log.e(TAG, "Failed to close object detector!", e);
    }
  }

  @Override
  protected Task<List<FirebaseVisionObject>> detectInImage(FirebaseVisionImage image) {
    return detector.processImage(image);
  }

  @MainThread
  @Override
  protected void onSuccess(
      FirebaseVisionImage image,
      List<FirebaseVisionObject> objects,
      GraphicOverlay graphicOverlay) {
    if (!workflowModel.isCameraLive()) {
      return;
    }

    if (PreferenceUtils.isClassificationEnabled(graphicOverlay.getContext())) {
      List<FirebaseVisionObject> qualifiedObjects = new ArrayList<>();
      for (FirebaseVisionObject object : objects) {
        if (object.getClassificationCategory() != FirebaseVisionObject.CATEGORY_UNKNOWN) {
          qualifiedObjects.add(object);
        }
      }
      objects = qualifiedObjects;
    }

    removeAnimatorsFromUntrackedObjects(objects);

    graphicOverlay.clear();

    DetectedObject selectedObject = null;
    for (int i = 0; i < objects.size(); i++) {
      FirebaseVisionObject object = objects.get(i);
      if (selectedObject == null && shouldSelectObject(graphicOverlay, object)) {
        selectedObject = new DetectedObject(object, i, image);
        // Starts the object confirmation once an object is regarded as selected.
        confirmationController.confirming(object.getTrackingId());
        graphicOverlay.add(new ObjectConfirmationGraphic(graphicOverlay, confirmationController));

        graphicOverlay.add(
            new ObjectGraphicInMultiMode(
                graphicOverlay, selectedObject, confirmationController));
      } else {
        if (confirmationController.isConfirmed()) {
          // Don't render other objects when an object is in confirmed state.
          continue;
        }

        ObjectDotAnimator objectDotAnimator = objectDotAnimatorMap.get(object.getTrackingId());
        if (objectDotAnimator == null) {
          objectDotAnimator = new ObjectDotAnimator(graphicOverlay);
          objectDotAnimator.start();
          objectDotAnimatorMap.put(object.getTrackingId(), objectDotAnimator);
        }
        graphicOverlay.add(
            new ObjectDotGraphic(
                graphicOverlay, new DetectedObject(object, i, image), objectDotAnimator));
      }
    }

    if (selectedObject == null) {
      confirmationController.reset();
      graphicOverlay.add(new ObjectReticleGraphic(graphicOverlay, cameraReticleAnimator));
      cameraReticleAnimator.start();
    } else {
      cameraReticleAnimator.cancel();
    }

    graphicOverlay.invalidate();

    if (selectedObject != null) {
      workflowModel.confirmingObject(selectedObject, confirmationController.getProgress());
    } else {
      workflowModel.setWorkflowState(
          objects.isEmpty()
              ? WorkflowModel.WorkflowState.DETECTING
              : WorkflowModel.WorkflowState.DETECTED);
    }
  }

  private void removeAnimatorsFromUntrackedObjects(List<FirebaseVisionObject> detectedObjects) {
    List<Integer> trackingIds = new ArrayList<>();
    for (FirebaseVisionObject object : detectedObjects) {
      trackingIds.add(object.getTrackingId());
    }
    // Stop and remove animators from the objects that have lost tracking.
    List<Integer> removedTrackingIds = new ArrayList<>();
    for (Entry<Integer, ObjectDotAnimator> entry : objectDotAnimatorMap.entrySet()) {
      if (!trackingIds.contains(entry.getKey())) {
        entry.getValue().cancel();
        removedTrackingIds.add(entry.getKey());
      }
    }
    objectDotAnimatorMap.keySet().removeAll(removedTrackingIds);
  }

  private boolean shouldSelectObject(GraphicOverlay graphicOverlay, FirebaseVisionObject object) {
    // Considers an object as selected when the camera reticle touches the object dot.
    RectF box = graphicOverlay.translateRect(object.getBoundingBox());
    PointF objectCenter = new PointF((box.left + box.right) / 2f, (box.top + box.bottom) / 2f);
    PointF reticleCenter =
        new PointF(graphicOverlay.getWidth() / 2f, graphicOverlay.getHeight() / 2f);
    double distance =
        Math.hypot(objectCenter.x - reticleCenter.x, objectCenter.y - reticleCenter.y);
    return distance < objectSelectionDistanceThreshold;
  }

  @Override
  protected void onFailure(Exception e) {
    Log.e(TAG, "Object detection failed!", e);
  }
}
