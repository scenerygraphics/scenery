package graphics.scenery.ui

import glm_.L
import glm_.b
import glm_.f
import glm_.i
import glm_.vec2.Vec2
import glm_.vec2.Vec2d
import glm_.vec2.Vec2i
import glm_.vec4.Vec4
import graphics.scenery.Hub
import graphics.scenery.Material
import graphics.scenery.Mesh
import graphics.scenery.ShaderMaterial
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.backends.ShaderType
import graphics.scenery.controls.GLFWMouseAndKeyHandler
import graphics.scenery.controls.InputHandler
import graphics.scenery.textures.Texture
import imgui.*
import imgui.classes.Context
import imgui.impl.glfw.ImplGlfw
import imgui.impl.mouseCursors
import imgui.impl.mouseJustPressed
import imgui.impl.time
import kool.*
import kool.lib.fill
import org.joml.Vector2f
import org.joml.Vector2i
import org.joml.Vector3i
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryUtil.*
import uno.glfw.GlfwCursor
import uno.glfw.GlfwWindow
import uno.glfw.Joystick
import uno.glfw.glfw
import java.nio.ByteBuffer

class MenuMesh(val hub: Hub) : Mesh("Menu") {

    var showDemoWindow = false

    // Setup Dear ImGui context
    val ctx = Context()

    val vrTexSize: Vec2i? = null

    /** for passing inputs in vr */
    var vrCursorPos: Vec2? = null

    // 1f unless you use any scaling != 100%, 125% corresponds to 1.25f
    val scaleFont = 1.25f

    // this must come before retrieving the texture (which comes right after, in the init)
    val font = ImGui.io.fonts.addFontFromFileTTF("graphics/scenery/ui/ProggyClean.ttf", 16f * scaleFont)!!

    val fontMap: Texture = ImGui.io.fonts.getTexDataAsRGBA32().let { (pixels, size, _) ->
        Texture(dimensions = Vector3i(size.x, size.y, 1), contents = pixels)
    }

    var vtx = ByteBuffer(1024)
    var idx = IntBuffer(256)

    val renderer = hub.get<Renderer>()!!
    val input by lazy { hub.get<InputHandler>()!!.handler!! as GLFWMouseAndKeyHandler }
    val window by lazy { GlfwWindow.from((hub.get<Renderer>()!!.window as SceneryWindow.GLFWWindow).window) }
    val menus = mutableMapOf<String, MenuNode>() // TODO switch to Int key

    // Setup Platform/Renderer bindings
    val implGlfw = ImplGlfw(window, true, vrTexSize)

    override fun preDraw(): Boolean {
        //        if(!stale) {
        //            return true
        //        }

        // setup time step and input states
        //        implGlfwNewFrame()
        implGlfw.newFrame()

        ImGui.run {

            newFrame()

            dsl.withFont(this@MenuMesh.font) {

                // 1. Show the big demo window (Most of the sample code is in ImGui::ShowDemoWindow()! You can browse its code to learn more about Dear ImGui!).
                if (showDemoWindow)
                    showDemoWindow(::showDemoWindow)

                menu(getScene()!!)

            }
        }

        ImGui.io.mouseDown.forEach {
            if (it)
                println("mouse button $it down")
        }

        return renderImgui()
    }

