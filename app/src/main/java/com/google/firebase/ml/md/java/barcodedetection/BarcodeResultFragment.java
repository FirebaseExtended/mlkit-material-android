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

package com.google.firebase.ml.md.java.barcodedetection;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.ml.md.R;
import com.google.firebase.ml.md.java.camera.WorkflowModel;
import com.google.firebase.ml.md.java.camera.WorkflowModel.WorkflowState;
import java.util.ArrayList;

/** Displays the bottom sheet to present barcode fields contained in the detected barcode. */
public class BarcodeResultFragment extends BottomSheetDialogFragment {

  private static final String TAG = "BarcodeResultFragment";
  private static final String ARG_BARCODE_FIELD_LIST = "arg_barcode_field_list";

  public static void show(
      FragmentManager fragmentManager, ArrayList<BarcodeField> barcodeFieldArrayList) {
    BarcodeResultFragment barcodeResultFragment = new BarcodeResultFragment();
    Bundle bundle = new Bundle();
    bundle.putParcelableArrayList(ARG_BARCODE_FIELD_LIST, barcodeFieldArrayList);
    barcodeResultFragment.setArguments(bundle);
    barcodeResultFragment.show(fragmentManager, TAG);
  }

  public static void dismiss(FragmentManager fragmentManager) {
    BarcodeResultFragment barcodeResultFragment =
        (BarcodeResultFragment) fragmentManager.findFragmentByTag(TAG);
    if (barcodeResultFragment != null) {
      barcodeResultFragment.dismiss();
    }
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater layoutInflater,
      @Nullable ViewGroup viewGroup,
      @Nullable Bundle bundle) {
    View view = layoutInflater.inflate(R.layout.barcode_bottom_sheet, viewGroup);
    ArrayList<BarcodeField> barcodeFieldList;
    Bundle arguments = getArguments();
    if (arguments != null && arguments.containsKey(ARG_BARCODE_FIELD_LIST)) {
      barcodeFieldList = arguments.getParcelableArrayList(ARG_BARCODE_FIELD_LIST);
    } else {
      Log.e(TAG, "No barcode field list passed in!");
      barcodeFieldList = new ArrayList<>();
    }

    RecyclerView fieldRecyclerView = view.findViewById(R.id.barcode_field_recycler_view);
    fieldRecyclerView.setHasFixedSize(true);
    fieldRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
    fieldRecyclerView.setAdapter(new BarcodeFieldAdapter(barcodeFieldList));

    return view;
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialogInterface) {
    if (getActivity() != null) {
      // Back to working state after the bottom sheet is dismissed.
      ViewModelProviders.of(getActivity())
          .get(WorkflowModel.class)
          .setWorkflowState(WorkflowState.DETECTING);
    }
    super.onDismiss(dialogInterface);
  }
}
