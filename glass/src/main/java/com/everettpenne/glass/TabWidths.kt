package com.everettpenne.glass

/**
 * Pure slot-width math for the floating tab capsule. Every value is in dp as a raw [Float] — no Compose or Android types — so the whole
 * object is unit-testable on the JVM.
 *
 * The capsule lays [count] equal-width slots inside a glass pill that pads [capsuleInset] on each side. A slot is [canonicalSlot] whenever it
 * fits; that is the original capsule's own width (260dp, from `widthIn(max = 260.dp)` in `GlassTabBar` before the content-sized rework) minus its
 * 8dp of insets, shared by its two tabs: `(260 - 2*4) / 2 = 126dp`. When the row cannot fit the capsule at the canonical width — three or four
 * tabs beside the 64dp search button on a phone — the slot scales down uniformly to fill what is available, but never below [minSlot], so a tab
 * always keeps its 44dp+ tap target and its indicator breathing room.
 *
 * Fit model: the capsule may occupy at most `maxAvailable` = screen width − 32dp outer padding − 64dp search button. Two tabs (260dp) always fit
 * on a 360dp+ phone (264dp available), so the two-tab apps keep the original full width everywhere; three and four tabs degrade gracefully.
 */
object TabWidths {
    /** Horizontal inset of the capsule around its tabs, on each side (the panel's 4dp padding). */
    const val capsuleInset = 4f

    /** Canonical per-tab slot width (dp): the original 260dp capsule minus its 8dp of insets, over its two tabs. */
    const val canonicalSlot = 126f

    /** The narrowest a slot may shrink to (dp): a 44dp+ tap target with the indicator's 10dp breathing room on each side. */
    const val minSlot = 56f

    /**
     * The per-tab slot width for [count] tabs in a capsule that may occupy at most [maxAvailable] dp (its outer width, insets included).
     *
     * Returns [canonicalSlot] when it fits; otherwise it shrinks uniformly to `(maxAvailable - 2*[capsuleInset]) / count`, floored at [minSlot].
     * When even the floor cannot fit the capsule (more tabs, or a device narrower than 360dp), the floor still wins — labels and tap targets
     * never collapse below it — and the caller may let the capsule clip or overflow rather than shrink the hit areas further.
     */
    fun slots(count: Int, maxAvailable: Float): Float {
        require(count >= 1) { "count must be >= 1" }
        val fit = (maxAvailable - capsuleInset * 2) / count
        return fit.coerceIn(minSlot, canonicalSlot)
    }

    /** The capsule's outer width (dp) for [count] tabs at [slot] dp each, insets included. */
    fun capsule(slot: Float, count: Int): Float = slot * count + capsuleInset * 2
}
