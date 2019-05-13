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

package com.google.firebase.ml.md.java.camera;

import static com.google.common.base.Preconditions.checkNotNull;

import android.app.Application;
import android.content.Context;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.md.java.objectdetection.DetectedObject;
import com.google.firebase.ml.md.java.productsearch.Product;
import com.google.firebase.ml.md.java.productsearch.SearchEngine.SearchResultListener;
import com.google.firebase.ml.md.java.productsearch.SearchedObject;
import com.google.firebase.ml.md.java.settings.PreferenceUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** View model for handling application workflow based on camera preview. */
public class WorkflowModel extends AndroidViewModel implements SearchResultListener {

  /**
   * State set of the application workflow.
   */
  public enum WorkflowState {
    NOT_STARTED,
    DETECTING,
    DETECTED,
    CONFIRMING,
    CONFIRMED,
    SEARCHING,
    SEARCHED
  }

  public final MutableLiveData<WorkflowState> workflowState = new MutableLiveData<>();
  public final MutableLiveData<DetectedObject> objectToSearch = new MutableLiveData<>();
  public final MutableLiveData<SearchedObject> searchedObject = new MutableLiveData<>();

  public final MutableLiveData<FirebaseVisionBarcode> detectedBarcode = new MutableLiveData<>();

  private final Set<Integer> objectIdsToSearch = new HashSet<>();

  private boolean isCameraLive = false;
  @Nullable private DetectedObject confirmedObject;

  public WorkflowModel(Application application) {
    super(application);
  }

  @MainThread
  public void setWorkflowState(WorkflowState workflowState) {
    if (!workflowState.equals(WorkflowState.CONFIRMED)
        && !workflowState.equals(WorkflowState.SEARCHING)
        && !workflowState.equals(WorkflowState.SEARCHED)) {
      confirmedObject = null;
    }
    this.workflowState.setValue(workflowState);
  }

  @MainThread
  public void confirmingObject(DetectedObject object, float progress) {
    boolean isConfirmed = (Float.compare(progress, 1f) == 0);
    if (isConfirmed) {
      confirmedObject = object;
      if (PreferenceUtils.isAutoSearchEnabled(getContext())) {
        setWorkflowState(WorkflowState.SEARCHING);
        triggerSearch(object);
      } else {
        setWorkflowState(WorkflowState.CONFIRMED);
      }
    } else {
      setWorkflowState(WorkflowState.CONFIRMING);
    }
  }

  @MainThread
  public void onSearchButtonClicked() {
    if (confirmedObject == null) {
      return;
    }

    setWorkflowState(WorkflowState.SEARCHING);
    triggerSearch(confirmedObject);
  }

  private void triggerSearch(DetectedObject object) {
    Integer objectId = checkNotNull(object.getObjectId());
    if (objectIdsToSearch.contains(objectId)) {
      // Already in searching.
      return;
    }

    objectIdsToSearch.add(objectId);
    objectToSearch.setValue(object);
  }

  public void markCameraLive() {
    isCameraLive = true;
    objectIdsToSearch.clear();
  }

  public void markCameraFrozen() {
    isCameraLive = false;
  }

  public boolean isCameraLive() {
    return isCameraLive;
  }

  @Override
  public void onSearchCompleted(DetectedObject object, List<Product> products) {
    if (!object.equals(confirmedObject)) {
      // Drops the search result from the object that has lost focus.
      return;
    }

    objectIdsToSearch.remove(object.getObjectId());
    setWorkflowState(WorkflowState.SEARCHED);
    searchedObject.setValue(
        new SearchedObject(getContext().getResources(), confirmedObject, products));
  }

  private Context getContext() {
    return getApplication().getApplicationContext();
  }
}
