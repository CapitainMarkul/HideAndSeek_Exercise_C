package ru.palestra.hide_and_seek_exercise_c.domain.location

/** Описание объекта, который отвечает за работу с геолокацией. */
interface NearbyLocationManagerApi {

    /** Метод проверяет, включен ли у пользователя в данный момент GPS. */
    fun isGpsLocationEnabled(): Boolean

    /** Метод просит пользователя активировать работу GPS. */
    fun requestToEnableGpsLocation()
}