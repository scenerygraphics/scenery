package graphics.scenery.attribute.populatesubo

import graphics.scenery.Node

interface HasCustomPopulatesUBO<T: PopulatesUBO>: Node {
    fun createPopulatesUBO(): PopulatesUBO

    fun addPopulatesUBO() {
        addAttribute(PopulatesUBO::class.java, createPopulatesUBO())
    }

    fun populatesUBO(): T {
        return getAttribute(PopulatesUBO::class.java)
    }
}