    fun renderImgui(): Boolean {

        // Rendering
        ImGui.render()
        val drawData = ImGui.drawData!!

        // Avoid rendering when minimized, scale coordinates for retina displays (screen coordinates != framebuffer coordinates)
        val fbSize = Vec2i(drawData.displaySize * drawData.framebufferScale)
        if (fbSize anyLessThanEqual 0)
            return false

        // Setup scale and translation:
        // Our visible imgui space lies from draw_data->DisplayPps (top left) to draw_data->DisplayPos+data_data->DisplaySize (bottom right). DisplayPos is (0,0) for single viewport apps.
        val scale = Vector2f(2f / drawData.displaySize.x, 2f / drawData.displaySize.y)
        val translate = Vector2f(-1f - drawData.displayPos.x * scale[0], -1f - drawData.displayPos.y * scale[1])

        //        logger.info("Got ${drawData.cmdLists.size} draw cmd lists")

        val DrawVertSize = Vec2.size * 2 + Int.SIZE_BYTES
        val vertexSize = drawData.totalVtxCount * DrawVertSize
        val indexSize = drawData.totalIdxCount

        //        println("vertexSize: $vertexSize, indexSize: $indexSize")

        if (vertexSize > vtx.cap) {
            vtx.free()
            vtx = ByteBuffer(vertexSize * 2) // give it some space
        }
        if (indexSize > idx.capacity()) {
            idx.free()
            idx = IntBuffer(indexSize * 2)
        }
        var vtxPtr = vtx.adr
        var idxPtr = idx.adr

        //        run {
        //            val i = 0
        //            val o = DrawVertSize * i
        //            println("DrawVert[$i] { pos(${vtx.getFloat(o)}, ${vtx.getFloat(o + Float.BYTES)}, " +
        //                            "uv(${vtx.getFloat(o + Vec2.size)}, ${vtx.getFloat(o + Vec2.size + Float.BYTES)} " +
        //                            "col(${vtx.getInt(o + Vec2.size * 2)}")
        //        }

        vtx.fill(0)
        idx.fill(0)

        // retain our still existing menus
        menus.entries.retainAll { (k, _) -> drawData.cmdLists.any { it._ownerName == k } }

        var globalVtxOffset = 0
        var globalIdxOffset = 0
        drawData.cmdLists.forEach { drawList ->
            val node = menus.getOrPut(drawList._ownerName) {
                logger.info("Adding new node for ${drawList._ownerName}")
                MenuNode(drawList._ownerName).also {
                    it.vertexSize = 2
                    it.material = ShaderMaterial.fromClass(MenuNode::class.java, listOf(ShaderType.VertexShader, ShaderType.FragmentShader))
                    it.material.textures["sTexture"] = fontMap
                    it.material.blending.transparent = true
                    it.material.blending.setOverlayBlending()
                    it.material.cullingMode = Material.CullingMode.None
                    it.material.depthTest = Material.DepthTest.Always
                    addChild(it)
                }
            }

            node.splitDrawCalls.clear()

            val clipOff = drawData.displayPos         // (0,0) unless using multi-viewports
            val clipScale = drawData.framebufferScale // (1,1) unless using retina display which are often (2,2)
            for (cmd in drawList.cmdBuffer) {
                val clipRect = Vec4 { (cmd.clipRect[it] - clipOff[it % 2]) * clipScale[it % 2] }

                if (clipRect.x < fbSize.x && clipRect.y < fbSize.y && clipRect.z >= 0f && clipRect.w >= 0f) {
                    // Negative offsets are illegal for vkCmdSetScissor
                    if (clipRect.x < 0f)
                        clipRect.x = 0f
                    if (clipRect.y < 0f)
                        clipRect.y = 0f

                    // Apply scissor/clipping rectangle
                    node.uScale = scale
                    node.uTranslate = translate
                    val extent = Vector2i((clipRect.z - clipRect.x).i, (clipRect.w - clipRect.y).i)
                    val offset = Vector2i(clipRect.x.i, clipRect.y.i)
                    val scissor = Scissor(extent, offset)

                    node.splitDrawCalls += DrawState(cmd.elemCount, cmd.idxOffset + globalIdxOffset, cmd.vtxOffset + globalVtxOffset, scissor)
                }
            }
            globalIdxOffset += drawList.idxBuffer.rem
            globalVtxOffset += drawList.vtxBuffer.rem

            memCopy(drawList.vtxBuffer.data.adr, vtxPtr, drawList.vtxBuffer.size * DrawVertSize.L)
            memCopy(memAddress(drawList.idxBuffer), idxPtr, drawList.idxBuffer.remaining() * Integer.BYTES.L)
            vtxPtr += drawList.vtxBuffer.size * DrawVertSize.L
            idxPtr += drawList.idxBuffer.remaining() * Integer.BYTES

            node.vertices = vtx.asFloatBuffer()
            //node.normals = node.vertices // TODO change me
            node.indices = idx

            node.dirty = true
        }
        //        println("vtx:$vtx")
        //        println("idx:$idx")
        //        val duration = System.nanoTime() - start
        //        logger.info("Imgui serialisation took ${duration / 10e6}ms")
        //logger.info("menu children: ${this.children.joinToString { "${it.name}, ${(it as? MenuNode)?.vertices?.remaining()}, ${(it as? MenuNode)?.splitDrawCalls?.size}" }}")
        return true
    }

    fun implGlfwNewFrame() {

        assert(ImGui.io.fonts.isBuilt) { "Font atlas not built! It is generally built by the renderer back-end. Missing call to renderer _NewFrame() function? e.g. ImGui_ImplOpenGL3_NewFrame()." }

        // Setup display size (every frame to accommodate for window resizing)
        val size = window.size
        val displaySize = window.framebufferSize
        ImGui.io.displaySize put (vrTexSize ?: size)
        if (size allGreaterThan 0)
            ImGui.io.displayFramebufferScale put (displaySize / size)

        // Setup time step
        val currentTime = glfw.time
        ImGui.io.deltaTime = if (time > 0) (currentTime - time).f else 1f / 60f
        time = currentTime

        updateMousePosAndButtons()
        updateMouseCursor()

        // Update game controllers (if enabled and available)
        updateGamepads()
    }

