package ru.palestra.hide_and_seek_exercise_c.data

/** События о силе сигнала от другого устройства. */
sealed class ConnectionPowerLevel {
    object Level1 : ConnectionPowerLevel()
    object Level2 : ConnectionPowerLevel()
    object Level3 : ConnectionPowerLevel()
    object Level4 : ConnectionPowerLevel()
    object Level5 : ConnectionPowerLevel()
    object Level6 : ConnectionPowerLevel()
    object Level7 : ConnectionPowerLevel()
    object Level8 : ConnectionPowerLevel()
    object Level9 : ConnectionPowerLevel()
    object Level10 : ConnectionPowerLevel()

    object LevelUnknown : ConnectionPowerLevel()
}