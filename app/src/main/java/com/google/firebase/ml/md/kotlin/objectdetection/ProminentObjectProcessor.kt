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

import android.graphics.RectF
import android.util.Log
import androidx.annotation.MainThread
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
import com.google.firebase.ml.md.kotlin.camera.WorkflowModel.WorkflowState
import com.google.firebase.ml.md.kotlin.camera.FrameProcessorBase
import com.google.firebase.ml.md.kotlin.settings.PreferenceUtils
import java.io.IOException
import java.util.ArrayList

/** A processor to run object detector in prominent object only mode.  */
class ProminentObjectProcessor(graphicOverlay: GraphicOverlay, private val workflowModel: WorkflowModel) :
    FrameProcessorBase<List<FirebaseVisionObject>>() {

    private val detector: FirebaseVisionObjectDetector
    private val confirmationController: ObjectConfirmationController = ObjectConfirmationController(graphicOverlay)
    private val cameraReticleAnimator: CameraReticleAnimator = CameraReticleAnimator(graphicOverlay)
    private val reticleOuterRingRadius: Int = graphicOverlay
            .resources
            .getDimensionPixelOffset(R.dimen.object_reticle_outer_ring_stroke_radius)

    init {

        val optionsBuilder = FirebaseVisionObjectDetectorOptions.Builder()
                .setDetectorMode(FirebaseVisionObjectDetectorOptions.STREAM_MODE)
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
            qualifiedObjects.addAll(objects
                    .filter { it.classificationCategory != FirebaseVisionObject.CATEGORY_UNKNOWN }
            )
            objects = qualifiedObjects
        }

        if (objects.isEmpty()) {
            confirmationController.reset()
            workflowModel.setWorkflowState(WorkflowState.DETECTING)
        } else {
            val objectIndex = 0
            val visionObject = objects[objectIndex]
            if (objectBoxOverlapsConfirmationReticle(graphicOverlay, visionObject)) {
                // User is confirming the object selection.
                confirmationController.confirming(visionObject.trackingId)
                workflowModel.confirmingObject(
                        DetectedObject(visionObject, objectIndex, image), confirmationController.progress
                )
            } else {
                // Object detected but user doesn't want to pick this one.
                confirmationController.reset()
                workflowModel.setWorkflowState(WorkflowState.DETECTED)
            }
        }

        graphicOverlay.clear()
        if (objects.isEmpty()) {
            graphicOverlay.add(ObjectReticleGraphic(graphicOverlay, cameraReticleAnimator))
            cameraReticleAnimator.start()
        } else {
            if (objectBoxOverlapsConfirmationReticle(graphicOverlay, objects[0])) {
                // User is confirming the object selection.
                cameraReticleAnimator.cancel()
                graphicOverlay.add(
                        ObjectGraphicInProminentMode(
                                graphicOverlay, objects[0], confirmationController
                        )
                )
                if (!confirmationController.isConfirmed &&
                    PreferenceUtils.isAutoSearchEnabled(graphicOverlay.context)) {
                    // Shows a loading indicator to visualize the confirming progress if in auto search mode.
                    graphicOverlay.add(ObjectConfirmationGraphic(graphicOverlay, confirmationController))
                }
            } else {
                // Object is detected but the confirmation reticle is moved off the object box, which
                // indicates user is not trying to pick this object.
                graphicOverlay.add(
                        ObjectGraphicInProminentMode(
                                graphicOverlay, objects[0], confirmationController
                        )
                )
                graphicOverlay.add(ObjectReticleGraphic(graphicOverlay, cameraReticleAnimator))
                cameraReticleAnimator.start()
            }
        }
        graphicOverlay.invalidate()
    }

    private fun objectBoxOverlapsConfirmationReticle(
        graphicOverlay: GraphicOverlay,
        visionObject: FirebaseVisionObject
    ): Boolean {
        val boxRect = graphicOverlay.translateRect(visionObject.boundingBox)
        val reticleCenterX = graphicOverlay.width / 2f
        val reticleCenterY = graphicOverlay.height / 2f
        val reticleRect = RectF(
                reticleCenterX - reticleOuterRingRadius,
                reticleCenterY - reticleOuterRingRadius,
                reticleCenterX + reticleOuterRingRadius,
                reticleCenterY + reticleOuterRingRadius
        )
        return reticleRect.intersect(boxRect)
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Object detection failed!", e)
    }

    companion object {
        private const val TAG = "ProminentObjProcessor"
    }
}
