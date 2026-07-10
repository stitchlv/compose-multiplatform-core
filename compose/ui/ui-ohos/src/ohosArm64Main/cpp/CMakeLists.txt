cmake_minimum_required(VERSION 3.5.0)
project(arkui_utils)

add_library(arkui_utils STATIC arkui_utils_export.cpp)

find_library(hilog-lib hilog_ndk.z)
find_library(ace-lib ace_ndk.z)
target_link_libraries(arkui_utils PUBLIC ${hilog-lib} ${ace-lib})
