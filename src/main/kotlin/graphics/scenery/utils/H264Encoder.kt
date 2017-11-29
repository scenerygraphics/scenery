package graphics.scenery.utils

import org.bytedeco.javacpp.*
import org.bytedeco.javacpp.avcodec.*
import org.bytedeco.javacpp.avformat.*
import org.bytedeco.javacpp.avutil.*
import org.bytedeco.javacpp.swscale.SWS_BICUBIC
import org.lwjgl.system.MemoryUtil.memAlloc
import org.lwjgl.system.MemoryUtil.memAllocInt
import java.nio.ByteBuffer
import java.nio.IntBuffer

class H264Encoder(val frameWidth: Int, val frameHeight: Int, filename: String) {
    protected val logger by LazyLogger()
    protected val frame: AVFrame
    protected val codec: AVCodec
    protected val codecContext: AVCodecContext
    protected val outputContext: AVFormatContext = AVFormatContext()
    protected val stream: AVStream
    protected val packet: AVPacket

    protected var frameNum = 0L
    protected val timebase = AVRational().num(1).den(25)
    protected val conversionContext: swscale.SwsContext
    protected val lineSize: IntBuffer
    protected val frameLineSize: IntBuffer
    protected val conversionBuffer: AVFrame

    protected var outputFile: String = filename

    init {
        av_log_set_level(AV_LOG_TRACE)
        av_register_all()

        codec = avcodec_find_encoder(AV_CODEC_ID_H264)
        if(codec == null) {
            logger.error("Could not find H264 encoder")
        }

        codecContext = avcodec_alloc_context3(codec)
        if(codecContext == null) {
            logger.error("Could not allocate video codecContext")
        }

        codecContext.codec_id(codec.id())
        codecContext.bit_rate(400000)
        codecContext.width(512)
        codecContext.height(512)
        codecContext.time_base(timebase)
        codecContext.framerate(AVRational().num(25).den(1))
        codecContext.gop_size(10)
        codecContext.max_b_frames(1)
        codecContext.pix_fmt(AV_PIX_FMT_YUV420P)
        codecContext.codec_type(AVMEDIA_TYPE_VIDEO)

        av_opt_set(codecContext.priv_data(), "preset", "ultrafast", 0)
        var ret = avcodec_open2(codecContext, codec, AVDictionary())
        if(ret < 0) {
            logger.error("Could not open codec")
        }

        frame = av_frame_alloc()
        frame.format(codecContext.pix_fmt())
        frame.width(codecContext.width())
        frame.height(codecContext.height())

        val networked = false
        val url = "rtp://127.0.0.1:13337"

        val format = if(networked) {
            outputFile = url
            logger.info("Using network streaming, serving at $url")
            avformat_network_init()

            "mp4".to(av_guess_format("mp4", "mp4", outputFile))
        } else {
            "matroska".to(av_guess_format("matroska", null, null))
        }

        ret = avformat_alloc_output_context2(outputContext, format.second, format.first, outputFile)
        if(ret < 0) {
            logger.error("Could not allocate output context: $ret")
        }

        stream = avformat_new_stream(outputContext, null)
        if(stream == null) {
            logger.error("Could not allocate stream")
        }
        stream.time_base(timebase)
        stream.id(outputContext.nb_streams()-1)
        stream.r_frame_rate(codecContext.framerate())
        stream.codecpar().codec_tag(0)
        logger.info("Stream ID will be ${stream.id()}")

//        if(outputContext.oformat().flags() and AVFMT_GLOBALHEADER == 1) {
//            logger.info("Setting globalheader")
            codecContext.flags(codecContext.flags() or AV_CODEC_FLAG_GLOBAL_HEADER)
//        }

        ret = avcodec_parameters_from_context(stream.codecpar(), codecContext)
        if(ret < 0) {
            logger.error("Could not get codec parameters")
        }

        ret = av_frame_get_buffer(frame, 32)
        if(ret < 0) {
            logger.error("Could not allocate frame data")
        }

        av_dump_format(outputContext, 0, outputFile, 1)

        logger.info("IOContext: ${outputContext.pb()}")
        outputContext.pb(AVIOContext())

        ret = avio_open(outputContext.pb(), outputFile, AVIO_FLAG_WRITE)

        if(ret < 0) {
            logger.error("Failed to open output file $outputFile: $ret")
        }

        logger.info("Will write to $outputFile, with format ${String(outputContext.oformat().long_name().stringBytes)}")

        ret = avformat_write_header(outputContext, AVDictionary())

        if(ret < 0) {
            logger.error("Failed to write header: ${ffmpegErrorToString(ret)}/$ret")
        }

        packet = av_packet_alloc()
        conversionContext = swscale.sws_getContext(
            frameWidth, frameHeight,
            AV_PIX_FMT_BGRA,
            frame.width(), frame.height(),
            AV_PIX_FMT_YUV420P, SWS_BICUBIC,
            null, null, doubleArrayOf(0.0, 0.0))
        // line size is 4*frameWidth, as original data is BGRA
        lineSize = memAllocInt(1).put(0, 4*frameWidth)
        frameLineSize = memAllocInt(1).put(0, 3*frameWidth)
        conversionBuffer = av_frame_alloc()

        conversionBuffer.format(codecContext.pix_fmt())
        conversionBuffer.width(frameWidth)
        conversionBuffer.height(frameHeight)

        ret = av_frame_get_buffer(conversionBuffer, 32)

        if(ret < 0) {
            logger.error("Could not allocate space for conversion buffer: ${ffmpegErrorToString(ret)}")
        }
    }

