/*
 * Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.foundation.gestures

import androidx.compose.animation.core.cupertino.CupertinoScrollDecayAnimationSpec
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.foundation.gestures.cupertino.CupertinoFlingBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalUiDvsyncSwitch
import androidx.compose.ui.util.trace
import com.bytedance.kmp.harko.OHLogger

private const val DV_SYNC_TAG = "DvSyncContext1"

internal actual fun platformDefaultFlingBehavior(): ScrollableDefaultFlingBehavior =
    CupertinoFlingBehavior(CupertinoScrollDecayAnimationSpec().generateDecayAnimationSpec())

@Composable
internal actual fun rememberPlatformDefaultFlingBehavior(): FlingBehavior {
    // 鸿蒙的滑动动画更接近 ios 的滑动动画
    return remember {
        platformDefaultFlingBehavior()
    }
}

internal actual fun CompositionLocalConsumerModifierNode.setUiDvsyncSwitchForFling(enable: Boolean) {
    val traceName = if (enable) {
        "Scrollable.FlingStart.SetUiDvsyncSwitch"
    } else {
        "Scrollable.FlingEnd.SetUiDvsyncSwitch"
    }
    trace(traceName) {
        val uiDvsyncSwitch = currentValueOf(LocalUiDvsyncSwitch)
        if (uiDvsyncSwitch == null) {
            OHLogger.w(DV_SYNC_TAG, "FlingSwitch skipped reason=noLocalUiDvsyncSwitch enable=$enable")
            return@trace
        }
        OHLogger.i(DV_SYNC_TAG, "FlingSwitch request enable=$enable")
        uiDvsyncSwitch.invoke(enable)
    }
}
