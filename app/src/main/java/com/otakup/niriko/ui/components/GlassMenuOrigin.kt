package com.otakup.niriko.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * 锚定浮层的 transformOrigin 计算（纯函数，v1.2.2 新增）。
 *
 * 为什么需要它：`GlassAnchoredMenu` 改造前是 `if (!expanded || anchorInRoot == null) return`，
 * 浮层**硬切**出现/消失。要做「从按钮里长出来」的进场，缩放中心必须落在锚点上；
 * 用默认的 [TransformOrigin.Center] 会让浮层从自身中心胀大，与触发按钮脱开
 * （在左上角触发时尤其明显：菜单在按钮右下方，却从菜单自己的中间长出来）。
 *
 * 坐标系约定：所有输入都用浮层宿主（overlay host）的坐标空间，
 * 与 [glassMenuPosition] 完全一致——这样两个函数的输入是同一套量，不会出现
 * "位置算一次、origin 又换一套坐标系"的错位。
 *
 * @param anchorInRoot 锚点矩形（宿主坐标系）
 * @param hostOrigin 宿主原点在 root 坐标系里的位置（`positionInRoot()`）
 * @param menuPosition 菜单左上角在宿主坐标系里的偏移（[glassMenuPosition] 的结果）
 * @param menuSize 菜单测量后的尺寸（像素）
 * @return 以「菜单局部坐标归一化」表示的变换原点；菜单尺寸非法时回退到 [TransformOrigin.Center]
 */
fun glassMenuTransformOrigin(
    anchorInRoot: Rect,
    hostOrigin: Offset,
    menuPosition: IntOffset,
    menuSize: IntSize,
): TransformOrigin {
    if (menuSize.width <= 0 || menuSize.height <= 0) return TransformOrigin.Center
    // 锚点中心换算到「菜单局部坐标」：先减宿主原点得到宿主坐标，再减菜单位置得到菜单坐标。
    val anchorCenterX = anchorInRoot.center.x - hostOrigin.x - menuPosition.x
    val anchorCenterY = anchorInRoot.center.y - hostOrigin.y - menuPosition.y
    return TransformOrigin(
        pivotFractionX = (anchorCenterX / menuSize.width).coerceIn(-0.25f, 1.25f),
        pivotFractionY = (anchorCenterY / menuSize.height).coerceIn(-0.25f, 1.25f),
    )
}
