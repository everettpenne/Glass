package com.everettpenne.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * How the suite moves and feels, in one place: the springs its surfaces use and the haptic each kind of action gives, so a press, a toggle, a
 * confirm and a long press feel the same in every app (the launcher's own motion uses the same values). The numbers are the ones the kit's
 * controls were tuned with (damping ratio, stiffness).
 */
object GlassFeel {
    /** A control dipping under a finger, and coming back. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = .5f, stiffness = 300f)
    /** A switch or a selection moving to its new place. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = .7f, stiffness = 380f)
    /** A menu, a sheet or an alert opening: a little overshoot, then still. */
    fun <T> open(): FiniteAnimationSpec<T> = spring(dampingRatio = .74f, stiffness = 520f)
    /** Something large coming to rest: no overshoot. */
    fun <T> calm(): FiniteAnimationSpec<T> = spring(dampingRatio = .9f, stiffness = 300f)

    /** The haptic cues, by what the person did. */
    enum class Cue(val type: HapticFeedbackType) {
        /** A tap on a control, a step of a slider. */
        TICK(HapticFeedbackType.TextHandleMove),
        /** A switch changing. */
        TOGGLE(HapticFeedbackType.ContextClick),
        /** An action that went through. */
        CONFIRM(HapticFeedbackType.Confirm),
        /** A press held long enough to open a menu. */
        LONG_PRESS(HapticFeedbackType.LongPress),
    }

    fun play(haptic: HapticFeedback?, cue: Cue) { haptic?.performHapticFeedback(cue.type) }
}
