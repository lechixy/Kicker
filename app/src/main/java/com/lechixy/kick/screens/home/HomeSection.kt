package com.lechixy.kick.screens.home

import com.lechixy.kick.R

internal enum class HomeSection(val title: Int, val icon: Int) {
    CONTINUE_WATCHING(R.string.home_continue_watching, R.drawable.motion_play_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    FOLLOWING(R.string.home_following_live, R.drawable.favorite_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    CATEGORIES(R.string.section_categories, R.drawable.browse_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    RECOMMENDED(R.string.home_recommended_live, R.drawable.play_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
}

internal fun orderedHomeSections(savedOrder: String): List<HomeSection> {
    val byName = HomeSection.entries.associateBy { it.name }
    return (savedOrder.split(',').mapNotNull(byName::get) + HomeSection.entries).distinct()
}
