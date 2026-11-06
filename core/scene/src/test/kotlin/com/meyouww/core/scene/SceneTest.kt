package com.meyouww.core.scene

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class SceneTest {

    private fun near(a: Float, b: Float, eps: Float = 1e-3f) = abs(a - b) <= eps

    @Test
    fun `cover fills the target and crops evenly`() {
        // A 9:20 design on a 1080x2400 phone: exact fit, nothing cropped.
        val t = SceneTransform.cover(20f / 9f, 1080, 2400)
        val box = t.visibleDesignBox(1080, 2400)
        assertThat(near(box.left, 0f)).isTrue()
        assertThat(near(box.right, 1f)).isTrue()
        assertThat(near(box.bottom, 20f / 9f)).isTrue()

        // The same design on a 16:9 phone (1080x1920): the sides fit, top and bottom are cropped equally.
        val short = SceneTransform.cover(20f / 9f, 1080, 1920)
        val b2 = short.visibleDesignBox(1080, 1920)
        assertThat(near(b2.left, 0f)).isTrue()
        assertThat(near(b2.top, (20f / 9f - 1920f / 1080f) / 2f)).isTrue()
        assertThat(near(b2.top, 20f / 9f - b2.bottom)).isTrue()
    }

    @Test
    fun `cover never stretches - one scale for both axes`() {
        val t = SceneTransform.cover(2f, 1000, 3000)
        // 1 design unit horizontally and vertically map to the same pixel distance.
        assertThat(near(t.toPxX(1f) - t.toPxX(0f), t.toPxY(1f) - t.toPxY(0f))).isTrue()
    }

    /**
     * The consistency guarantee: the preview (any size) and the exported wallpaper
     * show exactly the same part of the design, across phone shapes.
     */
    @Test
    fun `preview and export crop the same region across aspect ratios`() {
        val phones = listOf(
            1080 to 2340, // 19.5:9
            1080 to 2400, // 20:9
            1080 to 2520, // 21:9
            1080 to 1920, // 16:9
            1812 to 2176, // foldable inner display
            2208 to 1840, // foldable inner, landscape-ish
        )
        val designs = listOf(20f / 9f, 16f / 9f, 1.2f, 21f / 9f)
        for (aspect in designs) {
            for ((w, h) in phones) {
                val (ew, eh) = ExportPlan.size(w, h, aspect)
                val export = SceneTransform.cover(aspect, ew, eh).visibleDesignBox(ew, eh)
                for (previewWidth in listOf(240, 360, 411)) {
                    val ph = (previewWidth.toFloat() * h / w).toInt()
                    val preview = SceneTransform.cover(aspect, previewWidth, ph).visibleDesignBox(previewWidth, ph)
                    val tolerance = 2f / previewWidth // rounding of the preview's pixel height
                    assertThat(abs(preview.left - export.left)).isLessThan(tolerance)
                    assertThat(abs(preview.top - export.top)).isLessThan(tolerance * aspect)
                    assertThat(abs(preview.right - export.right)).isLessThan(tolerance)
                    assertThat(abs(preview.bottom - export.bottom)).isLessThan(tolerance * aspect)
                }
            }
        }
    }

    @Test
    fun `focus moves the crop without changing its size`() {
        val top = SceneTransform.cover(20f / 9f, 1080, 1920, focusY = 0f).visibleDesignBox(1080, 1920)
        val bottom = SceneTransform.cover(20f / 9f, 1080, 1920, focusY = 1f).visibleDesignBox(1080, 1920)
        assertThat(near(top.top, 0f)).isTrue()
        assertThat(near(bottom.bottom, 20f / 9f)).isTrue()
        assertThat(near(top.height, bottom.height)).isTrue()
    }

    @Test
    fun `fit shows the whole design centred`() {
        val t = SceneTransform.fit(2f, 400f, 1000f)
        assertThat(near(t.scale, 400f)).isTrue()
        assertThat(near(t.offsetY, 100f)).isTrue()
        assertThat(near(t.toDesignX(t.toPxX(0.3f)), 0.3f)).isTrue()
    }

    @Test
    fun `export size respects the recipient and the 1440x3200 cap`() {
        assertThat(ExportPlan.size(1080, 2400, 2f)).isEqualTo(1080 to 2400)
        assertThat(ExportPlan.size(1440, 3200, 2f)).isEqualTo(1440 to 3200)
        val (w, h) = ExportPlan.size(1600, 3600, 2f)
        assertThat(w).isAtMost(1440)
        assertThat(h).isAtMost(3200)
        assertThat(abs(w.toFloat() / h - 1600f / 3600f)).isLessThan(0.002f)
        assertThat(ExportPlan.size(null, null, 20f / 9f)).isEqualTo(1440 to 3200)
        assertThat(ExportPlan.size(0, 100, 1f)).isEqualTo(1440 to 1440)
    }

    @Test
    fun `photos switch export to webp`() {
        val doodle = Scene(elements = listOf(StrokeElement("s", Tool.Crayon, 0, 0.01f, 1f, listOf(0f, 0f, 1f, 0.1f, 0.1f, 1f))))
        assertThat(ExportPlan.usePng(doodle)).isTrue()
        val photo = SceneEdits.add(doodle, PhotoElement("p", "a.webp", 0.5f, 1f, 0.8f, 1.3f))
        assertThat(ExportPlan.usePng(photo)).isFalse()
    }

    @Test
    fun `scenes survive a JSON round trip`() {
        val scene = Scene(
            aspect = 2.1f,
            background = Background.Gradient(0xFFFFE0F0.toInt(), 0xFFB8E6FF.toInt()),
            elements = listOf(
                StrokeElement("s1", Tool.Neon, 0xFF00FF00.toInt(), 0.02f, 0.8f, listOf(0.1f, 0.2f, 0.5f, 0.3f, 0.4f, 0.9f)),
                ShapeElement("h1", ShapeKind.Heart, 0.5f, 1f, 0.3f, 0.3f, 15f, 0xFFFF0000.toInt(), 0.01f, filled = true),
                TextElement("t1", "I love you\nWhity", 0.5f, 0.4f, 0.08f, 0xFF000000.toInt(), FontChoice.Handwritten),
                StickerElement("k1", "Love", 0.3f, 1.5f, 0.25f, -10f),
                StickerElement("k2", "emoji:🌸", 0.7f, 1.5f, 0.2f),
                PhotoElement("p1", "photo_1.webp", 0.5f, 1.1f, 0.9f, 1.33f, 5f, 0.9f, 0.1f),
            ),
        )
        val text = SceneJson.encode(scene)
        assertThat(SceneJson.decode(text)).isEqualTo(scene)
        assertThat(text).contains("\"type\":\"stroke\"")
        assertThat((SceneJson.decode(text).elements[4] as StickerElement).emoji).isEqualTo("🌸")
    }

    @Test
    fun `old or newer drafts with unknown fields still load`() {
        val text = """{"version":1,"aspect":2.0,"future":"x","background":{"type":"solid","color":-1},"elements":[]}"""
        assertThat(SceneJson.decode(text).aspect).isEqualTo(2f)
    }

    @Test
    fun `stroke builder skips tiny moves and turns a tap into a dot`() {
        val b = StrokeBuilder(minStep = 0.01f)
        b.add(0.5f, 0.5f, 0.7f)
        b.add(0.501f, 0.5f, 0.7f)
        assertThat(b.size).isEqualTo(1)
        val dot = b.build("d", Tool.Marker, 0, 0.02f, 1f)!!
        assertThat(dot.pointCount).isEqualTo(2)
        b.clear()
        assertThat(b.build("x", Tool.Marker, 0, 0.02f, 1f)).isNull()
        b.add(0f, 0f, 5f)
        assertThat(b.snapshot()[2]).isEqualTo(1f)
    }

    @Test
    fun `history undoes and redoes and forgets redo after a new change`() {
        val h = History(limit = 2)
        val a = Scene(aspect = 1f)
        val b = Scene(aspect = 2f)
        val c = Scene(aspect = 3f)
        h.record(a)
        h.record(b)
        assertThat(h.undo(c)).isEqualTo(b)
        assertThat(h.redo(b)).isEqualTo(c)
        assertThat(h.undo(c)).isEqualTo(b)
        h.record(b)
        assertThat(h.canRedo).isFalse()
        h.record(c)
        h.record(c)
        var count = 0
        var cur = c
        while (true) {
            cur = h.undo(cur) ?: break
            count++
        }
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `selection finds the top-most placed element`() {
        val below = StickerElement("a", "Love", 0.5f, 1f, 0.4f)
        val above = StickerElement("b", "Hi", 0.55f, 1f, 0.2f)
        val stroke = StrokeElement("s", Tool.Marker, 0, 0.05f, 1f, listOf(0.5f, 1f, 1f, 0.6f, 1f, 1f))
        val scene = Scene(elements = listOf(below, above, stroke))
        assertThat(scene.placedAt(0.55f, 1f)?.id).isEqualTo("b")
        assertThat(scene.placedAt(0.36f, 1f)?.id).isEqualTo("a")
        assertThat(scene.placedAt(0.95f, 0.1f)).isNull()
    }

    @Test
    fun `edits move, scale, reorder and duplicate`() {
        val s = StickerElement("a", "Love", 0.5f, 1f, 0.4f)
        val moved = SceneEdits.moveBy(s, 0.1f, -0.2f) as StickerElement
        assertThat(near(moved.x, 0.6f) && near(moved.y, 0.8f)).isTrue()
        val scaled = SceneEdits.scaleRotate(s, 100f, 200f) as StickerElement
        assertThat(scaled.size).isEqualTo(1.5f)
        assertThat(scaled.rotation).isEqualTo(-160f)

        val t = TextElement("t", "hi", 0.5f, 0.5f, 0.1f, 0)
        var scene = Scene(elements = listOf(s, t))
        scene = SceneEdits.reorder(scene, "a", up = true)
        assertThat(scene.elements.map { it.id }).containsExactly("t", "a").inOrder()
        assertThat(SceneEdits.reorder(scene, "a", up = true)).isEqualTo(scene)
        scene = SceneEdits.duplicate(scene, "a", "a2")
        assertThat(scene.elements.last().id).isEqualTo("a2")
        assertThat(SceneEdits.remove(scene, "a2").elements).hasSize(2)
    }

    @Test
    fun `rotated boxes grow to contain the rotation`() {
        val flat = ShapeElement("r", ShapeKind.Rectangle, 0.5f, 0.5f, 0.4f, 0.1f, 0f, 0, 0.01f).bounds()
        val turned = ShapeElement("r", ShapeKind.Rectangle, 0.5f, 0.5f, 0.4f, 0.1f, 90f, 0, 0.01f).bounds()
        assertThat(near(flat.width, 0.4f)).isTrue()
        assertThat(near(turned.width, 0.1f)).isTrue()
        assertThat(near(turned.height, 0.4f)).isTrue()
    }
}
