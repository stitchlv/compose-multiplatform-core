/*
 * Copyright 2023 The Android Open Source Project
 *
 * Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.animation.core.cupertino

import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.convertNanosToSeconds
import androidx.compose.animation.core.convertSecondsToNanos
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

/**
 * A class that represents the animation specification for a scroll decay animation
 * using iOS-style decay behavior.
 *
 * @property decelerationRate The rate at which the velocity decelerates over time.
 * Default value is equal to one used by default UIScrollView behavior.
 *
 * 鸿蒙的滑动减速曲线，借鉴 ios，修复了一些 bug。
 * 基本公式：
 * vt = v0*decelerationRate^(1000f * t)
 *
 * absVelocityThreshold 为滑动停止时的速度，带入 vt 中
 * 得到  absVelocityThreshold = v0*decelerationRate^(1000 * t),两边取自然对数
 *      ln(absVelocityThreshold) = ln(v0) + 1000t *ln(decelerationRate)
 *           t = ln(absVelocityThreshold/v0)/(1000*ln(decelerationRate))
 * 因为：coefficient = (1000*ln(decelerationRate))
 * 所以： getDurationNanos = ln(absVelocityThreshold / absVelocity) / coefficient
 * s = v*t, 做积分，得到
 * getValueFromNanos = s = (decelerationRate.pow(1000f * playTimeSeconds) - 1f) * initialVelocity / coefficient
 * 把 t = ln(absVelocityThreshold/v0)/(1000*ln(decelerationRate)) 带入，
 * 得到 getTargetValue = (absVelocityThreshold - initialVelocity) / coefficient
 *
 */
class CupertinoScrollDecayAnimationSpec(
    private val decelerationRate: Float = 0.998f,
    private val targetFrameRate: Float = 120f // 可选参数，默认 120Hz
) : FloatDecayAnimationSpec {

    private val coefficient: Float = 1000f * ln(decelerationRate)

    override val absVelocityThreshold: Float
        get() {
            val frameTimeSeconds = 1.0f / targetFrameRate
            return 1f / frameTimeSeconds // v=s/t 根据当前刷新率判断，当一帧内移动小于 1 像素的时候停止
        }

    override fun getTargetValue(initialValue: Float, initialVelocity: Float): Float =
        initialValue - (initialVelocity - sign(initialVelocity) * absVelocityThreshold) / coefficient // 这里原来公式错误了，应该要减去absVelocityThreshold

    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        initialVelocity: Float
    ): Float {
        val playTimeSeconds = convertNanosToSeconds(playTimeNanos).toFloat()
        val initialVelocityOverTimeIntegral =
            (decelerationRate.pow(1000f * playTimeSeconds) - 1f) / coefficient * initialVelocity
        return initialValue + initialVelocityOverTimeIntegral
    }

    override fun getDurationNanos(initialValue: Float, initialVelocity: Float): Long {
        val absVelocity = abs(initialVelocity)

        if (absVelocity < absVelocityThreshold) {
            return 0
        }
        val seconds = ln(absVelocityThreshold / absVelocity) / coefficient // 这里原来公式也错误了，ln里多了个coefficient
        return convertSecondsToNanos(seconds)
    }

    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        initialVelocity: Float
    ): Float {
        val playTimeSeconds = convertNanosToSeconds(playTimeNanos).toFloat()
        return initialVelocity * decelerationRate.pow(1000f * playTimeSeconds) // 最基础的公式 vt = v0*decelerationRate^(1000f * t)
    }
}