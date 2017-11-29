package graphics.scenery.utils

import org.bytedeco.javacpp.*
import org.bytedeco.javacpp.avcodec.*
import org.bytedeco.javacpp.avformat.*
import org.bytedeco.javacpp.avutil.*
import java.nio.ByteBuffer

class H264Encoder(val frameWidth: Int, val frameHeight: Int) {
    protected val logger by LazyLogger()
    protected val frame: AVFrame
    protected val codec: AVCodec
    protected val context: AVCodecContext

    protected var frameNum = 0L

    init {
       av_register_all()

        codec = avcodec_find_encoder(AV_CODEC_ID_H264)
        if(codec == null) {
            logger.error("Could not find H264 encoder")
        }

        context = avcodec_alloc_context3(codec)
        if(context == null) {
            logger.error("Could not allocate video context")
        }

        context.bit_rate(400000)
        context.width(512)
        context.height(512)
        context.time_base(AVRational(1L).num(1).den(25))
        context.gop_size(10)
        context.max_b_frames(1)
        context.pix_fmt(AV_PIX_FMT_YUV420P)

        av_opt_set(context.priv_data(), "preset", "fast", 0)
        avcodec_open2(context, codec, AVDictionary())

        frame = av_frame_alloc()
        frame.format(context.pix_fmt())
        frame.width(context.width())
        frame.height(context.height())

        val ret = av_image_alloc(frame.data(), frame.linesize(), context.width(), context.height(), context.pix_fmt(), 32)
        if(ret < 0) {
            logger.error("Could not allocate frame data")
        }
    }

    fun encodeFrame(data: ByteBuffer) {
        val packet = AVPacket()
        av_init_packet(packet)

        frame.data(0, BytePointer(data))
        frame.pts(frameNum)
        val ret = avcodec_send_frame(context, frame)

        if(ret == -11) {
            avcodec_receive_packet(context, packet)
        }

        if(ret < 0 && ret != -11) {
            logger.error("Error encoding frame $frameNum: $ret")
        }

        frameNum++
    }
}