    private fun updateMousePosAndButtons() {

        // Update buttons
        repeat(ImGui.io.mouseDown.size) {
            /*  If a mouse press event came, always pass it as "mouse held this frame", so we don't miss click-release
                events that are shorter than 1 frame.   */
            ImGui.io.mouseDown[it] = mouseJustPressed[it] || input.getMouseButton(it) == GLFW.GLFW_PRESS
            mouseJustPressed[it] = false
        }

        // Update mouse position
        val mousePosBackup = Vec2d(ImGui.io.mousePos)
        ImGui.io.mousePos put -Float.MAX_VALUE
        if (window.isFocused)
            if (ImGui.io.wantSetMousePos)
                window.cursorPos = mousePosBackup
            else
                ImGui.io.mousePos put (vrCursorPos ?: window.cursorPos)
        else
            vrCursorPos?.let(ImGui.io.mousePos::put) // window is usually unfocused in vr
    }

    private fun updateMouseCursor() {

        if (ImGui.io.configFlags has ConfigFlag.NoMouseCursorChange || window.cursorMode == GlfwWindow.CursorMode.disabled)
            return

        val imguiCursor = ImGui.mouseCursor
        if (imguiCursor == MouseCursor.None || ImGui.io.mouseDrawCursor)
        // Hide OS mouse cursor if imgui is drawing it or if it wants no cursor
            window.cursorMode = GlfwWindow.CursorMode.hidden
        else {
            // Show OS mouse cursor
            // FIXME-PLATFORM: Unfocused windows seems to fail changing the mouse cursor with GLFW 3.2, but 3.3 works here.
            window.cursor = GlfwCursor(mouseCursors[imguiCursor.i].takeIf { it != NULL }
                                           ?: mouseCursors[MouseCursor.Arrow.i])
            window.cursorMode = GlfwWindow.CursorMode.normal
        }
    }

    fun updateGamepads() {

        ImGui.io.navInputs.fill(0f)
        if (ImGui.io.configFlags has ConfigFlag.NavEnableGamepad) {
            // Update gamepad inputs
            val buttons = Joystick._1.buttons ?: ByteBuffer.allocate(0)
            val buttonsCount = buttons.lim
            val axes = Joystick._1.axes ?: java.nio.FloatBuffer.allocate(0)
            val axesCount = axes.lim

            fun mapButton(nav: NavInput, button: Int) {
                if (buttonsCount > button && buttons[button] == GLFW.GLFW_PRESS.b)
                    ImGui.io.navInputs[nav] = 1f
            }

            fun mapAnalog(nav: NavInput, axis: Int, v0: Float, v1: Float) {
                var v = if (axesCount > axis) axes[axis] else v0
                v = (v - v0) / (v1 - v0)
                if (v > 1f) v = 1f
                if (ImGui.io.navInputs[nav] < v)
                    ImGui.io.navInputs[nav] = v
            }

            mapButton(NavInput.Activate, 0)     // Cross / A
            mapButton(NavInput.Cancel, 1)     // Circle / B
            mapButton(NavInput.Menu, 2)     // Square / X
            mapButton(NavInput.Input, 3)     // Triangle / Y
            mapButton(NavInput.DpadLeft, 13)    // D-Pad Left
            mapButton(NavInput.DpadRight, 11)    // D-Pad Right
            mapButton(NavInput.DpadUp, 10)    // D-Pad Up
            mapButton(NavInput.DpadDown, 12)    // D-Pad Down
            mapButton(NavInput.FocusPrev, 4)     // L1 / LB
            mapButton(NavInput.FocusNext, 5)     // R1 / RB
            mapButton(NavInput.TweakSlow, 4)     // L1 / LB
            mapButton(NavInput.TweakFast, 5)     // R1 / RB
            mapAnalog(NavInput.LStickLeft, 0, -0.3f, -0.9f)
            mapAnalog(NavInput.LStickRight, 0, +0.3f, +0.9f)
            mapAnalog(NavInput.LStickUp, 1, +0.3f, +0.9f)
            mapAnalog(NavInput.LStickDown, 1, -0.3f, -0.9f)

            ImGui.io.backendFlags = when {
                axesCount > 0 && buttonsCount > 0 -> ImGui.io.backendFlags or BackendFlag.HasGamepad
                else -> ImGui.io.backendFlags wo BackendFlag.HasGamepad
            }
        }
    }
}