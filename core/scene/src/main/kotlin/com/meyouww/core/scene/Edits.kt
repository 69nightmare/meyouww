package com.meyouww.core.scene

import kotlinx.serialization.json.Json

/** Scene <-> JSON for drafts. Unknown fields from newer versions are ignored. */
object SceneJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(scene: Scene): String = json.encodeToString(Scene.serializer(), scene)

    fun decode(text: String): Scene = json.decodeFromString(Scene.serializer(), text)
}

/** Builds a stroke while the finger or pen moves, dropping points that add nothing. */
class StrokeBuilder(
    private val minStep: Float = 0.0015f,
) {
    private val points = ArrayList<Float>(600)
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    val isEmpty: Boolean get() = points.isEmpty()
    val size: Int get() = points.size / 3

    fun add(x: Float, y: Float, pressure: Float) {
        if (!lastX.isNaN() && distance(lastX, lastY, x, y) < minStep) return
        points += x
        points += y
        points += pressure.coerceIn(0.05f, 1f)
        lastX = x
        lastY = y
    }

    /** A copy of the points so far, for drawing the stroke in progress. */
    fun snapshot(): List<Float> = ArrayList(points)

    fun build(id: String, tool: Tool, color: Int, width: Float, opacity: Float): StrokeElement? {
        if (points.isEmpty()) return null
        // A single tap becomes a dot: repeat the point so renderers draw a round cap.
        if (points.size == 3) {
            points += points[0]
            points += points[1]
            points += points[2]
        }
        return StrokeElement(id, tool, color, width, opacity, ArrayList(points))
    }

    fun clear() {
        points.clear()
        lastX = Float.NaN
        lastY = Float.NaN
    }
}

/** Pure edits used by the studio. Each returns a new scene. */
object SceneEdits {
    fun add(scene: Scene, element: Element): Scene = scene.copy(elements = scene.elements + element)

    fun remove(scene: Scene, id: String): Scene = scene.copy(elements = scene.elements.filterNot { it.id == id })

    fun replace(scene: Scene, element: Element): Scene =
        scene.copy(elements = scene.elements.map { if (it.id == element.id) element else it })

    fun setBackground(scene: Scene, background: Background): Scene = scene.copy(background = background)

    fun clear(scene: Scene): Scene = scene.copy(elements = emptyList())

    /** Moves an element one step up (toward the viewer) or down. */
    fun reorder(scene: Scene, id: String, up: Boolean): Scene {
        val list = scene.elements.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return scene
        val j = if (up) i + 1 else i - 1
        if (j !in list.indices) return scene
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
        return scene.copy(elements = list)
    }

    fun duplicate(scene: Scene, id: String, newId: String, offset: Float = 0.04f): Scene {
        val e = scene.element(id) as? Placed ?: return scene
        return add(scene, moveBy(e, offset, offset).withId(newId))
    }

    fun moveBy(e: Placed, dx: Float, dy: Float): Placed = when (e) {
        is ShapeElement -> e.copy(x = e.x + dx, y = e.y + dy)
        is TextElement -> e.copy(x = e.x + dx, y = e.y + dy)
        is StickerElement -> e.copy(x = e.x + dx, y = e.y + dy)
        is PhotoElement -> e.copy(x = e.x + dx, y = e.y + dy)
    }

    /** Pinch-zoom and twist. Sizes are kept within sensible limits. */
    fun scaleRotate(e: Placed, zoom: Float, rotateDegrees: Float): Placed {
        val z = zoom.coerceIn(0.2f, 5f)
        val r = normalizeDegrees(e.rotation + rotateDegrees)
        return when (e) {
            is ShapeElement -> e.copy(w = (e.w * z).limit(), h = (e.h * z).limit(), rotation = r)
            is TextElement -> e.copy(size = (e.size * z).coerceIn(0.02f, 0.6f), rotation = r)
            is StickerElement -> e.copy(size = (e.size * z).coerceIn(0.05f, 1.5f), rotation = r)
            is PhotoElement -> e.copy(width = (e.width * z).coerceIn(0.1f, 3f), rotation = r)
        }
    }

    private fun Float.limit(): Float = if (this >= 0) coerceIn(0.01f, 3f) else coerceIn(-3f, -0.01f)

    private fun normalizeDegrees(d: Float): Float {
        var v = d % 360f
        if (v > 180f) v -= 360f
        if (v < -180f) v += 360f
        return v
    }

    private fun Placed.withId(id: String): Placed = when (this) {
        is ShapeElement -> copy(id = id)
        is TextElement -> copy(id = id)
        is StickerElement -> copy(id = id)
        is PhotoElement -> copy(id = id)
    }
}

/** Undo / redo over immutable scenes. */
class History(private val limit: Int = 100) {
    private val undo = ArrayDeque<Scene>()
    private val redo = ArrayDeque<Scene>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /** Call with the scene as it was BEFORE a change. */
    fun record(before: Scene) {
        undo.addLast(before)
        if (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun undo(current: Scene): Scene? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        return previous
    }

    fun redo(current: Scene): Scene? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        return next
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }
}
