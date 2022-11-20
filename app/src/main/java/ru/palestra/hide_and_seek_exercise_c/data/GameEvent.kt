package ru.palestra.hide_and_seek_exercise_c.data

/** Игровые события, для реакции UI. */
sealed class GameEvent(val eventSymbol: String) {

    companion object {
        /** Спец. символ для события начала игры. */
        private const val GAME_INITIALIZE_EVENT = "I"

        /** Спец. символ для события "спрятался". */
        private const val GAME_PLAYER_HIDED_EVENT = "H"

        /** Спец. символ для события "меня нашли". */
        private const val GAME_FOUND_EVENT = "F"

        /** Спец. символ для события "играть звук". */
        private const val GAME_PLAY_SOUND_EVENT = "P"

        /** Спец. символ для события "остановить звук". */
        private const val GAME_STOP_SOUND_EVENT = "S"

        /** Спец. символ для события "игра прервана". */
        private const val GAME_REJECTED_EVENT = "R"

        /** Спец. символ для события "игрок покинул игру". */
        private const val GAME_PLAYER_LEAVE_EVENT = "L"

        /** Спец. символ для события "игрок успешно приглашен в игру". */
        private const val GAME_INVITE_SUCCESS_EVENT = "D"

        /** Спец. символ необходим для того, чтобы система событий "прочухалась". */
        private const val SYSTEM_EVENT = "Z"

        /** Спец. символ для события "игрок проиграл". */
        private const val GAME_PLAYER_RESET_ALL = "A"

        /** Метод для конвертирования спец. символа в игровое событие. */
        fun getEventFromSymbol(symbol: String): GameEvent =
            when (symbol) {
                GAME_INITIALIZE_EVENT -> GameInitialize
                GAME_REJECTED_EVENT -> GameRejected
                GAME_FOUND_EVENT -> GamePlayerWasFound

                GAME_PLAYER_RESET_ALL -> GamePlayerResetAll
                GAME_PLAYER_LEAVE_EVENT -> GamePlayerLeave
                GAME_PLAYER_HIDED_EVENT -> GamePlayerHided
                GAME_INVITE_SUCCESS_EVENT -> GamePlayerInviteSuccess

                GAME_PLAY_SOUND_EVENT -> GameBirdPlaySound
                GAME_STOP_SOUND_EVENT -> GameBirdStopSound

                SYSTEM_EVENT -> SystemEvent

                else -> throw IllegalStateException("Symbol '$symbol' not supported in GameEvent mapper!")
            }
    }

    /** Событие начала игры. */
    object GameInitialize : GameEvent(GAME_INITIALIZE_EVENT)

    /** Событие прерывания игры. */
    object GameRejected : GameEvent(GAME_REJECTED_EVENT)

    /** Событие "игрок успешно приглашен в игру". */
    object GamePlayerInviteSuccess : GameEvent(GAME_INVITE_SUCCESS_EVENT)

    /** Событие "игрок покинул игру". */
    object GamePlayerLeave : GameEvent(GAME_PLAYER_LEAVE_EVENT)

    /** Событие "игрок проиграл". */
    object GamePlayerResetAll : GameEvent(GAME_PLAYER_RESET_ALL)

    /** Событие "игрок спрятался". */
    object GamePlayerHided : GameEvent(GAME_PLAYER_HIDED_EVENT)

    /** Событие "нашли игрока". */
    object GamePlayerWasFound : GameEvent(GAME_FOUND_EVENT)

    /** Событие "играть звук". */
    object GameBirdPlaySound : GameEvent(GAME_PLAY_SOUND_EVENT)

    /** Событие "остановить звук". */
    object GameBirdStopSound : GameEvent(GAME_STOP_SOUND_EVENT)

    /** Системное событие. */
    object SystemEvent : GameEvent(SYSTEM_EVENT)
}