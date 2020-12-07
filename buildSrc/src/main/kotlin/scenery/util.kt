package scenery

var prefix = ""

operator fun String.invoke(block: () -> Unit) {
    prefix = this
    block()
    prefix = ""
}

//object Support {
//
//}