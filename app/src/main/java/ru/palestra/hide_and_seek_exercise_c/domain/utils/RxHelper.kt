package ru.palestra.hide_and_seek_exercise_c.domain.utils

import io.reactivex.disposables.Disposable

/** Метод для уничтожения Rx подписки, в случае, если она не была уничтожена ранее. */
fun Disposable.disposeIfNeeded() {
    if (!isDisposed) dispose()
}