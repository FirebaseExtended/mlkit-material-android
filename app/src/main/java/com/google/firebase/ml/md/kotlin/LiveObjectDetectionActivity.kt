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

package com.google.firebase.ml.md.kotlin

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.common.base.Objects
import com.google.firebase.ml.md.R
import com.google.firebase.ml.md.kotlin.camera.WorkflowModel
import com.google.firebase.ml.md.kotlin.camera.WorkflowModel.WorkflowState
import com.google.firebase.ml.md.kotlin.camera.CameraSource
import com.google.firebase.ml.md.kotlin.objectdetection.MultiObjectProcessor
import com.google.firebase.ml.md.kotlin.objectdetection.ProminentObjectProcessor
import com.google.firebase.ml.md.kotlin.productsearch.ProductAdapter
import com.google.firebase.ml.md.kotlin.productsearch.SearchEngine
import com.google.firebase.ml.md.kotlin.settings.PreferenceUtils
import com.google.firebase.ml.md.kotlin.settings.SettingsActivity
import kotlinx.android.synthetic.main.activity_live_object_kotlin.*
import kotlinx.android.synthetic.main.camera_preview_overlay_kotlin.*
import kotlinx.android.synthetic.main.product_bottom_sheet.*
import kotlinx.android.synthetic.main.top_action_bar_in_live_camera.*
import java.io.IOException

/** Demonstrates the object detection and visual search workflow using camera preview.  */
class LiveObjectDetectionActivity : AppCompatActivity(), OnClickListener {

    private var cameraSource: CameraSource? = null
    private var promptChipAnimator: AnimatorSet? = null
    private var searchButtonAnimator: AnimatorSet? = null
    private var workflowModel: WorkflowModel? = null
    private var currentWorkflowState: WorkflowState? = null
    private var searchEngine: SearchEngine? = null

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private var objectThumbnailForBottomSheet: Bitmap? = null
    private var slidingSheetUpFromHiddenState: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchEngine = SearchEngine(applicationContext)

