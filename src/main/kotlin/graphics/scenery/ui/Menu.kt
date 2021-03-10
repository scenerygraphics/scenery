package graphics.scenery.ui

import graphics.scenery.Mesh
import graphics.scenery.ShaderMaterial
import graphics.scenery.backends.ShaderType
import graphics.scenery.textures.Texture
import org.joml.Vector2f
import org.joml.Vector2i
import org.lwjgl.system.MemoryUtil.memAllocFloat
import org.lwjgl.system.MemoryUtil.memAllocInt

class Menu : Mesh("Menu") {

//    protected var fontMap: Texture
//    init {
//        // do imgui setup
//        fontMap = Texture()
//    }
//
//    override fun preDraw(): Boolean {
//        if(!stale) {
//            return true
//        }
//
//        implVk.newFrame()
//        implGlfw.newFrame()
//        ImGui.run {
//            newFrame()
//
//            // 1. Show the big demo window (Most of the sample code is in ImGui::ShowDemoWindow()! You can browse its code to learn more about Dear ImGui!).
//            if (showDemoWindow)
//                showDemoWindow(::showDemoWindow)
//        }
//
//        // Rendering
//        ImGui.render()
//        val drawData = ImGui.drawData!!
//
//        this.children.clear()
//        drawData.cmds.forEach {
//            val node = MenuNode()
//            node.material = ShaderMaterial.fromClass(MenuNode::class.java, listOf(ShaderType.VertexShader, ShaderType.FragmentShader))
//            node.uScale = Vector2f(0.0f)
//            node.uTranslate = Vector2f(0.0f)
//            node.extent = Vector2i(0)
//            node.offset = Vector2i(0)
//
//            node.vertices = memAllocFloat(1)
//            node.indices = memAllocInt(1)
//
//            node.material.textures["sTexture"] = fontMap
//            this.addChild(node)
//        }
//        return true
//    }
}