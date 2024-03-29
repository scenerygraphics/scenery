package graphics.scenery.utils

import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avformat.*
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.ffmpeg.global.swscale.*
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.PointerPointer
import kotlin.concurrent.thread

/**
 * H264 decoder class
 *
 * Experimental class for enabling decoding of movie recordings and encoded streams, based on ffmpeg.
 *
 * Source for ffmpeg-based video decoding: https://github.com/bytedeco/javacpp-presets/blob/master/ffmpeg/samples/ReadFewFrame.java
 *
 * @param[filename] The file name under which the movie is saved.
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */

class VideoDecoder(val filename: String) {

    private val logger by lazyLogger()
    private val formatContext = AVFormatContext(null)
    private val pkt = AVPacket()
    private var vidStreamIdx = -1
    private val codecCtx = avcodec_alloc_context3(null)
    private var swsCtx: SwsContext? = null
    private val pFrameRGB = av_frame_alloc()
    private val frm = av_frame_alloc()

    var nextFrameExists = true
    var videoWidth: Int = 0
    var videoHeight: Int = 0

    private var ready: Boolean = false
    @Volatile private var error = false
    @Volatile private var eVal = 0
    private var ret = 0

    init {

        thread {
            if (logger.isDebugEnabled) {
                av_log_set_level(AV_LOG_TRACE)
            } else {
                av_log_set_level(AV_LOG_ERROR)
            }

            val videoPath = filename

            ret = avformat_open_input(formatContext, videoPath, null, null)
            if (ret < 0) {
                eVal = ret
                logger.error("Open video file $videoPath failed. Error code: $eVal")
                error = true
                return@thread
            } else {
                logger.debug("Video found and opened")
            }

            ret = avformat_find_stream_info(formatContext, null as PointerPointer<*>?)

            if (ret < 0) {
                eVal = ret
                logger.error("Could not find stream information. Error code: $eVal")
                error = true
                return@thread
            }

            av_dump_format(formatContext, 0, videoPath, 0)

            var i = 0
            while (i < formatContext.nb_streams()) {
                if (formatContext.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
                    vidStreamIdx = i
                    break
                }
                i++
            }

            if (vidStreamIdx == -1) {
                logger.error("Cannot find video stream")
                error = true
                return@thread
            } else {
                logger.debug(
                    "Video stream %d with resolution %dx%d\n", vidStreamIdx,
                    formatContext.streams(i).codecpar().width(),
                    formatContext.streams(i).codecpar().height()
                )
            }

            ret = avcodec_parameters_to_context(codecCtx, formatContext.streams(vidStreamIdx).codecpar())
            if (ret < 0) {
                eVal = ret
                logger.error("Could not fill codec context. Error code: $eVal")
                error = true
                return@thread
            }

            val codec = avcodec_find_decoder(codecCtx.codec_id())
            if (codec == null) {
                logger.error("Unsupported codec for video file")
                error = true
                return@thread
            }

            ret = avcodec_open2(codecCtx, codec, null as PointerPointer<*>?)
            if (ret < 0) {
                eVal = ret
                logger.error("Can not open codec. Error code: $eVal")
                error = true
                return@thread
            }

            if (pFrameRGB == null) {
                logger.error("Could not allocate AVFrame structure")
                error = true
                return@thread
            }

            // Determine required buffer size and allocate buffer
            val numBytes = av_image_get_buffer_size(
                AV_PIX_FMT_RGBA, codecCtx.width(),
                codecCtx.height(), 1
            )
            val buffer = BytePointer(av_malloc(numBytes.toLong()))

            videoHeight = codecCtx.height()
            videoWidth = codecCtx.width()

            swsCtx = sws_getContext(
                codecCtx.width(),
                codecCtx.height(),
                codecCtx.pix_fmt(),
                codecCtx.width(),
                codecCtx.height(),
                AV_PIX_FMT_RGBA,
                SWS_BILINEAR, null, null,
                null as DoublePointer?
            )

            if (swsCtx == null) {
                logger.error("Can not use sws")
                error = true
                return@thread
            }

            av_image_fill_arrays(
                pFrameRGB.data(), pFrameRGB.linesize(),
                buffer, AV_PIX_FMT_RGBA, codecCtx.width(), codecCtx.height(), 1
            )

            ready = true
        }

        while(!ready && !error) {
            Thread.sleep(5)
        }

        if(error) {
            logger.error("Not decoding, error occurred during initialisation.")
            if(eVal < 0) {
                logger.error("Error is: ${ffmpegErrorString(eVal)}")
            }
        }
    }

    fun decodeFrame(): ByteArray? {

        var finalize = false

        if(av_read_frame(formatContext, pkt) < 0) {
            // if the video has no more frames, finalize the objects used for decoding

            finalize = true
            nextFrameExists = false
        }

        if (pkt.stream_index() == vidStreamIdx) {
            ret = avcodec_send_packet(codecCtx, pkt)
            if(ret < 0) {
                logger.debug("Error sending frame data to decoder. Error code: $ret")
                logger.debug("Error is: ${ffmpegErrorString(ret)}")
            }
            ret = avcodec_receive_frame(codecCtx, frm)
            if(ret < 0) {
                logger.debug("Error receiving data from decoder. Error code: $ret.")
                logger.debug("Error is: ${ffmpegErrorString(ret)}")
            }
        }

        val image : ByteArray? = if(ret >= 0) {
            // if there were no errors while decoding, then scale and fetch the image
            sws_scale(
                swsCtx,
                frm.data(),
                frm.linesize(),
                0,
                codecCtx.height(),
                pFrameRGB.data(),
                pFrameRGB.linesize()
            )
            getImage(pFrameRGB, codecCtx.width(), codecCtx.height())
        } else {
            // if error had occurred while decoding
            null
        }

        av_packet_unref(pkt)

        if(finalize) {
            av_frame_free(frm)
            avcodec_close(codecCtx)
            avcodec_free_context(codecCtx)
            avformat_close_input(formatContext)

            logger.debug("Finished decoding the movie")
        }

        return image
    }

    private fun getImage(pFrame: AVFrame, width: Int, height: Int) : ByteArray {
        val image = ByteArray(width * height * 4)
        val data = pFrame.data(0)
        val l = pFrame.linesize(0)
        for (y in 0 until height) {
            val h = height - 1 - y
            data.position((h * l).toLong()).get(image, width * y * 4, width * 4)
        }
        return image
    }

    private fun ffmpegErrorString(returnCode: Int): String {
        val buffer = ByteArray(1024, { _ -> 0 })
        av_make_error_string(buffer, buffer.size*1L, returnCode)

        return String(buffer, 0, buffer.indexOfFirst { it == 0.toByte() })
    }
}