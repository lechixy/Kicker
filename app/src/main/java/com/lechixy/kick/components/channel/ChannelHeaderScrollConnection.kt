package com.lechixy.kick.components.channel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

internal class ChannelHeaderScrollConnection(
    private val drag: (Float) -> Float,
    private val settle: () -> Unit = {}
) : NestedScrollConnection {
    // Collapse the header before the page moves, then expand when the page reaches its top.
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0f) Offset(0f, -drag(-available.y)) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y > 0f) Offset(0f, -drag(-available.y)) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settle()
        return Velocity.Zero
    }
}
