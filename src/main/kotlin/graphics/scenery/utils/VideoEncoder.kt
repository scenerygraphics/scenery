package graphics.scenery.utils

import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.SceneryElement
import graphics.scenery.Settings
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVIOContext
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avutil.AVBufferRef
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avformat.*
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.ffmpeg.global.swscale
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

/**
 * H264 encoder class
 *
 * Experimental class for enabling movie recordings and streaming from within scenery, based on ffmpeg.
 *
 * @param[frameWidth] The width of the rendered picture. Will be rounded to the nearest multiple of 2 in the movie.
 * @param[frameHeight] The height of the rendered picture. Will be rounded to the nearest multiple of 2 in the movie.
 * @param[filename] The file name under which to save the movie. In case the system property `scenery.StreamVideo` is true,
 *      the frames are streamed via UDP multicast on the local IP, on port 3337 as MPEG transport stream.
 * @param[fps] The target frame rate for the movie. Setting this to 0 will perform variable frame rate recording.
 *
 * The following [Settings] determine the output quality:
 * - `VideoEncoder.Bitrate` sets the bit rate for encoding, default is 10000000, or 10 MBit
 * - `VideoEncoder.Quality` sets the [VideoEncodingQuality], default is "Medium"
 *
 * Additionally, `VideoEncoder.StreamVideo` enables RTP network streaming.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

class VideoEncoder(
    val frameWidth: Int,
    val frameHeight: Int,
    val filename: String,
    val fps: Int = 60,
    override var hub: Hub? = null,
    val networked: Boolean = hub?.get<Settings>()?.get("VideoEncoder.StreamVideo", false) ?: false
): Hubable {
    protected val logger by lazyLogger()
    protected lateinit var frame: AVFrame
    protected lateinit var tmpframe: AVFrame

    protected lateinit var codec: AVCodec
    protected lateinit var codecContext: AVCodecContext
    protected var outputContext: AVFormatContext = AVFormatContext()
    protected lateinit var stream: AVStream

    protected var frameNum = 0L
    protected val timebase = AVRational().num(1).den(fps)
    protected val framerate = AVRational().num(fps).den(1)

    protected var outputFile: String = filename
    protected var actualFrameWidth: Int = 512
    protected var actualFrameHeight: Int = 512
    protected val startTimestamp: Long

    /** Video encoding format, can be H264 or HEVC. */
    val format = VideoFormat.valueOf(hub?.get<Settings>(SceneryElement.Settings)?.get("VideoEncoder.Format", "H264") ?: "H264")
    /** Quality preset of the encoder, if available. Default is [VideoEncodingQuality.Medium]. */
    val quality = VideoEncodingQuality.valueOf(hub?.get<Settings>(SceneryElement.Settings)?.get("VideoEncoder.Quality", "Medium") ?: "Medium")
    /** Bitrate to use for video encoding. Default is 2MBit. */
    val bitrate = hub?.get<Settings>(SceneryElement.Settings)?.get("VideoEncoder.Bitrate", 2000000) ?: 2000000

    /** Disables hardware accelleration, will always fall back to the (slow) software encoder. If false, NVenc, AMF or QuickSync will be used. */
    val disableHWAcceleration = hub?.get<Settings>(SceneryElement.Settings)?.get("VideoEncoder.DisableHWEncoding", false)

    /** The streaming address to use, if [networked] is true. Defaults to RTP streaming on port 5004, will write out an SDP file to the current working directory. */
    val streamingAddress = hub?.get<Settings>()?.get("VideoEncoder.StreamingAddress", "rtp://${InetAddress.getLocalHost().hostAddress}:5004") ?: "rtp://${InetAddress.getLocalHost().hostAddress}:5004"

    enum class VideoFormat {
        H264,
        HEVC
    }

    companion object {
        fun VideoEncodingQuality.toFFMPEGPreset(): String =
            when(this) {
                VideoEncodingQuality.VeryLow -> "ultrafast"
                VideoEncodingQuality.Low -> "veryfast"
                VideoEncodingQuality.Medium -> "medium"
                VideoEncodingQuality.High -> "slow"
                VideoEncodingQuality.Ultra -> "slower"
                VideoEncodingQuality.Insane -> "veryslow"
            }

        fun VideoFormat.toCodecId(): Int = when(this) {
            VideoFormat.H264 -> AV_CODEC_ID_H264
            VideoFormat.HEVC -> AV_CODEC_ID_H265
        }

        init {
            avformat_network_init()
        }
    }

    private fun Int.nearestWholeMultipleOf(divisor: Int): Int {
        var out = this.div(divisor)
        if(out == 0 && this > 0) {
            out++
        }

        return out * divisor
    }

    private val encodingThread: Job
    private var ready: Boolean = false
    private var finished: Boolean = false
    private var frameQueue = ConcurrentLinkedQueue<QueuedFrame>()
    private val emptyScalingParams = DoublePointer()
    @Volatile private var error = 0

    sealed class QueuedFrame {
        class Frame(val data: ByteBuffer, val timestamp: Long): QueuedFrame()
        class FinalFrame: QueuedFrame()
    }

    init {

        encodingThread = GlobalScope.launch {
            if (logger.isDebugEnabled) {
                av_log_set_level(AV_LOG_TRACE)
            } else {
                av_log_set_level(AV_LOG_ERROR)
            }

            val fileFormat = if (networked) {
                outputFile = streamingAddress
                logger.info("Using network streaming, serving at $streamingAddress")

                "rtp" to av_guess_format("rtp", null, null)
            } else {
                "mp4" to av_guess_format("mp4", null, null)
            }

            error = avformat_alloc_output_context2(outputContext, fileFormat.second, fileFormat.first, outputFile)
            if (error < 0) {
                logger.error("Could not allocate output context: ${ffmpegErrorString(error)}")
                return@launch
            }

            outputContext.video_codec_id(format.toCodecId())
            outputContext.audio_codec_id(AV_CODEC_ID_NONE)

            actualFrameWidth = frameWidth.nearestWholeMultipleOf(2)
            actualFrameHeight = frameHeight.nearestWholeMultipleOf(2)

            val encoders = if(disableHWAcceleration == true) {
                listOf<Triple<String, AVCodec?, (AVCodecContext) -> AVCodecContext?>>(
                    Triple("Software encoder", avcodec_find_encoder(outputContext.video_codec_id())) { context ->
                        av_opt_set(context.priv_data(), "preset", quality.toFFMPEGPreset(), 0)
                        av_opt_set(context.priv_data(), "tune", "zerolatency", 0)
                        av_opt_set(context.priv_data(), "repeat-headers", "1", 0)
                        context
                    }
                )
            } else {
                listOf<Triple<String, AVCodec?, (AVCodecContext) -> AVCodecContext?>>(
                    Triple(
                        "NVenc",
                        avcodec_find_encoder_by_name("${format.toString().lowercase()}_nvenc"),
                        { context -> context }),

                    Triple(
                        "AMD AMF",
                        avcodec_find_encoder_by_name("${format.toString().lowercase()}_amf"),
                        { context -> context }),

                    Triple(
                        "Intel Quick Sync Video",
                        avcodec_find_encoder_by_name("${format.toString().lowercase()}_qsv")
                    ) { context: AVCodecContext ->
                        logger.debug("Creating QuickSync device")
                        val device = AVBufferRef()
                        val create = av_hwdevice_ctx_create(device, AV_HWDEVICE_TYPE_QSV, "", AVDictionary(), 0)

                        if (create < 0) {
                            logger.error("Could not open QSV device: ${ffmpegErrorString(create)}")
                            null
                        } else {
                            context.pix_fmt(AV_PIX_FMT_NV12)
                            context.hw_device_ctx(device)
                            av_opt_set(context.priv_data(), "preset", quality.toFFMPEGPreset(), 0)
                            context
                        }
                    },

                    Triple("Software encoder", avcodec_find_encoder(outputContext.video_codec_id())) { context ->
                        av_opt_set(context.priv_data(), "preset", quality.toFFMPEGPreset(), 0)
                        av_opt_set(context.priv_data(), "tune", "zerolatency", 0)
                        av_opt_set(context.priv_data(), "repeat-headers", "1", 0)
                        context
                    }
                )
            }
                .mapNotNull {
                    val codec = it.second ?: return@mapNotNull null

                    var context = avcodec_alloc_context3(codec)
                    // codecContext might actually be null
                    @Suppress("SENSELESS_COMPARISON")
                    if (context == null) {
                        logger.error("Could not allocate video codecContext")
                    }

                    context.codec_id(format.toCodecId())
                    context.bit_rate(bitrate.toLong())
                    context.width(actualFrameWidth)
                    context.height(actualFrameHeight)
                    context.time_base(timebase)
                    context.framerate(framerate)
                    context.gop_size(10)
                    context.max_b_frames(1)
                    context.pix_fmt(AV_PIX_FMT_YUV420P)
                    context.codec_tag(0)
                    context.codec_type(AVMEDIA_TYPE_VIDEO)
                    context.flags(AV_CODEC_FLAG_GLOBAL_HEADER)

                    context = it.third.invoke(context) ?: return@mapNotNull null

                    val codecOpenError = avcodec_open2(context, codec, AVDictionary())
                    if (codecOpenError < 0) {
                        logger.debug("Could not open codec ${it.first}: ${ffmpegErrorString(codecOpenError)}")
                        null
                    } else {
                        Triple<String, AVCodec, AVCodecContext>(it.first, codec, context)
                    }
                }

            if(encoders.isEmpty()) {
                logger.error("No supported $format encoders found.")
                error = -1
                return@launch
            } else {
                logger.info("Supported $format encoders: ${encoders.joinToString { it.first }}")
            }

            if(disableHWAcceleration == true || encoders.size == 1) {
                logger.info("Using software encoder for $format encoding")
                codec = encoders.last().second
                codecContext = encoders.last().third
            } else {
                val encoder = encoders.first()
                logger.info("Using hardware-accelerated ${encoder.first} encoder for $format encoding")
                codec = encoder.second
                codecContext = encoder.third
            }

            stream = avformat_new_stream(outputContext, codec)
            // stream might actually be null
            @Suppress("SENSELESS_COMPARISON")
            if (stream == null) {
                logger.error("Could not allocate stream")
            }

            stream.time_base(timebase)
            stream.id(outputContext.nb_streams() - 1)
            stream.r_frame_rate(codecContext.framerate())

            logger.debug("Stream ID will be ${stream.id()}")

            frame = av_frame_alloc()
            frame.format(codecContext.pix_fmt())
            frame.width(codecContext.width())
            frame.height(codecContext.height())

            tmpframe = av_frame_alloc()
            tmpframe.format(codecContext.pix_fmt())
            tmpframe.width(codecContext.width())
            tmpframe.height(codecContext.height())

            outputContext.streams(0, stream)

            error = avcodec_parameters_from_context(stream.codecpar(), codecContext)
            if (error < 0) {
                logger.error("Could not get codec parameters: ${ffmpegErrorString(error)}")
                return@launch
            }

            error = av_frame_get_buffer(frame, 32)
            if (error < 0) {
                logger.error("Could not allocate frame data: ${ffmpegErrorString(error)}")
                return@launch
            }

            av_dump_format(outputContext, 0, outputFile, 1)

            if (outputContext.oformat().flags() and AVFMT_NOFILE == 0) {
                val pb = AVIOContext(null)
                error = avio_open(pb, outputFile, AVIO_FLAG_WRITE)
                outputContext.pb(pb)

                if (error < 0) {
                    logger.error("Failed to open output file $outputFile: ${ffmpegErrorString(error)}")
                    return@launch
                }
            } else {
                logger.debug("Not opening file as not required by outputContext")
            }

            logger.info("Writing movie to $outputFile, ${frameWidth}x$frameHeight, with format ${String(outputContext.oformat().long_name().stringBytes)}, quality $quality, bitrate ${String.format(Locale.US, "%.2f", bitrate/1024.0f/1024.0f)} MBit")

            if(networked) {
                val buffer = ByteArray(1024)
                av_sdp_create(outputContext, 1, buffer, buffer.size)

                val f = File("scenery-stream.sdp")
                val writer = f.writer()

                writer.write(String(buffer).substringBefore('\u0000'))
                writer.close()
            }

            error = avformat_write_header(outputContext, AVDictionary())

            if (error < 0) {
                logger.error("Failed to write header: ${ffmpegErrorString(error)}")
                return@launch
            }

            ready = true

            while(!finished) {
                if(frameQueue.isEmpty()) {
                    delay(5)
                    continue
                }

                when(val currentFrame = frameQueue.poll()) {
                    // encoding step for each frame we received data
                    is QueuedFrame.Frame -> {
                        if(frameQueue.lastOrNull() is QueuedFrame.FinalFrame && frameQueue.size % 50 == 0) {
                            logger.info("${frameQueue.size} frames (${frameQueue.map { if(it is QueuedFrame.Frame) { it.data.remaining()*1L } else { 0L }}.sum()/1024L/1024L} MBytes) left to encode.")
                        }

                        encode(currentFrame)
                        MemoryUtil.memFree(currentFrame.data)
                    }

                    // encoding step for the final, empty frame, plus deallocation steps
                    is QueuedFrame.FinalFrame -> {
                        encode(currentFrame)

                        av_write_trailer(outputContext)
                        avio_closep(outputContext.pb())
                        avformat_free_context(outputContext)

                        logger.info("Finished recording $outputFile, wrote $frameNum frames.")
                        finished = true

                        av_frame_free(frame)
                        av_frame_free(tmpframe)

                        swscale.sws_freeContext(scalingContext)
                    }
                }
            }
        }

        while(!ready && error >= 0) {
            Thread.sleep(5)
        }

        if(error < 0) {
            logger.error("Not recording, error occured during initialisation: ${ffmpegErrorString(error)}")
        }

        startTimestamp = System.nanoTime()
    }

    protected var scalingContext: SwsContext? = null
    protected var frameEncodingFailure = 0
    protected var start = 0L
    protected var lastPts = 0L

    @JvmOverloads fun encodeFrame(data: ByteBuffer?, flip: Boolean = false) {
        if(!ready && error < 0) {
            return
        }

        GlobalScope.launch {
            if(start == 0L) {
                start = System.currentTimeMillis()
            }

            if (data != null) {
                val copy = MemoryUtil.memAlloc(data.remaining())
                if(flip) {
                    for(line in (frameHeight-1) downTo 0) {
                        val start = line * frameWidth * 4
                        val size = frameWidth * 4
                        val pos = (line - frameHeight + 1).absoluteValue * frameWidth * 4

                        val src = data.duplicate().position(start).limit(start + size) as ByteBuffer
                        val target = copy.duplicate().position(pos).limit(pos + size) as ByteBuffer
                        MemoryUtil.memCopy(src, target)
                    }
                } else {
                    MemoryUtil.memCopy(data, copy)
                }
                frameQueue.add(QueuedFrame.Frame(copy, timestamp = System.currentTimeMillis()))
            } else {
                frameQueue.add(QueuedFrame.FinalFrame())
            }
        }
    }

    private fun encode(f: QueuedFrame) {
        val data = if(f is QueuedFrame.Frame) {
            f.data
        } else {
            null
        }

        if(frameEncodingFailure != 0) {
            return
        }

        val pts = if(f is QueuedFrame.Frame) {
            ((f.timestamp - start)/(1000.0f/fps)).roundToLong()
        } else {
            lastPts
        }

        // avoid duplicate frames
        if(pts == lastPts) {
            logger.debug("Skipping frame {} because of equal pts ({}, {})", frameNum, pts, lastPts)
            lastPts = pts
            return
        }

        if(scalingContext == null) {
            scalingContext = swscale.sws_getContext(
                frameWidth, frameHeight, AV_PIX_FMT_BGRA,
                actualFrameWidth, actualFrameHeight, codecContext.pix_fmt(), swscale.SWS_BICUBIC,
                null, null, emptyScalingParams)
        }

        av_frame_make_writable(tmpframe)
        av_frame_make_writable(frame)

        av_image_fill_arrays(tmpframe.data(), tmpframe.linesize(), BytePointer(data), AV_PIX_FMT_BGRA, frameWidth, frameHeight, 1)

        val packet = AVPacket()
        av_init_packet(packet)

        var ret = if(data != null && f is QueuedFrame.Frame) {
            tmpframe.pts(pts)
            frame.pts(pts)

            swscale.sws_scale(scalingContext,
                tmpframe.data(),
                tmpframe.linesize(), 0, frameHeight,
                frame.data(), frame.linesize())

            avcodec_send_frame(codecContext, frame)
        } else {
            avcodec_send_frame(codecContext, null)
        }

        while(ret >= 0) {
            ret = avcodec_receive_packet(codecContext, packet)

            if(ret == -11 /* AVERROR_EAGAIN */|| ret == AVERROR_EOF || ret == -35 /* also AVERROR_EAGAIN -.- */) {
                lastPts = pts
                frameNum++
                return
            } else if(ret < 0){
                logger.error("Error encoding frame $frameNum: ${ffmpegErrorString(ret)} ($ret)")
                frameEncodingFailure = ret
                return
            }

            packet.stream_index(0)
            packet.pts(pts)
            packet.dts(pts)

            av_packet_rescale_ts(packet, timebase, stream.time_base())

            ret = av_write_frame(outputContext, packet)

            if(ret < 0) {
                logger.error("Error writing frame $frameNum/pts=$pts: ${ffmpegErrorString(ret)}")
            }
        }

        av_packet_unref(packet)
        logger.trace("Encoded frame {} pts={}", frameNum, pts)

        lastPts = pts
        frameNum++
    }

    fun finish() {
        logger.info("Stopping movie recording, ${frameQueue.size} frames (${frameQueue.map { if(it is QueuedFrame.Frame) { it.data.remaining() } else { 0 }}.sum()/1024/1024} MBytes) left to encode.")
        encodeFrame(null)
    }

    private fun ffmpegErrorString(returnCode: Int): String {
        val buffer = ByteArray(1024, { _ -> 0 })
        av_make_error_string(buffer, buffer.size*1L, returnCode)

        return String(buffer, 0, buffer.indexOfFirst { it == 0.toByte() })
    }
}

