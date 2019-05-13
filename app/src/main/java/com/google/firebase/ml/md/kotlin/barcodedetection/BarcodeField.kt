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

package com.google.firebase.ml.md.kotlin.barcodedetection

import android.os.Parcel
import android.os.Parcelable

/** Information about a barcode field.  */
class BarcodeField(internal val label:String, internal val value:String) : Parcelable {

    override fun describeContents() = 0
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(label)
        dest.writeString(value)
    }

    companion object {

        @JvmField
        val CREATOR: Parcelable.Creator<BarcodeField> = object : Parcelable.Creator<BarcodeField> {
            override fun createFromParcel(parcel: Parcel): BarcodeField{
                return BarcodeField( label = parcel.readString()?:"", value = parcel.readString()?:"" )
            }

            override fun newArray(size: Int): Array<BarcodeField?>{
                return arrayOfNulls(size)
            }

        }
    }
}
