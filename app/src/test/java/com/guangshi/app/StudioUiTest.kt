package com.guangshi.app

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w412dp-h915dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var model:StudioModel
    private lateinit var rootView:android.view.View
    @Before fun start() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        val store=FilmStore(app)
        val raw=Bitmap.createBitmap(240,320,Bitmap.Config.ARGB_8888)
        for(y in 0 until raw.height) for(x in 0 until raw.width) raw.setPixel(x,y,Color.rgb((x*255/240),y*255/320,140))
        store.original("1700000000000_uitest").outputStream().use { raw.compress(Bitmap.CompressFormat.JPEG,100,it) };raw.recycle()
        store.save(Negative("1700000000000_uitest",Film.DAY,Adjust(grain=.18f)))
        model=StudioModel(app)
        compose.setContent { rootView=LocalView.current; GuangshiTheme { Studio(model) } }
    }
    @Test fun sixFiltersAndPermissionScreenAreAccessible() {
        compose.onNodeWithText("开启相机").assertExists()
        compose.onNodeWithText("暖日光").performClick()
        compose.onNodeWithText("金色高光，温暖肤色").assertExists()
        compose.onNodeWithText("曝光").performClick()
        compose.onNodeWithContentDescription("曝光 EV").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.65f) }
        compose.runOnIdle { Assert.assertEquals(.7f,model.cameraAdjust.exposure,.001f) }
        compose.onNodeWithContentDescription("快门").assertIsDisplayed()
        saveScreenshot("camera")
    }
    @Test fun darkroomKeepsImageAboveInteractiveColorSliders() {
        compose.waitUntil(10000) { model.negatives.isNotEmpty() }
        compose.onNodeWithText("暗房 ↗").performClick()
        compose.onNodeWithText("色彩").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("实时画面").fetchSemanticsNodes().isNotEmpty() }
        val originalRed=centerRed()
        compose.onNodeWithContentDescription("红 R").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.6f) }
        compose.onNodeWithText("0.6").assertExists()
        compose.waitUntil(10000) { centerRed()>originalRed+15 }
        compose.onNodeWithText("冲印 · 导出到系统相册").assertExists()
        compose.onNodeWithContentDescription("实时画面").assertExists()
        saveScreenshot("darkroom")
    }
    @Test @Config(qualifiers="w320dp-h480dp-port-xxhdpi")
    fun compactWindowCanReachExportWithoutClippedControls() {
        compose.waitUntil(10000) { model.negatives.isNotEmpty() }
        compose.onNodeWithText("暗房 ↗").performClick()
        compose.onNodeWithText("冲印 · 导出到系统相册").performScrollTo().assertIsDisplayed()
        saveScreenshot("compact")
    }
    @Test fun controlsStayAboveLargePreviewAndNeverResizeIt() {
        val frame=compose.onNodeWithContentDescription("相机取景区域").fetchSemanticsNode().boundsInRoot
        val top=compose.onNodeWithText("曝光").fetchSemanticsNode().boundsInRoot
        Assert.assertTrue(top.bottom<=frame.top)
        Assert.assertTrue(frame.height>=frame.width*1.25f)
        compose.onNodeWithText("曝光").performClick()
        val expanded=compose.onNodeWithContentDescription("相机取景区域").fetchSemanticsNode().boundsInRoot
        Assert.assertEquals(frame,expanded)
        compose.onNodeWithText("24 ↻ 28 / 35    73 ↻ 85").assertDoesNotExist()
        compose.onNodeWithContentDescription("曝光 EV 增加 0.1").performClick()
        compose.runOnIdle {Assert.assertEquals(.1f,model.cameraAdjust.exposure)}
    }
    @Test fun multiSelectDeletionAndTrashRecoveryWorkWhenBoxBecomesEmpty() {
        compose.waitUntil(10000) { model.negatives.isNotEmpty() }
        compose.onNodeWithText("暗房 ↗").performClick()
        compose.onNode(hasText("管理底片 · 多选删除",substring=true)).performClick()
        compose.onNodeWithText("全选").performClick()
        compose.onNodeWithText("删除所选（1）").performClick()
        compose.onNodeWithText("移入回收站").performClick()
        compose.waitUntil(10000) { compose.waitForIdle(); !model.managing && model.negatives.isEmpty() && model.trash.size==1 }
        compose.onNodeWithText("回收站（1）").performClick()
        compose.onNodeWithText("全选").performClick()
        compose.onNodeWithText("恢复所选（1）").performClick()
        compose.waitUntil(10000) { compose.waitForIdle(); !model.managing && model.negatives.size==1 && model.trash.isEmpty() }
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("冲印 · 导出到系统相册").assertExists()
    }
    private fun centerRed():Int {
        val center=compose.onNodeWithContentDescription("实时画面").fetchSemanticsNode().boundsInRoot.center
        var value=0
        compose.runOnIdle {
            val bitmap=Bitmap.createBitmap(rootView.width,rootView.height,Bitmap.Config.ARGB_8888)
            rootView.draw(android.graphics.Canvas(bitmap))
            value=Color.red(bitmap.getPixel(center.x.toInt(),center.y.toInt()));bitmap.recycle()
        }
        return value
    }
    private fun saveScreenshot(name:String) {
        compose.waitForIdle()
        lateinit var image:Bitmap
        compose.runOnIdle {
            image=Bitmap.createBitmap(rootView.width,rootView.height,Bitmap.Config.ARGB_8888)
            rootView.draw(android.graphics.Canvas(image))
        }
        val dir=File("build/reports/screenshots").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
