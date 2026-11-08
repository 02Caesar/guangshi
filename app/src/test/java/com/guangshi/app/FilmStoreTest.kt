package com.guangshi.app

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilmStoreTest {
    private lateinit var context: Context
    private lateinit var store: FilmStore
    private lateinit var provider: TestMediaProvider
    private val id="1700000000000_test"
    private val n=Negative(id,Film.DAY,Adjust(grain=.18f),24)
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        store=FilmStore(context)
        provider=TestMediaProvider()
        provider.attachInfo(context,ProviderInfo().apply { authority=MediaStore.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY,provider)
        val original=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(80,120,160)) }
        store.original(id).outputStream().use { check(original.compress(Bitmap.CompressFormat.JPEG,100,it)) }
        original.recycle(); store.save(n)
    }
    @Test fun originalSizeLoadAndBothExportModesSucceed() = runBlocking {
        val before=store.original(id).readBytes()
        val raw=store.load(id,Int.MAX_VALUE)
        assertEquals(80,raw.width); assertEquals(60,raw.height)
        val developed=develop(raw,n.film,n.adjust)
        for(border in listOf(false,true)) {
            assertTrue(store.export(developed,n,border).contains("已保存"))
            val record=provider.rows.values.last()
            assertEquals(0,record.values.getAsInteger(MediaStore.Images.Media.IS_PENDING).toInt())
            assertEquals("Pictures/光室",record.values.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
            val jpeg=BitmapFactory.decodeFile(record.file.path)
            assertEquals(if(border)112 else 80,jpeg.width)
            assertEquals(if(border)108 else 60,jpeg.height)
            jpeg.recycle()
        }
        assertArrayEquals(before,store.original(id).readBytes())
        assertEquals(n,FilmStore(context).list().first { it.id==id })
        raw.recycle(); developed.recycle()
    }
    @Test fun failedWriteDeletesPendingRowAndPreservesNegative() = runBlocking {
        val before=store.original(id).readBytes()
        provider.failWrite=true
        val raw=store.load(id,Int.MAX_VALUE)
        var failed=false
        try { store.export(raw,n,true) } catch(_:Exception) { failed=true }
        assertTrue("写入异常必须被传播",failed)
        assertTrue(provider.rows.isEmpty())
        assertArrayEquals(before,store.original(id).readBytes())
        assertTrue(store.list().any { it.id==id })
        raw.recycle()
    }
    @Test fun orientationIsAppliedBeforeExport() {
        ExifInterface(store.original(id)).apply { setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        val raw=store.load(id,Int.MAX_VALUE)
        assertEquals(60,raw.width); assertEquals(80,raw.height); raw.recycle()
    }
    @Test fun v01NegativeMigratesWithoutLosingFilm() {
        File(store.dir,"$id.json").writeText("""{"film":"NIGHT","grain":0.28,"exposure":0.5,"cyan":0.1,"magenta":0.2,"yellow":0.3}""")
        val old=FilmStore(context).list().first { it.id==id }
        assertEquals(Film.NIGHT,old.film); assertEquals(16f,old.adjust.aperture)
        assertEquals(24,old.focalMm); assertEquals(.5f,old.adjust.exposure)
    }
    @Test fun apertureLeavesTappedCenterSharpAndDoesNotModifyOriginal() = runBlocking {
        val raw=Bitmap.createBitmap(120,160,Bitmap.Config.ARGB_8888)
        for(y in 0 until raw.height)for(x in 0 until raw.width)raw.setPixel(x,y,if((x/3+y/3)%2==0)Color.BLACK else Color.WHITE)
        val result=develop(raw,Film.NATURAL,Adjust(aperture=1.4f,focusX=.5f,focusY=.5f))
        assertEquals(raw.getPixel(60,80),result.getPixel(60,80))
        assertNotEquals(raw.getPixel(1,1),result.getPixel(1,1))
        assertEquals(Color.BLACK,raw.getPixel(1,1))
        raw.recycle();result.recycle()
    }
    @Test fun batchTrashSurvivesRestartAndRestoresOriginalsAndEdits() {
        val second=n.copy(id="1700000000001_test",adjust=Adjust(exposure=.7f))
        store.original(id).copyTo(store.original(second.id),overwrite=true); store.save(second)
        val before=store.original(id).readBytes()
        listOf(id,second.id).forEach { store.setDeleted(it,true) }
        val restarted=FilmStore(context)
        assertFalse(restarted.list().any { it.id in listOf(id,second.id) })
        assertTrue(restarted.list(true).map { it.id }.containsAll(listOf(id,second.id)))
        // Late editor disposal must not resurrect a deleted negative.
        restarted.save(n.copy(adjust=Adjust(exposure=1f)))
        assertFalse(restarted.list().any { it.id==id })
        listOf(id,second.id).forEach { restarted.setDeleted(it,false) }
        val restored=FilmStore(context)
        assertEquals(second,restored.list().first { it.id==second.id })
        assertEquals(1f,restored.list().first { it.id==id }.adjust.exposure)
        assertFalse(restored.list(true).any { it.id in listOf(id,second.id) })
        assertArrayEquals(before,restored.original(id).readBytes())
    }
    @Test fun deletionIsIdempotentAndUnknownNegativeDoesNotCreateMarker() {
        repeat(2) {store.setDeleted(id,true)}
        repeat(2) {store.setDeleted(id,false)}
        assertTrue(store.list().any {it.id==id})
        assertThrows(IllegalStateException::class.java) {store.setDeleted("missing",true)}
        assertFalse(File(store.dir,"missing.deleted").exists())
    }
    data class Row(val file:File,val values:ContentValues)
    class TestMediaProvider: ContentProvider() {
        val rows=linkedMapOf<Uri,Row>()
        var failWrite=false
        override fun onCreate()=true
        override fun insert(uri:Uri,values:ContentValues?):Uri {
            val target=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,(rows.size+1).toLong())
            rows[target]=Row(File.createTempFile("export",".jpg",context!!.cacheDir),ContentValues(values))
            if(failWrite) org.robolectric.Shadows.shadowOf(context!!.contentResolver).registerOutputStream(target,object:java.io.OutputStream() {
                override fun write(value:Int) { throw java.io.IOException("模拟相册写入失败") }
            })
            return target
        }
        override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
            if(failWrite)throw java.io.FileNotFoundException("模拟相册写入失败")
            return ParcelFileDescriptor.open(rows.getValue(uri).file,ParcelFileDescriptor.MODE_READ_WRITE)
        }
        override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?):Int {
            rows[uri]?.values?.putAll(values);return if(rows.containsKey(uri))1 else 0
        }
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?):Int { rows.remove(uri)?.file?.delete();return 1 }
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor?=null
        override fun getType(uri:Uri)="image/jpeg"
    }
}
