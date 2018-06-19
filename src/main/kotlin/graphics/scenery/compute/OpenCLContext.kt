package graphics.scenery.compute

import org.jocl.*
import org.jocl.CL.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.SceneryElement
import graphics.scenery.utils.LazyLogger
import java.io.File
import java.net.URL
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

class OpenCLContext(override var hub: Hub?, val devicePreference: String = System.getProperty("scenery.OpenCLDevice", "0,0")) : Hubable {
    private val logger by LazyLogger()

    var device: cl_device_id
    var kernels = ConcurrentHashMap<String, cl_kernel>()
    var context: cl_context
    var queue: cl_command_queue

    init {
        hub?.add(SceneryElement.OpenCLContext, this)

        val platformPref = devicePreference.substringBefore(",").toInt()
        val devicePref = devicePreference.substringAfter(",").toInt()

        val deviceType = CL_DEVICE_TYPE_GPU
        val deviceIndex = devicePref
        // Enable exceptions and subsequently omit error checks in this sample
        setExceptionsEnabled(true);

        // Obtain a platform ID
        val platforms = query<cl_platform_id> { l, a, n ->
            clGetPlatformIDs(l, a, n)
        }
        val platform = platforms[platformPref]

        // Initialize the context properties
        val contextProperties = cl_context_properties()
        contextProperties.addProperty(CL_CONTEXT_PLATFORM.toLong(), platform)

        // Obtain a device ID
        val devices = query({ l, a, n ->
            clGetDeviceIDs(platform, deviceType, l, a, n)
        }, { cl_device_id() })
        device = devices[deviceIndex]

		logger.info("Selected device: ${getString(device, CL_DEVICE_NAME)} running ${getString(device, CL_DEVICE_VERSION)}")

        // Create a context for the selected device
        context = clCreateContext(
                contextProperties, 1, arrayOf(device),
                null, null, null);

        // Create a command-queue for the selected device
        @Suppress("DEPRECATION")
        queue = clCreateCommandQueue(context, device, 0, null)
    }

    fun getString(device: cl_device_id, paramName: Int): String
    {
        // Obtain the length of the string that will be queried
        val size = LongArray(1)
        clGetDeviceInfo(device, paramName, 0, null, size);

        // Create a buffer of the appropriate size and fill it with the info
        val buffer = ByteArray(size[0].toInt())
        clGetDeviceInfo(device, paramName, buffer.size.toLong(), Pointer.to(buffer), null);

        // Create a string from the buffer (excluding the trailing \0 byte)
        return String(buffer, 0, buffer.size-1);
    }

    fun loadKernel(source: String, name: String): OpenCLContext {
        if(!kernels.containsKey(name)) {
            // Create the program from the source code
            val program = clCreateProgramWithSource(context, 1, arrayOf(source), null, null);

            // Build the program
            clBuildProgram(program, 0, null, null, null, null);

            // Create the kernel
            val kernel = clCreateKernel(program, name, null);
            kernels.put(name, kernel)
        }

        return this
    }

    fun loadKernel(source: File, name: String): OpenCLContext {
        return loadKernel(source.readLines(charset = Charsets.UTF_8).joinToString("\n"), name)

    }

    fun loadKernel(source: URL, name: String): OpenCLContext {
        return loadKernel(String(source.readBytes(), StandardCharsets.UTF_8), name)
    }

    protected fun getSizeof(obj: Any): Long {
        return when(obj.javaClass) {
            Float::class.java   -> Sizeof.cl_float
            Int::class.java     -> Sizeof.cl_int
            Integer::class.java -> Sizeof.cl_int
            Byte::class.java    -> Sizeof.cl_uchar
            cl_mem::class.java  -> Sizeof.cl_mem

            // buffers
            FloatBuffer::class.java -> Sizeof.cl_float
            ByteBuffer::class.java  -> Sizeof.cl_uchar
            IntBuffer::class.java   -> Sizeof.cl_int

            else                -> {
                // these classes are package-local and can therefore not be matched for here directly
                if(obj.javaClass.canonicalName.contains("DirectByteBuffer") || obj.javaClass.canonicalName.contains("HeapByteBuffer")) {
                    1
                } else {
                    logger.error("Unrecognized class ${obj.javaClass.canonicalName}, returning 1 byte as size")
                    1
                }
            }
        }.toLong()
    }

