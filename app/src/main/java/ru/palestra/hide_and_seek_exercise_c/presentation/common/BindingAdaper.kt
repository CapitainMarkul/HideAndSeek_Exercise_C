package ru.palestra.hide_and_seek_exercise_c.presentation.common

import android.view.View
import androidx.core.view.isVisible
import androidx.databinding.BindingAdapter

@BindingAdapter("isVisible")
fun View.isVisible(visible: Boolean) {
    isVisible = visible
}