        setContentView(R.layout.activity_live_object_kotlin)
        with(cameraPreviewGraphicOverlay){
            setOnClickListener(this@LiveObjectDetectionActivity)
            cameraSource = CameraSource(this)
        }
        promptChipAnimator =
            (AnimatorInflater.loadAnimator(this, R.animator.bottom_prompt_chip_enter) as AnimatorSet).apply {
                setTarget(bottomPromptChip)
            }
        productSearchButton.setOnClickListener(this@LiveObjectDetectionActivity)
        searchButtonAnimator =
            (AnimatorInflater.loadAnimator(this, R.animator.search_button_enter) as AnimatorSet).apply {
                setTarget(productSearchButton)
            }
        setUpBottomSheet()
        closeButton.setOnClickListener(this)
        flashButton.setOnClickListener(this@LiveObjectDetectionActivity)
        settingsButton.setOnClickListener(this@LiveObjectDetectionActivity)
        setUpWorkflowModel()
    }

    override fun onResume() {
        super.onResume()

        workflowModel?.markCameraFrozen()
        settingsButton.isEnabled = true
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        currentWorkflowState = WorkflowState.NOT_STARTED
        cameraSource?.setFrameProcessor(
                if (PreferenceUtils.isMultipleObjectsMode(this)) {
                    MultiObjectProcessor(cameraPreviewGraphicOverlay, workflowModel!!)
                } else {
                    ProminentObjectProcessor(cameraPreviewGraphicOverlay, workflowModel!!)
                }
        )
        workflowModel?.setWorkflowState(WorkflowState.DETECTING)
    }

    override fun onPause() {
        super.onPause()
        currentWorkflowState = WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
        cameraSource = null
        searchEngine?.shutdown()
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            super.onBackPressed()
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.productSearchButton -> {
                productSearchButton.isEnabled = false
                workflowModel?.onSearchButtonClicked()
            }
            R.id.bottomSheetScrimView -> bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_HIDDEN)
            R.id.closeButton -> onBackPressed()
            R.id.flashButton -> {
                cameraSource?.updateFlashMode(
                        if(flashButton.isSelected){
                            Camera.Parameters.FLASH_MODE_OFF
                        } else {
                            Camera.Parameters.FLASH_MODE_TORCH
                        }
                )
                flashButton.isSelected = !flashButton.isSelected
            }
            R.id.settingsButton -> {
                settingsButton.isEnabled = false
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
    }

    private fun startCameraPreview() {
        val cameraSource = this.cameraSource ?: return
        val workflowModel = this.workflowModel ?: return
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                cameraPreview.start(cameraSource)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start camera preview!", e)
                cameraSource.release()
                this.cameraSource = null
            }
        }
    }

    private fun stopCameraPreview() {
        if (workflowModel?.isCameraLive == true) {
            workflowModel!!.markCameraFrozen()
            flashButton.isSelected = false
            cameraPreview.stop()
        }
    }

    private fun setUpBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior?.setBottomSheetCallback(
                object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        Log.d(TAG, "Bottom sheet new state: $newState")
                        bottomSheetScrimView.visibility =
                            if (newState == BottomSheetBehavior.STATE_HIDDEN) View.GONE else View.VISIBLE
                        cameraPreviewGraphicOverlay.clear()

                        when (newState) {
                            BottomSheetBehavior.STATE_HIDDEN -> workflowModel?.setWorkflowState(WorkflowState.DETECTING)
                            BottomSheetBehavior.STATE_COLLAPSED,
                            BottomSheetBehavior.STATE_EXPANDED,
                            BottomSheetBehavior.STATE_HALF_EXPANDED -> slidingSheetUpFromHiddenState = false
                            BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
                            }
                        }
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        val searchedObject = workflowModel!!.searchedObject.value
                        if (searchedObject == null || java.lang.Float.isNaN(slideOffset)) {
                            return
                        }

                        val graphicOverlay = cameraPreviewGraphicOverlay ?: return
                        val bottomSheetBehavior = bottomSheetBehavior ?: return
                        val collapsedStateHeight = Math.min(bottomSheetBehavior.peekHeight, bottomSheet.height)
                        val bottomBitmap = objectThumbnailForBottomSheet ?: return
                        if (slidingSheetUpFromHiddenState) {
                            val thumbnailSrcRect = graphicOverlay.translateRect(searchedObject.boundingBox)
                            bottomSheetScrimView.updateWithThumbnailTranslateAndScale(
                                    bottomBitmap,
                                    collapsedStateHeight,
                                    slideOffset,
                                    thumbnailSrcRect)
                        } else {
                            bottomSheetScrimView.updateWithThumbnailTranslate(
                                bottomBitmap, collapsedStateHeight, slideOffset, bottomSheet)
                        }
                    }
                })

        bottomSheetScrimView.setOnClickListener(this@LiveObjectDetectionActivity)

        with(productRecyclerView) {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@LiveObjectDetectionActivity)
            adapter = ProductAdapter(listOf())
        }
    }

    private fun setUpWorkflowModel() {
        workflowModel = ViewModelProviders.of(this).get(WorkflowModel::class.java).apply {

            // Observes the workflow state changes, if happens, update the overlay view indicators and
            // camera preview state.
            workflowState.observe(this@LiveObjectDetectionActivity, Observer { workflowState ->
                if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                    return@Observer
                }
                currentWorkflowState = workflowState
                Log.d(TAG, "Current workflow state: ${workflowState.name}")

                if (PreferenceUtils.isAutoSearchEnabled(this@LiveObjectDetectionActivity)) {
                    stateChangeInAutoSearchMode(workflowState)
                } else {
                    stateChangeInManualSearchMode(workflowState)
                }
            })

            // Observes changes on the object to search, if happens, fire product search request.
            objectToSearch.observe(this@LiveObjectDetectionActivity, Observer { detectObject ->
                searchEngine!!.search(detectObject) { detectedObject, products ->
                    workflowModel?.onSearchCompleted(detectedObject, products)
                }
            })

            // Observes changes on the object that has search completed, if happens, show the bottom sheet
            // to present search result.
            searchedObject.observe(this@LiveObjectDetectionActivity, Observer { nullableSearchedObject ->
                val searchedObject = nullableSearchedObject ?: return@Observer
                val productList = searchedObject.productList
                objectThumbnailForBottomSheet = searchedObject.getObjectThumbnail()
                bottomSheetTitle.text = resources
                        .getQuantityString(
                                R.plurals.bottom_sheet_title, productList.size, productList.size)
                productRecyclerView.adapter = ProductAdapter(productList)
                slidingSheetUpFromHiddenState = true
                bottomSheetBehavior?.peekHeight =
                        cameraPreview.height.div(2) ?: BottomSheetBehavior.PEEK_HEIGHT_AUTO
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
            })
        }
    }

    private fun stateChangeInAutoSearchMode(workflowState: WorkflowState) {
        val wasPromptChipGone = bottomPromptChip.visibility == View.GONE

        productSearchButton.visibility = View.GONE
        searchProgressBar.visibility = View.GONE
        when (workflowState) {
            WorkflowState.DETECTING, WorkflowState.DETECTED, WorkflowState.CONFIRMING -> {
                bottomPromptChip.visibility = View.VISIBLE
                bottomPromptChip.setText(
                        if (workflowState == WorkflowState.CONFIRMING) {
                            R.string.prompt_hold_camera_steady
                        }
                        else {
                            R.string.prompt_point_at_an_object
                        })
                startCameraPreview()
            }
            WorkflowState.CONFIRMED -> {
                bottomPromptChip.visibility = View.VISIBLE
                bottomPromptChip.setText(R.string.prompt_searching)
                stopCameraPreview()
            }
            WorkflowState.SEARCHING -> {
                searchProgressBar.visibility = View.VISIBLE
                bottomPromptChip.visibility = View.VISIBLE
                bottomPromptChip.setText(R.string.prompt_searching)
                stopCameraPreview()
            }
            WorkflowState.SEARCHED -> {
                bottomPromptChip.visibility = View.GONE
                stopCameraPreview()
            }
            else -> bottomPromptChip.visibility = View.GONE
        }

        val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && bottomPromptChip.visibility == View.VISIBLE
        if (shouldPlayPromptChipEnteringAnimation && promptChipAnimator?.isRunning == false) {
            promptChipAnimator?.start()
        }
    }

    private fun stateChangeInManualSearchMode(workflowState: WorkflowState) {
        val wasPromptChipGone = bottomPromptChip.visibility == View.GONE
        val wasSearchButtonGone = productSearchButton.visibility == View.GONE

        searchProgressBar.visibility = View.GONE
        when (workflowState) {
            WorkflowState.DETECTING, WorkflowState.DETECTED, WorkflowState.CONFIRMING -> {
                bottomPromptChip.visibility = View.VISIBLE
                bottomPromptChip.setText(R.string.prompt_point_at_an_object)
                productSearchButton.visibility = View.GONE
                startCameraPreview()
            }
            WorkflowState.CONFIRMED -> {
                bottomPromptChip.visibility = View.GONE
                productSearchButton.visibility = View.VISIBLE
                productSearchButton.isEnabled = true
                productSearchButton.setBackgroundColor(Color.WHITE)
                startCameraPreview()
            }
            WorkflowState.SEARCHING -> {
                bottomPromptChip.visibility = View.GONE
                productSearchButton.visibility = View.VISIBLE
                productSearchButton.isEnabled = false
                productSearchButton.setBackgroundColor(Color.GRAY)
                searchProgressBar!!.visibility = View.VISIBLE
                stopCameraPreview()
            }
            WorkflowState.SEARCHED -> {
                bottomPromptChip.visibility = View.GONE
                productSearchButton.visibility = View.GONE
                stopCameraPreview()
            }
            else -> {
                bottomPromptChip.visibility = View.GONE
                productSearchButton.visibility = View.GONE
            }
        }

        val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && bottomPromptChip.visibility == View.VISIBLE
        promptChipAnimator?.let {
            if (shouldPlayPromptChipEnteringAnimation && !it.isRunning){
                it.start()
            }
        }

        val shouldPlaySearchButtonEnteringAnimation =
                wasSearchButtonGone && productSearchButton.visibility == View.VISIBLE
        searchButtonAnimator?.let {
            if (shouldPlaySearchButtonEnteringAnimation && !it.isRunning){
                it.start()
            }
        }
    }

    companion object {
        private const val TAG = "LiveObjectActivity"
    }
}
