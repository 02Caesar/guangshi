package com.guangshi.app
import org.junit.Assert.*
import org.junit.Test
class PixelEngineTest {
 private fun render(f:Film,a:Adjust,c:Int=0xff708090.toInt())=PixelEngine.pixel(c,80,90,400,300,f,a)
 @Test fun monoHasEqualChannels() { val p=render(Film.MONO,Adjust(grain=0f)); assertEquals(p ushr 16 and 255,p ushr 8 and 255); assertEquals(p and 255,p ushr 8 and 255) }
 @Test fun exposureIncreasesBrightness() { val low=render(Film.DAY,Adjust(exposure=-1f,grain=0f)); val high=render(Film.DAY,Adjust(exposure=1f,grain=0f)); assertTrue((high ushr 16 and 255)>(low ushr 16 and 255)) }
 @Test fun filmProfilesDiffer() { assertNotEquals(render(Film.DAY,Adjust(grain=0f)),render(Film.NIGHT,Adjust(grain=0f))) }
 @Test fun monoIgnoresColorControls() { assertEquals(render(Film.MONO,Adjust()),render(Film.MONO,Adjust(cyan=1f,magenta=-1f,yellow=.5f))) }
 @Test fun grainIsDeterministicAndResolutionNormalized() { val a=Adjust(grain=.8f); assertEquals(PixelEngine.pixel(0xff808080.toInt(),80,90,400,300,Film.DAY,a),PixelEngine.pixel(0xff808080.toInt(),160,180,800,600,Film.DAY,a)) }
 @Test fun cyanReducesRedOnly() { val a=render(Film.DAY,Adjust(grain=0f)); val b=render(Film.DAY,Adjust(grain=0f,cyan=1f)); assertTrue((b ushr 16 and 255)<(a ushr 16 and 255)); assertEquals(a and 65535,b and 65535) }
 @Test fun normalAtDefaultsPreservesColor() { val c=0xff708090.toInt(); assertEquals(c,render(Film.NATURAL,Adjust(),c)) }
 @Test fun allSixProfilesHaveDifferentResponses() { assertEquals(6,Film.entries.map { render(it,Adjust(grain=0f)) }.toSet().size) }
 @Test fun rgbChangesImmediatelyWithValue() { val a=render(Film.NATURAL,Adjust());val b=render(Film.NATURAL,Adjust(yellow=-.8f)); assertTrue((b and 255)>(a and 255)) }
}
