package com.guangshi.app
import org.junit.Assert.*
import org.junit.Test
class DecodeSizingTest {
 @Test fun fullResolutionExportNeverOverflows() { assertEquals(1,DecodeSizing.sampleSize(8192,6144,Int.MAX_VALUE)) }
 @Test fun previewUsesPowerOfTwo() { assertEquals(8,DecodeSizing.sampleSize(8192,6144,1000)) }
 @Test fun extremeMetadataStillTerminates() { assertEquals(1073741824,DecodeSizing.sampleSize(Int.MAX_VALUE,Int.MAX_VALUE,1)) }
 @Test(expected=IllegalArgumentException::class) fun rejectZeroTarget() { DecodeSizing.sampleSize(4000,3000,0) }
 @Test(expected=IllegalArgumentException::class) fun rejectInvalidJpeg() { DecodeSizing.sampleSize(-1,-1,1000) }
}
