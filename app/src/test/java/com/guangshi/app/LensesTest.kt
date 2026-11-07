package com.guangshi.app
import org.junit.Assert.*
import org.junit.Test
class LensesTest {
    private val main=LensSpec("main",24f,1f,10f)
    private val wide=LensSpec("wide",15f,1f,4f)
    private val tele=LensSpec("tele",73f,1f,5f)
    @Test fun choosesActualWideWhenExposed() { assertEquals("wide",chooseLens(listOf(main,wide,tele),15)!!.lens.id) }
    @Test fun noFake15mmOnMainOnlyPhone() { assertNull(chooseLens(listOf(main),15)) }
    @Test fun teleUsesTeleAnd146IsTwoTimes() { val p=chooseLens(listOf(main,wide,tele),146)!!;assertEquals("tele",p.lens.id);assertEquals(2f,p.zoom) }
    @Test fun mainCropsFor49IfTeleCannotGoWideEnough() { assertEquals("main",chooseLens(listOf(main,tele),49)!!.lens.id) }
    @Test fun mainGroupCyclesExactly() { assertEquals(28,nextFocal(24,24)); assertEquals(35,nextFocal(24,28)); assertEquals(24,nextFocal(24,35)) }
    @Test fun teleGroupCyclesExactly() { assertEquals(85,nextFocal(73,73)); assertEquals(73,nextFocal(73,85)) }
    @Test fun outOfRangeNeverClampsToMisleadingFocalLength() { assertNull(chooseLens(listOf(main.copy(maxZoom=2f)),146)) }
}