    private fun toTime(timestamp: Long, base: AVRational): String {
        if(timestamp == AV_NOPTS_VALUE) {
            return "NOPTS"
        } else {
            return String.format("%.6g", (1.0*base.num())/(1.0*base.den())*timestamp)
        }
    }

    private fun logPacket(packet: AVPacket) {
        logger.info("pts: ${packet.pts()} pts_time: ${toTime(packet.pts(), timebase)} dts: ${packet.dts()} dts_time: ${toTime(packet.dts(), timebase)} duration: ${packet.duration()} duration_time: ${toTime(packet.duration(), timebase)} stream_index: ${packet.stream_index()}")
    }

    var noencode = false
    fun encodeFrame(data: ByteBuffer?) {
        if(noencode && data != null) {
            return
        }
        av_frame_make_writable(frame)

        var ret = if(data != null) {
            conversionBuffer.data(0, BytePointer(data))
            swscale.sws_scale(conversionContext,
                conversionBuffer.data(), conversionBuffer.linesize(),
                0, codecContext.height(),
                frame.data(), frame.linesize())

            frame.pts(frameNum)

            avcodec_send_frame(codecContext, frame)
        } else {
            avcodec_send_frame(codecContext, null)
        }

        while(ret >= 0) {
            ret = avcodec_receive_packet(codecContext, packet)

            if(ret == -11 || ret == AVERROR_EOF) {
                frameNum++
                return
            } else if(ret < 0){
                logger.error("Error encoding frame $frameNum: $ret")
                noencode = true
            }

            logger.info("$frameNum: Received packet of size ${packet.size()}")

//            packet.pts(AV_NOPTS_VALUE)
//            packet.dts(AV_NOPTS_VALUE)
            av_packet_rescale_ts(packet, timebase, stream.time_base())
            packet.stream_index(stream.index())

            logPacket(packet)

//            if(stream.codec().coded_frame().key_frame() == 1) {
                packet.flags(packet.flags() or AV_PKT_FLAG_KEY)
//            }

            ret = av_write_frame(outputContext, packet)
            av_packet_unref(packet)

            if(ret < 0) {
                logger.error("Error writing frame $frameNum: ${ffmpegErrorToString(ret)}")
                logger.error("Packet data: ${packet.size()} ${packet.dts()} ${packet.pts()} ${packet.flags()}")
                noencode = true
            }
        }

        frameNum++
    }

    private fun ffmpegErrorToString(errorCode: Int): String {
        val buffer = ByteArray(1024)
        av_make_error_string(buffer, buffer.size*1L, errorCode)

        return String(buffer)
    }

    fun finish() {
        encodeFrame(null)

//        av_write_trailer(outputContext)
        avio_closep(outputContext.pb())
        avformat_free_context(outputContext)

        logger.info("Finished recording $outputFile, wrote $frameNum frames.")
    }
}
