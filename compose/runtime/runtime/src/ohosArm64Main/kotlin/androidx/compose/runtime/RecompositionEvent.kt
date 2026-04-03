/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.runtime


// 重组分组的唯一标识（供App模块使用）
data class RecompositionKey(val key: Int, val anchorId: Int)

// 重组统计数据（包含触发次数、跳过次数）
data class RecompositionStats(
    val count: Int, // 触发次数
    val skips: Int  // 跳过次数
)

// 定义重组数据更新的监听接口（Compose源码只定义，不实现）
fun interface RecompositionUpdateListener {
    /**
     * 当重组/跳过次数更新时触发
     * @param stats 最新的统计数据
     */
    fun onRecompositionUpdated(recompositionData: MutableMap<RecompositionKey, RecompositionStats>)
}