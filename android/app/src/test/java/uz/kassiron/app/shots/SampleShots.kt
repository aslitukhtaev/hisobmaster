package uz.kassiron.app.shots

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.kassiron.app.ui.till.TillScreen

/** Proves the screenshot harness works; each area adds its own *Shots.kt next to this. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = Shots.PHONE)
class SampleShots {
    @Before fun setUp() = Shots.seed()

    @Test fun till() = Shots.capture("sample_till") { nav -> TillScreen(nav) }
}
