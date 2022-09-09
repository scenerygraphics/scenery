package graphics.scenery.tests.unit.network

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import graphics.scenery.net.NetworkEvent
import graphics.scenery.net.Networkable
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

internal fun serializeAndDeserialize(obj: Any): Any {
    val kryo = NodePublisher.freeze()
    val bos = ByteArrayOutputStream()
    val output = Output(bos)
    kryo.writeClassAndObject(output, obj)
    output.flush()

    val bin = ByteArrayInputStream(bos.toByteArray())
    val input = Input(bin)
    return kryo.readClassAndObject(input)
}

//Inline function to access private function; We do evil for the nobel pursuit of testing. >:(
private inline fun <reified T> T.callPrivateFunc(name: String, vararg args: Any?): Any? =
    T::class
        .declaredMemberFunctions
        .firstOrNull { it.name == name }
        ?.apply { isAccessible = true }
        ?.call(this, *args)

internal fun NodeSubscriber.debugListen(event: NetworkEvent) =
    callPrivateFunc("debugListen", event)

internal fun NodePublisher.debugPublish(send: (NetworkEvent) -> Unit) =
    callPrivateFunc("debugPublish", send)

