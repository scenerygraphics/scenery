package graphics.scenery.ui


/**
 *  @author Jan Tiemann
 *  */
class TabbedMenu(tabs: List<MenuTab>) : Column(invertedYOrder = false){

    private var openTab: MenuTab? = null

    init {
        if(tabs.isEmpty()) throw IllegalArgumentException("Tabbed Menu needs at least one sub menu.")
        name = "TabbedMenu"

        tabs.forEach { tab ->
            tab.menu.centerVertically = false
            tab.button = Button(tab.name, command = {
                openMenu(tab)
            }.apply {
                tab.button?.stayPressed = true
            })
        }

        this.addChild(Row(*tabs.map { it.button!! }.toTypedArray()))

        openMenu(tabs.first())

        this.pack()
    }

    fun onActivate(){
        openTab?.onActivate?.invoke()
    }

    private fun openMenu(tab: MenuTab){
        if (openTab == tab) return

        openTab?.let { toBeClosedTab ->
            toBeClosedTab.menu.detach()
            toBeClosedTab.button?.pressed = false
            toBeClosedTab.onDeactivate()
            openTab = null
        }

        addChild(tab.menu)
        tab.button?.pressed = true
        tab.onActivate()
        openTab = tab
        this.pack()
    }

    class MenuTab(val name: String, val menu: Column, val onActivate: () -> Unit = {}, val onDeactivate: () -> Unit = {}, ) {
        internal var button: Button? = null
    }
}
