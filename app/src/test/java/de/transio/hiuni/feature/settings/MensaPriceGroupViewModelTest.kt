package de.transio.hiuni.feature.settings

import de.transio.hiuni.feature.mensa.data.MensaPriceGroupResolver
import de.transio.hiuni.feature.mensa.data.PriceGroup
import de.transio.hiuni.feature.mensa.data.PriceGroupDetection
import de.transio.hiuni.feature.mensa.data.PriceGroupPreference
import de.transio.hiuni.feature.mensa.data.PriceGroupSource
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MensaPriceGroupViewModelTest {

    private val preference = MutableStateFlow(PriceGroupPreference.AUTO)
    private val detection = MutableStateFlow(
        PriceGroupDetection(PriceGroup.EMPLOYEE, PriceGroupSource.LOGIN_WITHOUT_MATRIKEL)
    )
    private val resolver = mockk<MensaPriceGroupResolver>(relaxed = true).also {
        every { it.preference } returns preference
        every { it.detection } returns detection
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `State zeigt Wahl, Erkennung und effektive Gruppe`() = runTest {
        val vm = MensaPriceGroupViewModel(resolver)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }

        assertEquals(PriceGroupPreference.AUTO, vm.state.value.preference)
        assertEquals(PriceGroup.EMPLOYEE, vm.state.value.effective)
        assertEquals("Automatisch (aus Uni-Login: Bedienstete)", vm.state.value.autoLabel)

        preference.value = PriceGroupPreference.GUEST
        assertEquals(PriceGroup.GUEST, vm.state.value.effective)
        job.cancel()
    }

    @Test
    fun `select reicht die Wahl an den Resolver weiter`() = runTest {
        val vm = MensaPriceGroupViewModel(resolver)
        vm.select(PriceGroupPreference.STUDENT)
        coVerify { resolver.setPreference(PriceGroupPreference.STUDENT) }
    }
}
