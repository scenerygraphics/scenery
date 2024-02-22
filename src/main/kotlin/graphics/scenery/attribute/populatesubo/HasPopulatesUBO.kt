package graphics.scenery.attribute.populatesubo

interface HasPopulatesUBO: HasCustomPopulatesUBO<PopulatesUBO> {
    override fun createPopulatesUBO(): PopulatesUBO {
        return DefaultPopulatesUBO()
    }
}