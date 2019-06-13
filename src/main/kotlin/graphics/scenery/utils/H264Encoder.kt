package graphics.scenery.utils

import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.SceneryElement
import graphics.scenery.Settings
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.avcodec.*
import org.bytedeco.javacpp.avformat.*
import org.bytedeco.javacpp.avutil.*
import org.bytedeco.javacpp.swscale
import org.lwjgl.system.MemoryUtil
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.absoluteValue

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

class H264Encoder(val frameWidth: Int, val frameHeight: Int, filename: String, fps: Int = 60, override var hub: Hub? = null): Hubable {
    protected val logger by LazyLogger()
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

    val quality = VideoEncodingQuality.valueOf(hub?.get<Settings>(SceneryElement.Settings)?.get("VideoEncoder.Quality", "Medium") ?: "Medium")
    val networked = hub?.get<Settings>(SceneryElement.Settings)?.get("VideoEncoder.StreamVideo", false) ?: false
    val bitrate = hub?.get<Settings>(SceneryElement.Settings)?.get("VideoEncoder.Bitrate", 10000000) ?: 10000000

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

        init {
            @Suppress("DEPRECATION")
            av_register_all()
            @Suppress("DEPRECATION")
            avcodec_register_all()
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

            val url = hub?.get<Settings>(SceneryElement.Settings)?.get("H264Encoder.StreamingAddress", "udp://${InetAddress.getLocalHost().hostAddress}:3337")
                ?: "udp://${InetAddress.getLocalHost().hostAddress}:3337"

            val format = if (networked) {
                outputFile = url
                logger.info("Using network streaming, serving at $url")

                "rtp" to av_guess_format("mpegts", null, null)
            } else {
                "mp4" to av_guess_format("mp4", null, null)
            }

            var ret = avformat_alloc_output_context2(outputContext, format.second, format.first, outputFile)
            if (ret < 0) {
                logger.error("Could not allocate output context: $ret")
            }

            outputContext.video_codec_id(AV_CODEC_ID_H264)
            outputContext.audio_codec_id(AV_CODEC_ID_NONE)

            actualFrameWidth = frameWidth.nearestWholeMultipleOf(2)
            actualFrameHeight = frameHeight.nearestWholeMultipleOf(2)

            val nvenc = if(hub?.get<Settings>(SceneryElement.Settings)?.get("VideoEncoder.HWAccel", false) == true) {
                avcodec_find_encoder_by_name("h264_nvenc")
            } else {
                null
            }
            codec = if(nvenc == null) {
                logger.info("Could not find hardware-accelerated H264 encoder, falling back to software encoder.")
                avcodec_find_encoder(outputContext.video_codec_id())
            } else {
                nvenc
            }

            @Suppress("SENSELESS_COMPARISON")
            // codec might actually be null
            if (codec == null) {
                logger.error("Could not find H264 encoder")
            }

            codecContext = avcodec_alloc_context3(codec)
            // codecContext might actually be null
            @Suppress("SENSELESS_COMPARISON")
            if (codecContext == null) {
                logger.error("Could not allocate video codecContext")
            }

            codecContext.codec_id(AV_CODEC_ID_H264)
            codecContext.bit_rate(bitrate.toLong())
            codecContext.width(actualFrameWidth)
            codecContext.height(actualFrameHeight)
            codecContext.time_base(timebase)
            codecContext.framerate(framerate)
            codecContext.gop_size(10)
            codecContext.max_b_frames(1)
            codecContext.pix_fmt(AV_PIX_FMT_YUV420P)
            codecContext.codec_tag(0)
            codecContext.codec_type(AVMEDIA_TYPE_VIDEO)

            if (networked) {
                codecContext.flags(AV_CODEC_FLAG_GLOBAL_HEADER)
            }

            if (outputContext.oformat().flags() and AVFMT_GLOBALHEADER == 1) {
                logger.debug("Output format requires global format header")
                codecContext.flags(codecContext.flags() or AV_CODEC_FLAG_GLOBAL_HEADER)
            }

            av_opt_set(codecContext.priv_data(), "preset", quality.toFFMPEGPreset(), 0)
            av_opt_set(codecContext.priv_data(), "tune", "zerolatency", 0)
            av_opt_set(codecContext.priv_data(), "repeat-headers", "1", 0)

            ret = avcodec_open2(codecContext, codec, AVDictionary())
            if (ret < 0) {
                logger.error("Could not open codec: ${ffmpegErrorString(ret)}")
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

            ret = avcodec_parameters_from_context(stream.codecpar(), codecContext)
            if (ret < 0) {
                logger.error("Could not get codec parameters")
            }

            ret = av_frame_get_buffer(frame, 32)
            if (ret < 0) {
                logger.error("Could not allocate frame data")
            }

            av_dump_format(outputContext, 0, outputFile, 1)

            if (outputContext.oformat().flags() and AVFMT_NOFILE == 0) {
                val pb = AVIOContext(null)
                ret = avio_open(pb, outputFile, AVIO_FLAG_WRITE)
                outputContext.pb(pb)

                if (ret < 0) {
                    logger.error("Failed to open output file $outputFile: $ret")
                }
            } else {
                logger.debug("Not opening file as not required by outputContext")
            }

            logger.info("Writing movie to $outputFile, ${frameWidth}x$frameHeight, with format ${String(outputContext.oformat().long_name().stringBytes)}, quality $quality, bitrate ${String.format(Locale.US, "%.2f", bitrate/1024.0f/1024.0f)} MBit")

//        Don't use SDP files for the moment
//        if(networked) {
//            val buffer = ByteArray(1024, { 0 })
//            av_sdp_create(outputContext, 1, buffer, buffer.size)
//
//            File("$filename.sdp").bufferedWriter().use { out ->
//                logger.info("SDP size: ${String(buffer).length}")
//                out.write(String(buffer).substringBefore('\u0000'))
//            }
//        }

            ret = avformat_write_header(outputContext, AVDictionary())

            if (ret < 0) {
                logger.error("Failed to write header: ${ffmpegErrorString(ret)}")
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

        while(!ready) {
            Thread.sleep(5)
        }

        startTimestamp = System.nanoTime()
    }

    protected var scalingContext: swscale.SwsContext? = null
    protected var frameEncodingFailure = 0

    @JvmOverloads fun encodeFrame(data: ByteBuffer?, flip: Boolean = false) {
        GlobalScope.launch {
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
                frameQueue.add(QueuedFrame.Frame(copy, timestamp = System.nanoTime()))
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

        if(scalingContext == null) {
            scalingContext = swscale.sws_getContext(
                frameWidth, frameHeight, AV_PIX_FMT_BGRA,
                actualFrameWidth, actualFrameHeight, AV_PIX_FMT_YUV420P, swscale.SWS_BICUBIC,
                null, null, emptyScalingParams)
        }

        av_frame_make_writable(tmpframe)
        av_frame_make_writable(frame)

        av_image_fill_arrays(tmpframe.data(), tmpframe.linesize(), BytePointer(data), AV_PIX_FMT_BGRA, frameWidth, frameHeight, 1)

        val packet = AVPacket()
        av_init_packet(packet)

        var ret = if(data != null && f is QueuedFrame.Frame) {
            tmpframe.pts(frameNum)
            frame.pts(frameNum)

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
                frameNum++
                return
            } else if(ret < 0){
                logger.error("Error encoding frame $frameNum: ${ffmpegErrorString(ret)} ($ret)")
                frameEncodingFailure = ret
                return
            }

            packet.stream_index(0)

            av_packet_rescale_ts(packet, timebase, stream.time_base())

            ret = av_write_frame(outputContext, packet)

            if(ret < 0) {
                logger.error("Error writing frame $frameNum: ${ffmpegErrorString(ret)}")
            }
        }

        av_packet_unref(packet)
        logger.trace("Encoded frame $frameNum")

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