    protected fun cl_kernel.setArgs(vararg arguments: Any) {
        arguments.forEachIndexed { i, arg ->
            if(arg is NativePointerObject) {
                clSetKernelArg(this,
                        i,
                        getSizeof(arg),
                        Pointer.to(arg))
            } else if(arg is Buffer) {
                clSetKernelArg(this,
                        i,
                        getSizeof(arg),
                        Pointer.to(arg))
            } else if(arg is cl_mem) {
                clSetKernelArg(this,
                        i,
                        getSizeof(arg),
                        Pointer.to(arg))
            } else if(arg is Int) {
                clSetKernelArg(this,
                        i,
                        getSizeof(arg),
                        Pointer.to(arrayOf(arg).toIntArray()))
            } else if(arg is Float) {
                clSetKernelArg(this,
                        i,
                        getSizeof(arg),
                        Pointer.to(arrayOf(arg).toFloatArray()))
            } else if(arg is Byte) {
                clSetKernelArg(this,
                        i,
                        getSizeof(arg),
                        Pointer.to(arrayOf(arg).toByteArray()))
            }
        }
    }

    fun runKernel(name: String, wavefronts: Int, vararg arguments: Any) {
        val k: cl_kernel = kernels[name]!!
        k.setArgs(*arguments)

        // Set the work-item dimensions
        val global_work_size = arrayOf(1L * wavefronts).toLongArray()
        val local_work_size = arrayOf(1L).toLongArray()

        // Execute the kernel
        clEnqueueNDRangeKernel(this.queue, k, 1, null,
                global_work_size, local_work_size, 0, null, null);

    }

    fun wrapInput(buffer: Buffer, readonly: Boolean = false): cl_mem {
        buffer.rewind()

        val p = Pointer.to(buffer)
        var flags = if(readonly) CL_MEM_READ_ONLY else CL_MEM_READ_WRITE
        flags = flags or CL_MEM_COPY_HOST_PTR
        val mem = clCreateBuffer(this.context, flags.toLong(), getSizeof(buffer)*buffer.remaining(), p, null)

        return mem
    }

    fun wrapOutput(buffer: Buffer): cl_mem {
        buffer.rewind()
        val flags = CL_MEM_READ_WRITE
        val mem = clCreateBuffer(this.context, flags.toLong(), getSizeof(buffer)*buffer.remaining(), null, null)

        return mem
    }

    fun readBuffer(memory: cl_mem, target: Buffer) {
        val p = Pointer.to(target)
        clEnqueueReadBuffer(queue, memory, CL_TRUE, 0, target.remaining() * getSizeof(target), p, 0, null, null);
    }


    companion object OpenCLUtils {
        /**
         * Convenience wrapper for OpenCL functions that query arrays and are usually
         * called twice, once for finding the number of elements required for the array
         * and a second time for filling the array with values. For example:
         * ```
         *   val numDevicesArray = IntArray(1);
         *   clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
         *   val numDevices = numDevicesArray[0];
         *   val devices = Array<cl_device_id>(numDevices, {i -> cl_device_id() })
         *   clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
         * ```
         *
         * This can be replaced by
         * ```
         *   val devices = query<cl_device_id>(
         *   { l, a, n -> clGetDeviceIDs(platform, deviceType, l, a, n) },
         *   { cl_device_id() })
         * ```
         * @param fn
         *      wraps the cl function to be called (twice),
         *      taking the parameters num_array_entries, array, num_available_entries.
         * @param init
         *      T initializer for creating an Array<T>
         */
        inline fun <reified T> query(fn: (Int, Array<T>?, IntArray?) -> Unit, noinline init: (Int) -> T): Array<T> {
            val num = IntArray(1);
            fn(0, null, num)
            val things = Array<T>(num[0], init)
            fn(num[0], things, null)
            return things
        }

        /**
         * Variant of [query] without array initializer. It creates arrays with nullable entries, and returns Array<T?> instead of Array<T>.
         */
        inline fun <reified T> query(fn: (Int, Array<T?>?, IntArray?) -> Unit): Array<T?> {
            val num = IntArray(1);
            fn(0, null, num)
            val things = arrayOfNulls<T>(num[0])
            fn(num[0], things, null)
            return things
        }
    }
}
