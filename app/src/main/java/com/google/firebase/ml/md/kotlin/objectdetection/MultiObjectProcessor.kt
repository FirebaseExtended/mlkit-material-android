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

package com.google.firebase.ml.md.kotlin.objectdetection

import android.graphics.PointF
import android.util.Log
import android.util.SparseArray
import androidx.annotation.MainThread
import androidx.core.util.forEach
import androidx.core.util.set
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.objects.FirebaseVisionObject
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetector
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions
import com.google.firebase.ml.md.kotlin.camera.CameraReticleAnimator
import com.google.firebase.ml.md.kotlin.camera.GraphicOverlay
import com.google.firebase.ml.md.R
import com.google.firebase.ml.md.kotlin.camera.WorkflowModel
import com.google.firebase.ml.md.kotlin.camera.FrameProcessorBase
import com.google.firebase.ml.md.kotlin.settings.PreferenceUtils
import java.io.IOException
import java.util.ArrayList
import kotlin.math.hypot

/** A processor to run object detector in multi-objects mode.  */
class MultiObjectProcessor(graphicOverlay: GraphicOverlay, private val workflowModel: WorkflowModel) :
    FrameProcessorBase<List<FirebaseVisionObject>>() {
    private val confirmationController: ObjectConfirmationController = ObjectConfirmationController(graphicOverlay)
    private val cameraReticleAnimator: CameraReticleAnimator = CameraReticleAnimator(graphicOverlay)
    private val objectSelectionDistanceThreshold: Int = graphicOverlay
            .resources
            .getDimensionPixelOffset(R.dimen.object_selection_distance_threshold)
    private val detector: FirebaseVisionObjectDetector
    // Each new tracked object plays appearing animation exactly once.
    private val objectDotAnimatorArray = SparseArray<ObjectDotAnimator>()

    init {
        val optionsBuilder = FirebaseVisionObjectDetectorOptions.Builder()
                .setDetectorMode(FirebaseVisionObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
        if (PreferenceUtils.isClassificationEnabled(graphicOverlay.context)) {
            optionsBuilder.enableClassification()
        }
        this.detector = FirebaseVision.getInstance().getOnDeviceObjectDetector(optionsBuilder.build())
    }

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close object detector!", e)
        }
    }

    override fun detectInImage(image: FirebaseVisionImage): Task<List<FirebaseVisionObject>> {
        return detector.processImage(image)
    }

    @MainThread
    override fun onSuccess(
        image: FirebaseVisionImage,
        results: List<FirebaseVisionObject>,
        graphicOverlay: GraphicOverlay
    ) {
        var objects = results
        if (!workflowModel.isCameraLive) {
            return
        }

        if (PreferenceUtils.isClassificationEnabled(graphicOverlay.context)) {
            val qualifiedObjects = ArrayList<FirebaseVisionObject>()
            for (result in objects) {
                if (result.classificationCategory != FirebaseVisionObject.CATEGORY_UNKNOWN) {
                    qualifiedObjects.add(result)
                }
            }
            objects = qualifiedObjects
        }

        removeAnimatorsFromUntrackedObjects(objects)

        graphicOverlay.clear()

        var selectedObject: DetectedObject? = null
        for (i in objects.indices) {
            val result = objects[i]
            if (selectedObject == null && shouldSelectObject(graphicOverlay, result)) {
                selectedObject = DetectedObject(result, i, image)
                // Starts the object confirmation once an object is regarded as selected.
                confirmationController.confirming(result.trackingId)
                graphicOverlay.add(ObjectConfirmationGraphic(graphicOverlay, confirmationController))

                graphicOverlay.add(
                        ObjectGraphicInMultiMode(
                                graphicOverlay, selectedObject, confirmationController))
            } else {
                if (confirmationController.isConfirmed) {
                    // Don't render other objects when an object is in confirmed state.
                    continue
                }

                val trackingId = result.trackingId ?: return
                val objectDotAnimator = objectDotAnimatorArray.get(trackingId) ?: let {
                    ObjectDotAnimator(graphicOverlay).apply {
                        start()
                        objectDotAnimatorArray[trackingId] = this
                    }
                }
                graphicOverlay.add(
                        ObjectDotGraphic(
                                graphicOverlay, DetectedObject(result, i, image), objectDotAnimator
                        )
                )
            }
        }

        if (selectedObject == null) {
            confirmationController.reset()
            graphicOverlay.add(ObjectReticleGraphic(graphicOverlay, cameraReticleAnimator))
            cameraReticleAnimator.start()
        } else {
            cameraReticleAnimator.cancel()
        }

        graphicOverlay.invalidate()

        if (selectedObject != null) {
            workflowModel.confirmingObject(selectedObject, confirmationController.progress)
        } else {
            workflowModel.setWorkflowState(
                    if (objects.isEmpty()) {
                        WorkflowModel.WorkflowState.DETECTING
                    } else {
                        WorkflowModel.WorkflowState.DETECTED
                    }
            )
        }
    }

    private fun removeAnimatorsFromUntrackedObjects(detectedObjects: List<FirebaseVisionObject>) {
        val trackingIds = detectedObjects.mapNotNull { it.trackingId }
        // Stop and remove animators from the objects that have lost tracking.
        val removedTrackingIds = ArrayList<Int>()
        objectDotAnimatorArray.forEach { key, value ->
            if (!trackingIds.contains(key)) {
                value.cancel()
                removedTrackingIds.add(key)
            }
        }
        removedTrackingIds.forEach {
            objectDotAnimatorArray.remove(it)
        }
    }

    private fun shouldSelectObject(graphicOverlay: GraphicOverlay, visionObject: FirebaseVisionObject): Boolean {
        // Considers an object as selected when the camera reticle touches the object dot.
        val box = graphicOverlay.translateRect(visionObject.boundingBox)
        val objectCenter = PointF((box.left + box.right) / 2f, (box.top + box.bottom) / 2f)
        val reticleCenter = PointF(graphicOverlay.width / 2f, graphicOverlay.height / 2f)
        val distance =
            hypot((objectCenter.x - reticleCenter.x).toDouble(), (objectCenter.y - reticleCenter.y).toDouble())
        return distance < objectSelectionDistanceThreshold
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Object detection failed!", e)
    }

    companion object {

        private const val TAG = "MultiObjectProcessor"
    }
}
