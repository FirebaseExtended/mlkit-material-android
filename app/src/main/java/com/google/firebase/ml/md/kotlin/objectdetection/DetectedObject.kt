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

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Rect
import android.util.Log
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.objects.FirebaseVisionObject
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Holds the detected object and its related image info.
 */
class DetectedObject(
    private val visionObject: FirebaseVisionObject,
    val objectIndex: Int,
    private val image: FirebaseVisionImage
) {

    private var bitmap: Bitmap? = null
    private var jpegBytes: ByteArray? = null

    val objectId: Int? = visionObject.trackingId
    val boundingBox: Rect = visionObject.boundingBox

    val imageData: ByteArray?
        @Synchronized get() {
            if (jpegBytes == null) {
                try {
                    ByteArrayOutputStream().use { stream ->
                        getBitmap().compress(CompressFormat.JPEG, /* quality= */ 100, stream)
                        jpegBytes = stream.toByteArray()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error getting object image data!")
                }
            }
            return jpegBytes
        }

    @Synchronized
    fun getBitmap(): Bitmap {
        return bitmap ?: let {
            val boundingBox = visionObject.boundingBox
            val createdBitmap = Bitmap.createBitmap(
                    image.bitmap,
                    boundingBox.left,
                    boundingBox.top,
                    boundingBox.width(),
                    boundingBox.height()
            )
            if (createdBitmap.width > MAX_IMAGE_WIDTH) {
                val dstHeight = (MAX_IMAGE_WIDTH.toFloat() / createdBitmap.width * createdBitmap.height).toInt()
                bitmap = Bitmap.createScaledBitmap(createdBitmap, MAX_IMAGE_WIDTH, dstHeight, /* filter= */ false)
            }
            createdBitmap
        }
    }

    companion object {
        private const val TAG = "DetectedObject"
        private const val MAX_IMAGE_WIDTH = 640
    }
}
