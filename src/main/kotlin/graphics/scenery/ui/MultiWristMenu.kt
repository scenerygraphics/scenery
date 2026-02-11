package graphics.scenery.ui

import graphics.scenery.BoundingGrid
import graphics.scenery.Mesh
import graphics.scenery.Node
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Manages a set of wrist-mounted menu columns on a VR controller, with support for cycling between them.
 * Columns can contain [Button]s directly or grouped inside [Row]s (or any other [Gui3DElement]).
 *
 * Usage:
 * ```
 *      val menu = MultiWristMenu(parentNode = leftVRController?.model!!)
 *      menu.addColumn("Tools")
 *      menu.addButton("Tools") { doSomething() }
 *      menu.addButton("Tools") { doSomethingElse() }
 *      menu.addRow("Tools", buttonA, buttonB, buttonC) // Define these buttons somewhere first
 *      // Somewhere in your controller input handling:
 *      menu.cycleNext()
 * ```
 * @param parentNode The node to attach this menu to (typically the VR controller model)
 * @param columnScale Allows scaling the menu down. A good default value is 0.05 for Quest 2 controllers
 * @param columnBasePosition Adjust this value to move the position relative to the controller
 * @param columnRotation menu rotation. Default value fits the left controller
 * @param defaultColor Base color of new buttons. Can be overwritten in [addButton]
 * @param defaultPressedColor Color when button is pressed. Can be overwritten in [addButton]
 * @param defaultTouchingColor Color when the button is touched. Can be overwritten in [addButton]
 * @author Samuel Pantze
 */
