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

import java.nio.ByteBuffer;

/** An interface to process the input camera frame and perform detection on it. */
public interface FrameProcessor {

  /** Processes the input frame with the underlying detector. */
  void process(ByteBuffer data, FrameMetadata frameMetadata, GraphicOverlay graphicOverlay);

  /** Stops the underlying detector and release resources. */
  void stop();
}
