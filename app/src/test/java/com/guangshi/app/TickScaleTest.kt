package com.guangshi.app
import org.junit.Assert.*
import org.junit.Test
class TickScaleTest {
    @Test fun exposureHasSixtyOnePositionsAndOneTenthSteps() {
        val s=TickScale(-3f,3f)
        assertEquals(60,s.intervals)
        assertEquals(.7f,s.snap(.65f))
        assertEquals(-3f,s.snap(-9f));assertEquals(3f,s.snap(9f))
        for(i in 0 until s.intervals)assertEquals(.1f,s.value(i+1)-s.value(i),.00001f)
    }
    @Test fun repeatedClicksDoNotDriftAndReverseExactly() {
        val s=TickScale(-3f,3f);var value=0f
        repeat(30) {value=s.move(value,1)}
        assertEquals(3f,value)
        repeat(60) {value=s.move(value,-1)}
        assertEquals(-3f,value)
    }
    @Test fun colorGrainAndApertureAllUseTenths() {
        for(s in listOf(TickScale(-1f,1f),TickScale(0f,1f),TickScale(1.4f,16f))) {
            for(i in 0..s.intervals)assertEquals(i,s.index(s.value(i)))
        }
        assertEquals(1.5f,TickScale(1.4f,16f).move(1.4f,1))
        assertEquals(.2f,TickScale(0f,1f).snap(.18f))
    }
}
