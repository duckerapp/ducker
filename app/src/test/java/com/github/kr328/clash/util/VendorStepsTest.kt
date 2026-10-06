package com.github.kr328.clash.util

import com.github.kr328.clash.R
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Шаги для оболочек выбираются по версии Android (D-108, F-122). Границы легко сдвинуть на
 * единицу, и тогда человеку с Android 11 покажут меню, которого у него нет.
 */
class VendorStepsTest {
    @Test
    fun `OnePlus до Android 12 — своя OxygenOS, после — ColorOS`() {
        assertEquals(R.string.background_steps_oxygenos_old, Vendor.ColorOs.stepsFor(30, oneplus = true))
        assertEquals(R.string.background_steps_coloros, Vendor.ColorOs.stepsFor(31, oneplus = true))
        assertEquals(R.string.background_steps_coloros_old, Vendor.ColorOs.stepsFor(30, oneplus = false))
        assertEquals(R.string.background_steps_coloros, Vendor.ColorOs.stepsFor(36, oneplus = false))
    }

    @Test
    fun `Samsung и Transsion меняют меню с Android 12`() {
        assertEquals(R.string.background_steps_samsung_old, Vendor.Samsung.stepsFor(30, oneplus = false))
        assertEquals(R.string.background_steps_samsung, Vendor.Samsung.stepsFor(31, oneplus = false))
        assertEquals(R.string.background_steps_transsion_old, Vendor.Transsion.stepsFor(29, oneplus = false))
        assertEquals(R.string.background_steps_transsion, Vendor.Transsion.stepsFor(31, oneplus = false))
    }

    @Test
    fun `vivo получает батарею в настройках приложения с Android 13`() {
        assertEquals(R.string.background_steps_vivo_old, Vendor.Vivo.stepsFor(32, oneplus = false))
        assertEquals(R.string.background_steps_vivo, Vendor.Vivo.stepsFor(33, oneplus = false))
    }

    @Test
    fun `Xiaomi и Huawei — одни шаги на все версии`() {
        for (sdk in 29..37) {
            assertEquals(R.string.background_steps_xiaomi, Vendor.Xiaomi.stepsFor(sdk, oneplus = false))
            assertEquals(R.string.background_steps_huawei, Vendor.Huawei.stepsFor(sdk, oneplus = false))
        }
    }
}
