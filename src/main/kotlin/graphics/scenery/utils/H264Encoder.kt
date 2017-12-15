package graphics.scenery.utils

import org.bytedeco.javacpp.*
import org.bytedeco.javacpp.avcodec.*
import org.bytedeco.javacpp.avformat.*
import org.bytedeco.javacpp.avutil.*
import java.nio.ByteBuffer

class H264Encoder(val frameWidth: Int, val frameHeight: Int, filename: String) {
    protected val logger by LazyLogger()
    protected val frame: AVFrame
    protected val codec: AVCodec
    protected val codecContext: AVCodecContext
    protected val outputContext: AVFormatContext = AVFormatContext()
    protected val stream: AVStream

    protected var frameNum = 0L
    protected val timebase = AVRational().num(1).den(25)

    protected var outputFile: String = filename

    init {
        var ret = 0
        av_log_set_level(AV_LOG_TRACE)
        avcodec_register_all()
        av_register_all()

        val networked = false
        val url = "rtp://127.0.0.1:13337"

        val format = if(networked) {
            outputFile = url
            logger.info("Using network streaming, serving at $url")
            avformat_network_init()

            "rtp".to(av_guess_format("rtp", null, null))
        } else {
            "mp4".to(av_guess_format("mp4", null, null))
        }

        ret = avformat_alloc_output_context2(outputContext, format.second, format.first, outputFile)
        if(ret < 0) {
            logger.error("Could not allocate output context: $ret")
        }

        outputContext.video_codec_id(AV_CODEC_ID_H264)
        outputContext.audio_codec_id(AV_CODEC_ID_NONE)

        codec = avcodec_find_encoder(outputContext.video_codec_id())
        if(codec == null) {
            logger.error("Could not find H264 encoder")
        }

        codecContext = avcodec_alloc_context3(codec)
        if(codecContext == null) {
            logger.error("Could not allocate video codecContext")
        }

        codecContext.codec_id(outputContext.oformat().video_codec())
        codecContext.bit_rate(400000)
        codecContext.width(512)
        codecContext.height(512)
        codecContext.time_base(timebase)
//        codecContext.framerate(AVRational().num(25).den(1))
        codecContext.gop_size(10)
        codecContext.max_b_frames(1)
        codecContext.pix_fmt(AV_PIX_FMT_YUV420P)
//        codecContext.codec_type(AVMEDIA_TYPE_VIDEO)

        if(outputContext.oformat().flags() and AVFMT_GLOBALHEADER == 1) {
            logger.info("Output format requires global format header")
            codecContext.flags(codecContext.flags() or CODEC_FLAG_GLOBAL_HEADER)
        }
//        av_opt_set(codecContext.priv_data(), "preset", "ultrafast", 0)
        ret = avcodec_open2(codecContext, codec, AVDictionary())
        if(ret < 0) {
            logger.error("Could not open codec")
        }

        stream = avformat_new_stream(outputContext, null)
        if(stream == null) {
            logger.error("Could not allocate stream")
        }

//        stream.time_base(timebase)
        stream.id(outputContext.nb_streams()-1)
//        stream.r_frame_rate(codecContext.framerate())

        logger.info("Stream ID will be ${stream.id()}")

        frame = av_frame_alloc()
        frame.format(codecContext.pix_fmt())
        frame.width(codecContext.width())
        frame.height(codecContext.height())

        ret = avcodec_parameters_from_context(stream.codecpar(), codecContext)
        if(ret < 0) {
            logger.error("Could not get codec parameters")
        }

        ret = av_frame_get_buffer(frame, 32)
        if(ret < 0) {
            logger.error("Could not allocate frame data")
        }

        av_dump_format(outputContext, 0, outputFile, 1)

        if(outputContext.oformat().flags() and AVFMT_NOFILE == 0) {
            outputContext.pb(AVIOContext())
            logger.info("IOContext: ${outputContext.pb()}")

            ret = avio_open(outputContext.pb(), outputFile, AVIO_FLAG_WRITE)

            if (ret < 0) {
                logger.error("Failed to open output file $outputFile: $ret")
            }
        } else {
            logger.info("Not opening file as not required by outputContext")
        }

        logger.info("Will write to $outputFile, with format ${String(outputContext.oformat().long_name().stringBytes)}")

        ret = avformat_write_header(outputContext, AVDictionary())

        if(ret < 0) {
            val buffer = ByteArray(1024)
            av_make_error_string(buffer, buffer.size*1L, ret)
            logger.error("Failed to write header: ${String(buffer)}/$ret")
        }
    }

    fun encodeFrame(data: ByteBuffer?) {
        val packet = AVPacket()
        av_init_packet(packet)

        av_frame_make_writable(frame)

        frame.data(0, BytePointer(data))
        frame.pts(frameNum)

        var ret = if(data != null) {
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
            }

            av_packet_rescale_ts(packet, timebase, stream.time_base())
            packet.stream_index(0)
            packet.pts(AV_NOPTS_VALUE)
            packet.dts(AV_NOPTS_VALUE)
            packet.pos(-1)
            ret = av_write_frame(outputContext, packet)

            if(ret < 0) {
                logger.error("Error writing frame $frameNum: $ret")
            }
        }

        frameNum++
    }

    fun finish() {
        encodeFrame(null)

        av_write_trailer(outputContext)
        avio_closep(outputContext.pb())
        avformat_free_context(outputContext)

        logger.info("Finished recording $outputFile, wrote $frameNum frames.")
    }
}
