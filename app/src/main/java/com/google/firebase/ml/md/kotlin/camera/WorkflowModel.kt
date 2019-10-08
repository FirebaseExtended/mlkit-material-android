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

package com.google.firebase.ml.md.kotlin.camera

import android.app.Application
import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.md.kotlin.objectdetection.DetectedObject
import com.google.firebase.ml.md.kotlin.productsearch.Product
import com.google.firebase.ml.md.kotlin.productsearch.SearchedObject
import com.google.firebase.ml.md.kotlin.settings.PreferenceUtils
import java.util.HashSet

/** View model for handling application workflow based on camera preview.  */
class WorkflowModel(application: Application) : AndroidViewModel(application) {

    val workflowState = MutableLiveData<WorkflowState>()
    val objectToSearch = MutableLiveData<DetectedObject>()
    val searchedObject = MutableLiveData<SearchedObject>()
    val detectedBarcode = MutableLiveData<FirebaseVisionBarcode>()

    private val objectIdsToSearch = HashSet<Int>()

    var isCameraLive = false
        private set

    private var confirmedObject: DetectedObject? = null

    private val context: Context
        get() = getApplication<Application>().applicationContext

    /**
     * State set of the application workflow.
     */
    enum class WorkflowState {
        NOT_STARTED,
        DETECTING,
        DETECTED,
        CONFIRMING,
        CONFIRMED,
        SEARCHING,
        SEARCHED
    }

    @MainThread
    fun setWorkflowState(workflowState: WorkflowState) {
        if (workflowState != WorkflowState.CONFIRMED &&
                workflowState != WorkflowState.SEARCHING &&
                workflowState != WorkflowState.SEARCHED) {
            confirmedObject = null
        }
        this.workflowState.value = workflowState
    }

    @MainThread
    fun confirmingObject(confirmingObject: DetectedObject, progress: Float) {
        val isConfirmed = progress.compareTo(1f) == 0
        if (isConfirmed) {
            confirmedObject = confirmingObject
            if (PreferenceUtils.isAutoSearchEnabled(context)) {
                setWorkflowState(WorkflowState.SEARCHING)
                triggerSearch(confirmingObject)
            } else {
                setWorkflowState(WorkflowState.CONFIRMED)
            }
        } else {
            setWorkflowState(WorkflowState.CONFIRMING)
        }
    }

    @MainThread
    fun onSearchButtonClicked() {
        confirmedObject?.let {
            setWorkflowState(WorkflowState.SEARCHING)
            triggerSearch(it)
        }
    }

    private fun triggerSearch(detectedObject: DetectedObject) {
        val objectId = detectedObject.objectId ?: throw NullPointerException()
        if (objectIdsToSearch.contains(objectId)) {
            // Already in searching.
            return
        }

        objectIdsToSearch.add(objectId)
        objectToSearch.value = detectedObject
    }

    fun markCameraLive() {
        isCameraLive = true
        objectIdsToSearch.clear()
    }

    fun markCameraFrozen() {
        isCameraLive = false
    }

    fun onSearchCompleted(detectedObject: DetectedObject, products: List<Product>) {
        val lConfirmedObject = confirmedObject
        if (detectedObject != lConfirmedObject) {
            // Drops the search result from the object that has lost focus.
            return
        }

        objectIdsToSearch.remove(detectedObject.objectId)
        setWorkflowState(WorkflowState.SEARCHED)

        searchedObject.value = SearchedObject(context.resources, lConfirmedObject, products)
    }
}