class MultiWristMenu(
    /** The scene node to which all menu columns will be parented (e.g. a controller model node). */
    private val parentNode: Node,
    /** Scale applied to every column when it is attached to the parent. */
    private val columnScale: Float = 0.04f,
    /** Base position offset for the columns relative to the parent. The z-component is
     *  adjusted per-column based on its packed height; see [attachColumn]. */
    private val columnBasePosition: Vector3f = Vector3f(0.05f, 0.05f, 0.1f),
    /** Rotation applied to every column (matches the default wrist orientation). */
    private val columnRotation: Quaternionf = Quaternionf().rotationXYZ(-1.57f, 1.57f, 0f),

    defaultColor: Vector3f = Vector3f(0.8f),
    defaultPressedColor: Vector3f = Vector3f(0.95f, 0.35f, 0.25f),
    defaultTouchingColor: Vector3f = Vector3f(0.7f, 0.55f, 0.55f)
): Mesh() {

    /** Default resting color for buttons. */
    var defaultColor = defaultColor
        private set
    /** Default pressed color for buttons. */
    var defaultPressedColor = defaultPressedColor
        private set
    /** Default touch-hover color for buttons. */
    var defaultTouchingColor = defaultTouchingColor
        private set
    /** Ordered map: column name → its [Column] node.
     * Insertion order is preserved (LinkedHashMap), so cycling follows the order
     * in which [addColumn] was called. */
    private val columns: LinkedHashMap<String, Column> = linkedMapOf()

    /** Index into [columns.values] that points to the currently visible column. */
    private var currentIndex = 0

    /** Creates a new, empty [Column] and registers it under [name].
     * The column is immediately parented to [parentNode] and hidden.
     * Throws [IllegalArgumentException] if a column with that name already exists. */
    fun addColumn(name: String) {
        require(!columns.containsKey(name)) {
            "A column named \"$name\" already exists in this MultiWristMenu."
        }

        // Empty column; elements are added later via addButton / addRow / addElement.
        val column = Column(
            centerVertically = false,
            centerHorizontally = true,
            invertedYOrder = true,
            anchor = Gui3DElement.Anchor.Top
        )
        column.name = name
        // hidden by default; only the "current" one is shown
        column.visible = false

        attachColumn(column)
        columns[name] = column

    }

    /** Returns the [Column] registered under [name], or null.
     * Useful when you need to manipulate a column directly (e.g. force a [pack] call
     * after bulk-adding elements outside of this class). */
    fun getColumn(name: String): Column? = columns[name]

    /** Returns an unmodifiable view of all column names in insertion order. */
    fun columnNames(): List<String> = columns.keys.toList()

    /**
     * Creates a [Button] with the class-level color presets (or the optionally
     * supplied per-button colors) and appends it to the column named [columnName].
     * @param columnName   Target column (must have been created with [addColumn]).
     * @param label        The button's display text.
     * @param command      Action executed when the button is activated.
     * @param byTouch      If true the button fires on touch-hold; otherwise on press.
     * @param stayPressed  If true the button remains visually depressed until explicitly released.
     * @param depressDelay Milliseconds before the button visually returns to the unpressed state.
     * @param color        Resting color override (defaults to [defaultColor]).
     * @param pressedColor Pressed color override (defaults to [defaultPressedColor]).
     * @param touchingColor Touch-hover color override (defaults to [defaultTouchingColor]).
     * @return The newly created [Button], so you can store a reference if needed.
     */
    fun addButton(
        columnName: String,
        label: String,
        command: () -> Unit,
        byTouch: Boolean = true,
        stayPressed: Boolean = false,
        depressDelay: Int = 250,
        color: Vector3f = defaultColor,
        pressedColor: Vector3f = defaultPressedColor,
        touchingColor: Vector3f = defaultTouchingColor
    ): Button {
        val column = requireColumn(columnName)

        val button = Button(
            text = label, command = command, byTouch = byTouch, stayPressed = stayPressed, depressDelay = depressDelay,
            defaultColor = color, pressedColor = pressedColor, touchingColor = touchingColor
        )
        column.addChild(button)
        column.onGeometryReady {
            column.pack()
            // Make column top-aligned
            column.ifSpatial {
                position.y = -column.height * columnScale + columnBasePosition.y
                needsUpdate = true
            }
        }
        return button
    }

    /**
     * Creates a [ToggleButton] with the class-level color presets (or the optionally
     * supplied per-button colors) and appends it to the column named [columnName].
     * @param columnName   Target column (must have been created with [addColumn]).
     * @param labelFalse   The button's display text if toggled off.
     * @param labelTrue    The button's display text if toggled on.
     * @param command      Action executed when the button is activated.
     * @param byTouch      If true the button fires on touch-hold; otherwise on press.
     * @param color        Resting color override (defaults to [defaultColor]).
     * @param pressedColor Pressed color override (defaults to [defaultPressedColor]).
     * @param touchingColor Touch-hover color override (defaults to [defaultTouchingColor]).
     * @param defaultState Default state of the button.
     * @return The newly created [ToggleButton], so you can store a reference if needed.
     */
    fun addToggleButton(
        columnName: String,
        labelFalse: String,
        labelTrue: String,
        command: () -> Unit,
        byTouch: Boolean = true,
        color: Vector3f = defaultColor,
        pressedColor: Vector3f = defaultPressedColor,
        touchingColor: Vector3f = defaultTouchingColor,
        defaultState: Boolean = false
    ): ToggleButton {
        val column = requireColumn(columnName)

        val button = ToggleButton(labelFalse, labelTrue, command = command, byTouch = byTouch,
            defaultColor = color, pressedColor = pressedColor, touchingColor = touchingColor, default = defaultState)

        column.addChild(button)
        column.onGeometryReady {
            column.pack()
            // Make column top-aligned
            column.ifSpatial {
                position.y = -column.height * columnScale + columnBasePosition.y
                needsUpdate = true
            }
        }
        return button
    }

    /** Creates a [Row] from the supplied pre-built [Gui3DElement]s and appends it to the column named [columnName].
     * This is the natural way to add a horizontal group of buttons that were constructed elsewhere.

     * Note: if the elements were already added to a column via [addButton] you will want to remove them first,
     * or simply build the buttons manually and pass them here directly.
     * @param columnName Target column.
     * @param elements   The [Gui3DElement]s that make up the row.
     * @param margin     Horizontal spacing between elements in the row.
     * @param middleAlign If true the row is horizontally centred around its origin.
     * @return The newly created [Row]. */
    fun addRow(
        columnName: String,
        vararg elements: Gui3DElement,
        margin: Float = 0.5f,
        middleAlign: Boolean = true
    ): Row {
        val column = requireColumn(columnName)

        val row = Row(*elements, margin = margin, middleAlign = middleAlign)
        column.addChild(row)
        column.pack()
        return row
    }

    /** Appends an arbitrary [Gui3DElement] to the named column.
     * This is a general-purpose escape method when [addButton] / [addRow] don't work. */
    fun addElement(columnName: String, element: Gui3DElement) {
        val column = requireColumn(columnName)
        column.addChild(element)
        column.pack()
    }

    /** Positions and parents a [Column] node to [parentNode].
     * The z-offset is nudged by the column's height so that taller menus don't clip into the controller model. */
    private fun attachColumn(column: Column) {
        column.ifSpatial {
            scale.set(columnScale)
            position.set(columnBasePosition)
            rotation.set(columnRotation)
        }
        parentNode.addChild(column)
    }

    /** Looks up a column by name or throws an exception. */
    private fun requireColumn(name: String): Column =
        columns[name] ?: throw IllegalArgumentException(
            "Column \"$name\" does not exist. Available columns: ${columns.keys}"
        )

    /** The name of the column that is currently visible, or null if no columns exist. */
    fun currentColumnName(): String? = columns.keys.toList().getOrNull(currentIndex)

    /** Advances to the next column in insertion order, wrapping around.
     * Call this from a VR controller button behaviour. */
    fun cycleNext() {
        if (columns.isEmpty()) return
        val list = columns.values.toList()
        list[currentIndex].visible = false
        currentIndex = (currentIndex + 1) % list.size
        list[currentIndex].visible = true
        logger.debug("Cycled to menu \"${list[currentIndex].name}\"")
    }

    /** Jumps directly to the column named [name]. Useful when an external event should surface a particular menu page. */
    fun jumpTo(name: String) {
        val targetIndex = columns.keys.indexOf(name)
        require(targetIndex >= 0) { "No column named \"$name\" exists." }
        val list = columns.values.toList()
        list[currentIndex].visible = false
        currentIndex = targetIndex
        list[currentIndex].visible = true
        logger.debug("Jumped to menu \"$name\"")
    }

    /** Iterates over every [Button] in every column, including buttons that are nested inside [Row]s (one level deep). */
    private inline fun forEachButton(action: (Button) -> Unit) {
        for (column in columns.values) {
            for (child in column.children) {
                when (child) {
                    is Button -> action(child)
                    is Row -> child.children.filterIsInstance<Button>().forEach(action)
                }
            }
        }
    }

    /** Convenience: hide the menu entirely (all columns). */
    fun hideAll() { columns.values.forEach { it.visible = false } }

    /** Shows or hides the entire menu (only the currently-selected column is affected). */
    fun toggleVisibility(visible: Boolean) {
        columns.values.toList().getOrNull(currentIndex)?.visible = visible
    }

    /**
     * Overwrites [defaultColor] and re-applies it to every [Button] that is currently using the *old*
     * default resting color (i.e. buttons whose color was never individually overridden).
     * Buttons with a custom color are left untouched.
     * If you want to update *all* buttons unconditionally, use [setAllButtonColors].
     */
    fun setDefaultColor(color: Vector3f) {
        val old = defaultColor
        defaultColor = color
        forEachButton { if (it.defaultColor == old) it.box.material().diffuse = color }
    }

    /** Same logic as [setDefaultColor], but for [defaultPressedColor]. */
    fun setDefaultPressedColor(color: Vector3f) {
        defaultPressedColor = color
    }

    /** Same logic as [setDefaultColor], but for [defaultTouchingColor]. */
    fun setDefaultTouchingColor(color: Vector3f) {
        defaultTouchingColor = color
    }

    /** Unconditionally sets the resting, pressed, and touching colors on every
     * [Button] in every column. Handy for a global theme change. */
    fun setAllButtonColors(
        color: Vector3f = defaultColor,
        pressedColor: Vector3f = defaultPressedColor,
        touchingColor: Vector3f = defaultTouchingColor
    ) {
        forEachButton { button ->
            button.box.material().diffuse = color
             button.pressedColor  = pressedColor
             button.touchingColor = touchingColor
        }
    }


}
