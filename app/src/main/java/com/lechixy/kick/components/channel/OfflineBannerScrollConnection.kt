package com.lechixy.kick.components.channel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

internal class OfflineBannerScrollConnection(
    private val enabled: () -> Boolean,
    private val atTop: () -> Boolean,
    private val state: OfflineBannerState
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Drag and kinetic scrolling must consume the same header distance before the list moves.
        if (enabled() && available.y < 0f) {
            return Offset(0f, -state.drag(-available.y))
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (enabled() && available.y > 0f && atTop()) {
            return Offset(0f, -state.drag(-available.y))
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
        if (enabled()) state.settle()
        return androidx.compose.ui.unit.Velocity.Zero
    }
}
