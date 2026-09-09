package graphics.scenery.controls

import graphics.scenery.*
import graphics.scenery.backends.Display
import graphics.scenery.backends.vulkan.VU
import graphics.scenery.backends.vulkan.VulkanDevice
import graphics.scenery.backends.vulkan.VulkanTexture
import graphics.scenery.backends.vulkan.endCommandBuffer
import graphics.scenery.utils.GLBReader
import graphics.scenery.utils.duplicateGeometry
import graphics.scenery.utils.lazyLogger
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.joml.*
import org.lwjgl.openxr.*
import org.lwjgl.PointerBuffer
import org.lwjgl.openxr.EXTInteractionRenderModel.*
import org.lwjgl.openxr.EXTRenderModel.*
import org.lwjgl.openxr.EXTUUIUD.XR_EXT_UUID_EXTENSION_NAME
import org.lwjgl.openxr.KHRVulkanEnable.*
import org.lwjgl.openxr.XR10.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memAlloc
import org.lwjgl.system.MemoryUtil.memFree
import org.lwjgl.system.MemoryUtil.memUTF8
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.scijava.ui.behaviour.*
import org.scijava.ui.behaviour.io.InputTriggerConfig
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.tan

/**
 * [TrackerInput] and [Display] implementation for OpenXR, the successor of OpenVR, which was
 * dropped from LWJGL in 3.4.3. Uses the `XR_KHR_vulkan_enable` extension.
 *
 * Initialisation is two-phase, as the renderer needs the required Vulkan extensions before it
 * creates its instance and device: the constructor creates the [XrInstance] and queries the
 * system, while the [XrSession], spaces, actions and swapchain follow in [initializeSession] on
 * the first [submitToCompositorVulkan].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[seated] Whether the user is assumed to be sitting or not. Selects a LOCAL over a STAGE reference space.
 * @property[useCompositor] Whether the OpenXR compositor should be used for frame submission.
 * @property[applicationName] The application name handed to the OpenXR runtime.
 * @constructor Creates a new OpenXR HMD instance.
 */
open class OpenXRHMD(
    val seated: Boolean = false,
    val useCompositor: Boolean = true,
    val applicationName: String = "scenery"
) : TrackerInput, Display, Hubable {

    /** slf4j logger instance */
    protected val logger by lazyLogger()

    /** The Hub to use for communication */
    override var hub: Hub? = null

    /** Has the instance/system been initialised? */
    @Volatile
    protected var initialized = false

    /** Has the session, including swapchain and actions, been initialised? */
    @Volatile
    protected var sessionInitialized = false

    /** The OpenXR instance. Created in the constructor. */
    protected var instance: XrInstance? = null

    /** The OpenXR session. Created lazily, once Vulkan handles are available. */
    protected var session: XrSession? = null

    /** The system ID of the HMD. */
    protected var systemId: Long = 0L

    /** The main, application-facing reference space (STAGE or LOCAL, depending on [seated]). */
    protected var referenceSpace: XrSpace? = null

    /** The VIEW reference space, used to derive the head pose. */
    protected var viewSpace: XrSpace? = null

    /** The color swapchain handed to the compositor. */
    protected var swapchain: XrSwapchain? = null

    /** The images backing [swapchain]. */
    protected var swapchainImages: XrSwapchainImageVulkanKHR.Buffer? = null

    /** The format [swapchain] was created with. */
    protected var swapchainFormat: Int = 0

    /** Per-eye render target size, as recommended by the runtime. */
    protected var recommendedSize = Vector2i(1920, 1080)

    /** The per-eye view configuration views, queried once at startup. */
    protected var viewConfigurationViews: XrViewConfigurationView.Buffer? = null

    /** Views located for the current frame, holding per-eye pose and FOV. */
    protected var views: XrView.Buffer? = null

    /** The current session state, as reported by the runtime. */
    @Volatile
    protected var sessionState: Int = XR_SESSION_STATE_UNKNOWN

    /** Whether the session is running, i.e. [xrBeginSession] has succeeded. */
    @Volatile
    protected var sessionRunning = false

    /** Whether [xrBeginFrame] has been called for the current frame. */
    @Volatile
    protected var frameStarted = false

    /** Whether the runtime asked us to render this frame. */
    @Volatile
    protected var shouldRender = false

    /** Predicted display time of the current frame, used for pose prediction. */
    @Volatile
    protected var predictedDisplayTime = 0L

    /** Storage for the poses of all tracked devices. */
    protected var trackedDevices = ConcurrentHashMap<String, TrackedDevice>()

    /** Cache for per-eye projection matrices. Refreshed every frame, as the FOV may change. */
    protected var eyeProjectionCache: ArrayList<Matrix4f?> = arrayListOf(null, null)

    /** Cache for head-to-eye transform matrices. */
    protected var eyeTransformCache: ArrayList<Matrix4f?> = arrayListOf(null, null)

    /** Near plane the cached projections were calculated with. */
    protected var cachedNearPlane = 0.05f

    /** Far plane the cached projections were calculated with. */
    protected var cachedFarPlane = 1000.0f

    /** Command pool used for the blit into the OpenXR swapchain image. */
    private var commandPool = -1L

    /** Disables submission in case of unrecoverable compositor errors. */
    protected var disableSubmission: Boolean = false

    /** Whether the runtime supports the render model extensions, decided at instance creation. */
    protected var renderModelsSupported = false

    /** Render model assets, cached by their glTF cache ID so identical controllers load once. */
    protected val renderModelCache = ConcurrentHashMap<String, Mesh>()

    /** The runtime's name, for informational purposes. */
    var runtimeName: String = ""
        private set

    /** The interaction profile currently bound to the controllers. */
    var interactionProfile: String = ""
        private set

    /** Name of the tracking system, used to build unique behaviour names. */
    var trackingSystemName: String = ""
        private set

    /**
     * Controller manufacturer, derived from the active interaction profile. OpenXR abstracts
     * hardware behind profiles, so this is a best-effort mapping kept for API compatibility
     * with [OpenVRHMD].
     */
    var manufacturer: Manufacturer = Manufacturer.Other
        private set

    /** Controller manufacturers scenery distinguishes for input profiles. */
    enum class Manufacturer {
        HTC,
        Oculus,
        WindowsMR,
        Valve,
        Other
    }

    override var events = TrackerInputEventHandlers()

    protected val inputHandler = MouseAndKeyHandler()
    protected val config: InputTriggerConfig = InputTriggerConfig()
    protected val inputMap = InputTriggerMap()
    protected val behaviourMap = BehaviourMap()

    /** Set of keys that are allowed to be repeated. */
    val allowRepeats = HashSet<Pair<OpenXRButton, TrackerRole>>(5, 0.8f)
    protected val keysDown = HashSet<Pair<OpenXRButton, TrackerRole>>(5, 0.8f)

    /** The action set holding all of scenery's actions. */
    protected var actionSet: XrActionSet? = null

    /** Pose actions for the left and right hand. */
    protected val poseActions = HashMap<TrackerRole, XrAction>()

    /** Per-hand action spaces, created from [poseActions]. */
    protected val poseSpaces = HashMap<TrackerRole, XrSpace>()

    /** Haptic output actions for the left and right hand. */
    protected val hapticActions = HashMap<TrackerRole, XrAction>()

    /** Boolean actions, keyed by the button they represent. */
    protected val buttonActions = HashMap<OpenXRButton, XrAction>()

    /** The thumbstick/trackpad 2D action, used to synthesise D-pad events. */
    protected var thumbstickAction: XrAction? = null

    /** Subaction paths for the left and right hand. */
    protected val handPaths = HashMap<TrackerRole, Long>()

    /** Last known thumbstick direction per hand, to emit edge-triggered D-pad events. */
    protected val lastThumbstickDirection = HashMap<TrackerRole, OpenXRButton?>()

    /**
     * Buttons supported by scenery's OpenXR action set. Unlike OpenVR, OpenXR is action-based, so
     * these are mapped to concrete input paths per interaction profile in [suggestBindings].
     */
    enum class OpenXRButton {
        Left,
        Right,
        Up,
        Down,
        Menu,
        Side,
        Trigger,
        A,
        B,
        System,
        Touchpad,
        Thumbstick
    }

    init {
        inputHandler.setBehaviourMap(behaviourMap)
        inputHandler.setInputMap(inputMap)

        try {
            initializeInstance()
        } catch (e: UnsatisfiedLinkError) {
            logger.error("OpenXR support library not found, skipping initialization.")
            logger.error(e.message + "\n" + e.stackTrace.joinToString("\n"))
            initialized = false
        } catch (e: Exception) {
            logger.error("Could not initialise OpenXR: $e")
            initialized = false
        }

        // vsync leads to wrong prediction and "swimming" artifacts.
        hub?.get<Settings>()?.let { settings ->
            logger.info("Disabling vsync, as frame swap is governed by the OpenXR compositor.")
            settings.set("Renderer.DisableVsync", true)
        }
    }

    /**
     * Creates the [XrInstance] and queries the system, view configuration and render target size.
     */
    protected fun initializeInstance() {
        stackPush().use { stack ->
            val extensionCount = stack.callocInt(1)
            checkResult(
                xrEnumerateInstanceExtensionProperties(null as ByteBuffer?, extensionCount, null),
                "Enumerating instance extensions"
            )

            val properties = XrExtensionProperties.calloc(extensionCount[0], stack)
            properties.forEach { it.type(XR_TYPE_EXTENSION_PROPERTIES) }
            checkResult(
                xrEnumerateInstanceExtensionProperties(null as ByteBuffer?, extensionCount, properties),
                "Enumerating instance extensions"
            )

            val available = properties.map { it.extensionNameString() }
            logger.debug("OpenXR runtime supports ${available.size} extensions: ${available.joinToString(", ")}")

            if (XR_KHR_VULKAN_ENABLE_EXTENSION_NAME !in available) {
                logger.error("The OpenXR runtime does not support $XR_KHR_VULKAN_ENABLE_EXTENSION_NAME, which scenery requires.")
                initialized = false
                return
            }

            // Render models are optional: without them, placeholder geometry is used. Their
            // cache IDs are XrUuidEXT, so XR_EXT_uuid has to be enabled alongside them.
            renderModelsSupported = XR_EXT_RENDER_MODEL_EXTENSION_NAME in available
                && XR_EXT_INTERACTION_RENDER_MODEL_EXTENSION_NAME in available
                && XR_EXT_UUID_EXTENSION_NAME in available

            if (!renderModelsSupported) {
                val missing = listOf(
                    XR_EXT_UUID_EXTENSION_NAME,
                    XR_EXT_RENDER_MODEL_EXTENSION_NAME,
                    XR_EXT_INTERACTION_RENDER_MODEL_EXTENSION_NAME
                ).filter { it !in available }

                logger.info(
                    "Runtime is missing ${missing.joinToString(", ")}, falling back to placeholder controller models."
                )
            }

            val wanted = mutableListOf(XR_KHR_VULKAN_ENABLE_EXTENSION_NAME)
            if (renderModelsSupported) {
                wanted += XR_EXT_UUID_EXTENSION_NAME
                wanted += XR_EXT_RENDER_MODEL_EXTENSION_NAME
                wanted += XR_EXT_INTERACTION_RENDER_MODEL_EXTENSION_NAME
            }

            val extensionNames = stack.callocPointer(wanted.size)
            wanted.forEach { extensionNames.put(stack.UTF8(it)) }
            extensionNames.flip()

            logger.debug("Requesting OpenXR extensions: ${wanted.joinToString(", ")}")

            // 1.0 is requested deliberately: the render model extensions are built on
            // XR_EXT_uuid rather than core 1.1 types, so they work on a 1.0 instance, and
            // runtimes such as SteamVR reject 1.1 outright with XR_ERROR_API_VERSION_UNSUPPORTED.
            val instancePointer = stack.callocPointer(1)

            var (result, createInfo) = createInstance(stack, XR_API_VERSION_1_0, extensionNames, instancePointer)

            // Should a runtime advertise the render model extensions but refuse to enable them,
            // retry without, so controllers lose their models rather than VR failing entirely.
            if (result != XR_SUCCESS && renderModelsSupported) {
                logger.warn(
                    "Could not create an OpenXR instance with render model support (${resultName(result)}), " +
                        "retrying without it."
                )

                renderModelsSupported = false

                val fallbackNames = stack.callocPointer(1)
                fallbackNames.put(stack.UTF8(XR_KHR_VULKAN_ENABLE_EXTENSION_NAME))
                fallbackNames.flip()

                val retry = createInstance(stack, XR_API_VERSION_1_0, fallbackNames, instancePointer)
                result = retry.first
                createInfo = retry.second
            }

            if (result != XR_SUCCESS) {
                // A missing runtime is the common, benign case.
                if (result == XR_ERROR_RUNTIME_UNAVAILABLE) {
                    logger.warn("No OpenXR runtime available. Is your headset connected and its runtime active?")
                } else {
                    logger.error("Failed to create OpenXR instance: ${resultName(result)} ($result)")
                }

                initialized = false
                return
            }

            val xrInstance = XrInstance(instancePointer[0], createInfo)
            instance = xrInstance

            val instanceProperties = XrInstanceProperties.calloc(stack)
                .type(XR_TYPE_INSTANCE_PROPERTIES)
            if (xrGetInstanceProperties(xrInstance, instanceProperties) == XR_SUCCESS) {
                runtimeName = instanceProperties.runtimeNameString()
                logger.info("OpenXR runtime: $runtimeName")
            }

            val systemGetInfo = XrSystemGetInfo.calloc(stack)
                .type(XR_TYPE_SYSTEM_GET_INFO)
                .formFactor(XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY)

            val systemIdBuffer = stack.callocLong(1)
            val systemResult = xrGetSystem(xrInstance, systemGetInfo, systemIdBuffer)

            if (systemResult != XR_SUCCESS) {
                if (systemResult == XR_ERROR_FORM_FACTOR_UNAVAILABLE) {
                    logger.warn("No HMD found. Are all cables connected and the headset turned on?")
                } else {
                    logger.error("Failed to get OpenXR system: ${resultToString(systemResult)} ($systemResult)")
                }

                xrDestroyInstance(xrInstance)
                instance = null
                initialized = false
                return
            }

            systemId = systemIdBuffer[0]

            val systemProperties = XrSystemProperties.calloc(stack)
                .type(XR_TYPE_SYSTEM_PROPERTIES)
            if (xrGetSystemProperties(xrInstance, systemId, systemProperties) == XR_SUCCESS) {
                trackingSystemName = systemProperties.systemNameString()
                logger.info("OpenXR system: $trackingSystemName")
            }

            // Recommended per-eye render target size.
            val viewCount = stack.callocInt(1)
            checkResult(
                xrEnumerateViewConfigurationViews(
                    xrInstance, systemId,
                    XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, viewCount, null
                ), "Enumerating view configuration views"
            )

            // Outlive the stack frame, freed in close().
            val configViews = XrViewConfigurationView.calloc(viewCount[0])
            configViews.forEach { it.type(XR_TYPE_VIEW_CONFIGURATION_VIEW) }
            checkResult(
                xrEnumerateViewConfigurationViews(
                    xrInstance, systemId,
                    XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, viewCount, configViews
                ), "Enumerating view configuration views"
            )

            viewConfigurationViews = configViews
            views = XrView.calloc(viewCount[0])
            views?.forEach { it.type(XR_TYPE_VIEW) }

            recommendedSize = Vector2i(
                configViews[0].recommendedImageRectWidth(),
                configViews[0].recommendedImageRectHeight()
            )

            // Required before session creation, or the runtime refuses to create the session.
            val requirements = XrGraphicsRequirementsVulkanKHR.calloc(stack)
                .type(XR_TYPE_GRAPHICS_REQUIREMENTS_VULKAN_KHR)
            xrGetVulkanGraphicsRequirementsKHR(xrInstance, systemId, requirements)

            initialized = true
            logger.info("Initialized OpenXR device with render target size ${recommendedSize.x()}x${recommendedSize.y()} per eye")
        }
    }

    /**
     * Creates an [XrInstance] asking for [apiVersion] and [extensionNames], storing the handle in
     * [instancePointer]. Returns the raw [XrResult].
     */
    protected fun createInstance(
        stack: MemoryStack, apiVersion: Long,
        extensionNames: PointerBuffer, instancePointer: PointerBuffer
    ): Pair<Int, XrInstanceCreateInfo> {
        val applicationInfo = XrApplicationInfo.calloc(stack)
            .apiVersion(apiVersion)
            .applicationVersion(1)
            .engineVersion(1)

        // Names are size-limited by the spec, and must be set before the struct is copied below.
        applicationInfo.applicationName(stack.UTF8(applicationName.take(XR_MAX_APPLICATION_NAME_SIZE - 1)))
        applicationInfo.engineName(stack.UTF8("scenery"))

        val createInfo = XrInstanceCreateInfo.calloc(stack)
            .type(XR_TYPE_INSTANCE_CREATE_INFO)
            .next(NULL)
            .createFlags(0)
            .applicationInfo(applicationInfo)
            .enabledApiLayerNames(null)
            .enabledExtensionNames(extensionNames)

        return xrCreateInstance(createInfo, instancePointer) to createInfo
    }

    /**
     * Creates the [XrSession], its reference spaces and action set, once Vulkan handles are known.
     */
    @Synchronized
    protected fun initializeSession(instanceVk: VkInstance, device: VulkanDevice, queueFamilyIndex: Int, queueIndex: Int) {
        val xrInstance = instance ?: return

        if (sessionInitialized) {
            return
        }

        stackPush().use { stack ->
            // OpenXR dictates which physical device the session must use. If the renderer picked a
            // different one -- likely on multi-GPU machines -- xrCreateSession fails validation,
            // so the mismatch is reported here rather than as an opaque error.
            val required = getVulkanPhysicalDevice(instanceVk)
            if (required != null && required.address() != device.physicalDevice.address()) {
                logger.error(
                    "OpenXR requires Vulkan physical device 0x{}, but the renderer is using 0x{}. " +
                        "Set -Dscenery.Renderer.Device or -Dscenery.Renderer.DeviceId to select the device " +
                        "the headset is attached to.",
                    required.address().toString(16), device.physicalDevice.address().toString(16)
                )
            }

            // The runtime's Vulkan API version bounds have to be respected by the instance.
            val requirements = XrGraphicsRequirementsVulkanKHR.calloc(stack)
                .type(XR_TYPE_GRAPHICS_REQUIREMENTS_VULKAN_KHR)
            if (xrGetVulkanGraphicsRequirementsKHR(xrInstance, systemId, requirements) == XR_SUCCESS) {
                logger.debug(
                    "OpenXR requires Vulkan between {} and {}",
                    versionString(requirements.minApiVersionSupported()),
                    versionString(requirements.maxApiVersionSupported())
                )
            }

            val graphicsBinding = XrGraphicsBindingVulkanKHR.calloc(stack)
                .type(XR_TYPE_GRAPHICS_BINDING_VULKAN_KHR)
                .next(NULL)
                .instance(instanceVk)
                .physicalDevice(device.physicalDevice)
                .device(device.vulkanDevice)
                .queueFamilyIndex(queueFamilyIndex)
                .queueIndex(queueIndex)

            val sessionCreateInfo = XrSessionCreateInfo.calloc(stack)
                .type(XR_TYPE_SESSION_CREATE_INFO)
                .next(graphicsBinding.address())
                .createFlags(0)
                .systemId(systemId)

            logger.debug(
                "Creating OpenXR session: systemId={}, vkInstance=0x{}, physicalDevice=0x{}, device=0x{}, queueFamily={}, queueIndex={}",
                systemId, instanceVk.address().toString(16),
                device.physicalDevice.address().toString(16),
                device.vulkanDevice.address().toString(16),
                queueFamilyIndex, queueIndex
            )

            val sessionPointer = stack.callocPointer(1)
            val result = xrCreateSession(xrInstance, sessionCreateInfo, sessionPointer)

            if (result != XR_SUCCESS) {
                logger.error("Failed to create OpenXR session: ${resultToString(result)} ($result)")

                if (result == XR_ERROR_VALIDATION_FAILURE) {
                    logger.error(
                        "A validation failure here usually means the Vulkan instance, device or queue " +
                            "does not match what the runtime expects. Check that the instance and device " +
                            "extensions from getVulkanInstanceExtensions()/getVulkanDeviceExtensions() were " +
                            "enabled, and that the physical device matches the one logged above."
                    )
                }

                disableSubmission = true
                return
            }

            val xrSession = XrSession(sessionPointer[0], xrInstance)
            session = xrSession

            // STAGE for room-scale, LOCAL for seated. Not all runtimes offer STAGE.
            val spaceCreateInfo = XrReferenceSpaceCreateInfo.calloc(stack)
                .type(XR_TYPE_REFERENCE_SPACE_CREATE_INFO)
                .referenceSpaceType(if (seated) XR_REFERENCE_SPACE_TYPE_LOCAL else XR_REFERENCE_SPACE_TYPE_STAGE)
            spaceCreateInfo.poseInReferenceSpace().orientation().set(0.0f, 0.0f, 0.0f, 1.0f)
            spaceCreateInfo.poseInReferenceSpace().`position$`().set(0.0f, 0.0f, 0.0f)

            val spacePointer = stack.callocPointer(1)
            var spaceResult = xrCreateReferenceSpace(xrSession, spaceCreateInfo, spacePointer)

            if (spaceResult != XR_SUCCESS && !seated) {
                logger.warn("Could not create a STAGE reference space, falling back to LOCAL.")
                spaceCreateInfo.referenceSpaceType(XR_REFERENCE_SPACE_TYPE_LOCAL)
                spaceResult = xrCreateReferenceSpace(xrSession, spaceCreateInfo, spacePointer)
            }

            checkResult(spaceResult, "Creating reference space")
            referenceSpace = XrSpace(spacePointer[0], xrSession)

            // VIEW space gives the head pose relative to the reference space.
            spaceCreateInfo.referenceSpaceType(XR_REFERENCE_SPACE_TYPE_VIEW)
            checkResult(xrCreateReferenceSpace(xrSession, spaceCreateInfo, spacePointer), "Creating view space")
            viewSpace = XrSpace(spacePointer[0], xrSession)

            createActions(xrInstance, xrSession)

            sessionInitialized = true
            logger.info("OpenXR session initialized.")
        }
    }

    /**
     * Creates the double-wide swapchain the renderer's images are blitted into, left eye in the
     * left half, right eye in the right half.
     */
    protected fun createSwapchain(preferredFormat: Int) {
        val xrSession = session ?: return

        stackPush().use { stack ->
            val formatCount = stack.callocInt(1)
            checkResult(xrEnumerateSwapchainFormats(xrSession, formatCount, null), "Enumerating swapchain formats")

            val formats = stack.callocLong(formatCount[0])
            checkResult(xrEnumerateSwapchainFormats(xrSession, formatCount, formats), "Enumerating swapchain formats")

            val supported = (0 until formatCount[0]).map { formats[it].toInt() }
            logger.debug("Runtime supports swapchain formats: ${supported.joinToString(", ")}")

            // Prefer the renderer's format so the blit needs no conversion.
            val format = when {
                preferredFormat in supported -> preferredFormat
                VK_FORMAT_R8G8B8A8_SRGB in supported -> VK_FORMAT_R8G8B8A8_SRGB
                VK_FORMAT_B8G8R8A8_SRGB in supported -> VK_FORMAT_B8G8R8A8_SRGB
                else -> supported.firstOrNull() ?: VK_FORMAT_R8G8B8A8_SRGB
            }

            if (format != preferredFormat) {
                logger.debug("Renderer format $preferredFormat is not supported by the runtime, using $format instead.")
            }

            swapchainFormat = format

            val createInfo = XrSwapchainCreateInfo.calloc(stack)
                .type(XR_TYPE_SWAPCHAIN_CREATE_INFO)
                .createFlags(0)
                .usageFlags((XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT or XR_SWAPCHAIN_USAGE_TRANSFER_DST_BIT).toLong())
                .format(format.toLong())
                .sampleCount(1)
                .width(recommendedSize.x() * 2)
                .height(recommendedSize.y())
                .faceCount(1)
                .arraySize(1)
                .mipCount(1)

            val swapchainPointer = stack.callocPointer(1)
            checkResult(xrCreateSwapchain(xrSession, createInfo, swapchainPointer), "Creating swapchain")

            val xrSwapchain = XrSwapchain(swapchainPointer[0], xrSession)
            swapchain = xrSwapchain

            val imageCount = stack.callocInt(1)
            checkResult(xrEnumerateSwapchainImages(xrSwapchain, imageCount, null), "Enumerating swapchain images")

            val images = XrSwapchainImageVulkanKHR.calloc(imageCount[0])
            images.forEach { it.type(XR_TYPE_SWAPCHAIN_IMAGE_VULKAN_KHR) }

            checkResult(
                xrEnumerateSwapchainImages(
                    xrSwapchain, imageCount,
                    XrSwapchainImageBaseHeader.create(images.address(), images.remaining())
                ),
                "Enumerating swapchain images"
            )

            swapchainImages = images
            logger.debug("Created OpenXR swapchain with ${imageCount[0]} images, format $format")
        }
    }

    /** Creates the action set, actions and action spaces, and suggests bindings. */
    protected fun createActions(xrInstance: XrInstance, xrSession: XrSession) {
        stackPush().use { stack ->
            val actionSetCreateInfo = XrActionSetCreateInfo.calloc(stack)
                .type(XR_TYPE_ACTION_SET_CREATE_INFO)
                .priority(0)
            actionSetCreateInfo.actionSetName(stack.UTF8("scenery"))
            actionSetCreateInfo.localizedActionSetName(stack.UTF8("scenery actions"))

            val actionSetPointer = stack.callocPointer(1)
            checkResult(xrCreateActionSet(xrInstance, actionSetCreateInfo, actionSetPointer), "Creating action set")
            val set = XrActionSet(actionSetPointer[0], xrInstance)
            actionSet = set

            handPaths[TrackerRole.LeftHand] = stringToPath(xrInstance, "/user/hand/left")
            handPaths[TrackerRole.RightHand] = stringToPath(xrInstance, "/user/hand/right")

            val subactionPaths = stack.callocLong(2)
            subactionPaths.put(0, handPaths[TrackerRole.LeftHand]!!)
            subactionPaths.put(1, handPaths[TrackerRole.RightHand]!!)

            // Subaction paths let one action be queried for either hand.
            val posePointer = stack.callocPointer(1)
            val poseCreateInfo = XrActionCreateInfo.calloc(stack)
                .type(XR_TYPE_ACTION_CREATE_INFO)
                .actionType(XR_ACTION_TYPE_POSE_INPUT)
                .subactionPaths(subactionPaths)
            poseCreateInfo.actionName(stack.UTF8("hand_pose"))
            poseCreateInfo.localizedActionName(stack.UTF8("Hand pose"))
            checkResult(xrCreateAction(set, poseCreateInfo, posePointer), "Creating pose action")
            val poseAction = XrAction(posePointer[0], set)

            listOf(TrackerRole.LeftHand, TrackerRole.RightHand).forEach { role ->
                poseActions[role] = poseAction

                val actionSpaceCreateInfo = XrActionSpaceCreateInfo.calloc(stack)
                    .type(XR_TYPE_ACTION_SPACE_CREATE_INFO)
                    .action(poseAction)
                    .subactionPath(handPaths[role]!!)
                actionSpaceCreateInfo.poseInActionSpace().orientation().set(0.0f, 0.0f, 0.0f, 1.0f)
                actionSpaceCreateInfo.poseInActionSpace().`position$`().set(0.0f, 0.0f, 0.0f)

                val actionSpacePointer = stack.callocPointer(1)
                checkResult(
                    xrCreateActionSpace(xrSession, actionSpaceCreateInfo, actionSpacePointer),
                    "Creating action space for $role"
                )
                poseSpaces[role] = XrSpace(actionSpacePointer[0], xrSession)
            }

            val hapticPointer = stack.callocPointer(1)
            val hapticCreateInfo = XrActionCreateInfo.calloc(stack)
                .type(XR_TYPE_ACTION_CREATE_INFO)
                .actionType(XR_ACTION_TYPE_VIBRATION_OUTPUT)
                .subactionPaths(subactionPaths)
            hapticCreateInfo.actionName(stack.UTF8("haptic"))
            hapticCreateInfo.localizedActionName(stack.UTF8("Haptic feedback"))
            checkResult(xrCreateAction(set, hapticCreateInfo, hapticPointer), "Creating haptic action")
            val hapticAction = XrAction(hapticPointer[0], set)
            hapticActions[TrackerRole.LeftHand] = hapticAction
            hapticActions[TrackerRole.RightHand] = hapticAction

            // Boolean actions, shared between hands via subaction paths.
            listOf(
                OpenXRButton.Trigger to "trigger",
                OpenXRButton.Side to "squeeze",
                OpenXRButton.Menu to "menu",
                OpenXRButton.A to "a_button",
                OpenXRButton.B to "b_button",
                OpenXRButton.System to "system",
                OpenXRButton.Touchpad to "trackpad_click",
                OpenXRButton.Thumbstick to "thumbstick_click"
            ).forEach { (button, name) ->
                val pointer = stack.callocPointer(1)
                val info = XrActionCreateInfo.calloc(stack)
                    .type(XR_TYPE_ACTION_CREATE_INFO)
                    .actionType(XR_ACTION_TYPE_BOOLEAN_INPUT)
                    .subactionPaths(subactionPaths)
                info.actionName(stack.UTF8(name))
                info.localizedActionName(stack.UTF8(name.replace('_', ' ').replaceFirstChar { it.uppercase() }))

                if (xrCreateAction(set, info, pointer) == XR_SUCCESS) {
                    buttonActions[button] = XrAction(pointer[0], set)
                } else {
                    logger.warn("Could not create action for $button")
                }
            }

            // Axis used to synthesise the D-pad events OpenVR delivered directly.
            val thumbstickPointer = stack.callocPointer(1)
            val thumbstickInfo = XrActionCreateInfo.calloc(stack)
                .type(XR_TYPE_ACTION_CREATE_INFO)
                .actionType(XR_ACTION_TYPE_VECTOR2F_INPUT)
                .subactionPaths(subactionPaths)
            thumbstickInfo.actionName(stack.UTF8("thumbstick"))
            thumbstickInfo.localizedActionName(stack.UTF8("Thumbstick"))
            if (xrCreateAction(set, thumbstickInfo, thumbstickPointer) == XR_SUCCESS) {
                thumbstickAction = XrAction(thumbstickPointer[0], set)
            }

            suggestBindings(xrInstance)

            val attachInfo = XrSessionActionSetsAttachInfo.calloc(stack)
                .type(XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO)
                .actionSets(stack.pointers(set.address()))

            checkResult(xrAttachSessionActionSets(xrSession, attachInfo), "Attaching action sets")
        }
    }

    /**
     * Suggests bindings per interaction profile. Runtimes pick the one matching the connected
     * hardware, so suggesting several is expected.
     */
    protected fun suggestBindings(xrInstance: XrInstance) {
        // Maps abstract buttons to input paths. Buttons a profile lacks are left unbound.
        val profiles = mapOf(
            "/interaction_profiles/khr/simple_controller" to mapOf(
                OpenXRButton.Trigger to "input/select/click",
                OpenXRButton.Menu to "input/menu/click"
            ),
            "/interaction_profiles/htc/vive_controller" to mapOf(
                OpenXRButton.Trigger to "input/trigger/click",
                OpenXRButton.Side to "input/squeeze/click",
                OpenXRButton.Menu to "input/menu/click",
                OpenXRButton.System to "input/system/click",
                OpenXRButton.Touchpad to "input/trackpad/click",
                // Vive controllers have no A/B, so the trackpad click stands in for A.
                OpenXRButton.A to "input/trackpad/click"
            ),
            "/interaction_profiles/valve/index_controller" to mapOf(
                OpenXRButton.Trigger to "input/trigger/click",
                OpenXRButton.Side to "input/squeeze/force",
                OpenXRButton.A to "input/a/click",
                OpenXRButton.B to "input/b/click",
                OpenXRButton.System to "input/system/click",
                OpenXRButton.Touchpad to "input/trackpad/force",
                OpenXRButton.Thumbstick to "input/thumbstick/click"
            ),
            "/interaction_profiles/oculus/touch_controller" to mapOf(
                OpenXRButton.Trigger to "input/trigger/value",
                OpenXRButton.Side to "input/squeeze/value",
                OpenXRButton.Menu to "input/menu/click",
                OpenXRButton.Thumbstick to "input/thumbstick/click"
            ),
            "/interaction_profiles/microsoft/motion_controller" to mapOf(
                OpenXRButton.Trigger to "input/trigger/value",
                OpenXRButton.Side to "input/squeeze/click",
                OpenXRButton.Menu to "input/menu/click",
                OpenXRButton.Touchpad to "input/trackpad/click",
                OpenXRButton.Thumbstick to "input/thumbstick/click",
                // WMR controllers have no A/B, so the trackpad click stands in for A.
                OpenXRButton.A to "input/trackpad/click"
            )
        )

        // Touch exposes A/B on the right, X/Y and menu on the left, so these bind per hand.
        val perHandOverrides = mapOf(
            "/interaction_profiles/oculus/touch_controller" to mapOf(
                (TrackerRole.LeftHand to OpenXRButton.A) to "input/x/click",
                (TrackerRole.LeftHand to OpenXRButton.B) to "input/y/click",
                (TrackerRole.RightHand to OpenXRButton.A) to "input/a/click",
                (TrackerRole.RightHand to OpenXRButton.B) to "input/b/click",
                (TrackerRole.LeftHand to OpenXRButton.Menu) to "input/menu/click"
            )
        )

        // Thumbstick/trackpad axis paths differ between profiles.
        val axisPaths = mapOf(
            "/interaction_profiles/htc/vive_controller" to "input/trackpad",
            "/interaction_profiles/valve/index_controller" to "input/thumbstick",
            "/interaction_profiles/oculus/touch_controller" to "input/thumbstick",
            "/interaction_profiles/microsoft/motion_controller" to "input/thumbstick"
        )

        profiles.forEach { (profile, buttons) ->
            stackPush().use { stack ->
                val bindings = ArrayList<Pair<XrAction, Long>>()
                val overrides = perHandOverrides[profile]

                listOf(TrackerRole.LeftHand, TrackerRole.RightHand).forEach { role ->
                    val hand = if (role == TrackerRole.LeftHand) "left" else "right"

                    poseActions[role]?.let { action ->
                        bindings.add(action to stringToPath(xrInstance, "/user/hand/$hand/input/grip/pose"))
                    }

                    hapticActions[role]?.let { action ->
                        bindings.add(action to stringToPath(xrInstance, "/user/hand/$hand/output/haptic"))
                    }

                    buttons.forEach buttons@{ (button, path) ->
                        // Only bind where an override exists for this hand.
                        val hasOverrideForButton = overrides?.keys?.any { it.second == button } == true
                        val override = overrides?.get(role to button)

                        if (hasOverrideForButton && override == null) {
                            return@buttons
                        }

                        buttonActions[button]?.let { action ->
                            bindings.add(action to stringToPath(xrInstance, "/user/hand/$hand/${override ?: path}"))
                        }
                    }

                    axisPaths[profile]?.let { axis ->
                        thumbstickAction?.let { action ->
                            bindings.add(action to stringToPath(xrInstance, "/user/hand/$hand/$axis"))
                        }
                    }
                }

                if (bindings.isEmpty()) {
                    return@use
                }

                val suggestedBindings = XrActionSuggestedBinding.calloc(bindings.size, stack)
                bindings.forEachIndexed { i, (action, path) ->
                    suggestedBindings[i].action(action).binding(path)
                }

                val suggestInfo = XrInteractionProfileSuggestedBinding.calloc(stack)
                    .type(XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING)
                    .interactionProfile(stringToPath(xrInstance, profile))
                    .suggestedBindings(suggestedBindings)

                val result = xrSuggestInteractionProfileBindings(xrInstance, suggestInfo)
                if (result != XR_SUCCESS) {
                    // Runtimes may reject profiles they do not know.
                    logger.debug("Could not suggest bindings for $profile: ${resultToString(result)}")
                } else {
                    logger.debug("Suggested ${bindings.size} bindings for $profile")
                }
            }
        }
    }

    /** Polls the event queue and begins/ends the session as the runtime requests. */
    protected fun pollEvents() {
        val xrInstance = instance ?: return

        stackPush().use { stack ->
            val eventBuffer = XrEventDataBuffer.calloc(stack)

            while (true) {
                eventBuffer.type(XR_TYPE_EVENT_DATA_BUFFER).next(NULL)

                if (xrPollEvent(xrInstance, eventBuffer) != XR_SUCCESS) {
                    break
                }

                when (eventBuffer.type()) {
                    XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED -> {
                        val event = XrEventDataSessionStateChanged.create(eventBuffer.address())
                        sessionState = event.state()
                        logger.debug("OpenXR session state changed to ${sessionStateToString(sessionState)}")
                        handleSessionStateChange()
                    }

                    XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING -> {
                        logger.warn("OpenXR instance loss pending, shutting down.")
                        initialized = false
                        disableSubmission = true
                    }

                    XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED -> {
                        updateInteractionProfile()
                    }

                    XR_TYPE_EVENT_DATA_EVENTS_LOST -> {
                        val event = XrEventDataEventsLost.create(eventBuffer.address())
                        logger.warn("Lost ${event.lostEventCount()} OpenXR events")
                    }
                }
            }
        }
    }

    /** Begins or ends the session, following the state the runtime moved us into. */
    protected fun handleSessionStateChange() {
        val xrSession = session ?: return

        when (sessionState) {
            XR_SESSION_STATE_READY -> {
                stackPush().use { stack ->
                    val beginInfo = XrSessionBeginInfo.calloc(stack)
                        .type(XR_TYPE_SESSION_BEGIN_INFO)
                        .primaryViewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)

                    val result = xrBeginSession(xrSession, beginInfo)
                    if (result == XR_SUCCESS) {
                        sessionRunning = true
                        logger.info("OpenXR session started.")
                    } else {
                        logger.error("Could not begin OpenXR session: ${resultToString(result)}")
                    }
                }
            }

            XR_SESSION_STATE_STOPPING -> {
                sessionRunning = false
                xrEndSession(xrSession)
                logger.info("OpenXR session stopped.")
            }

            XR_SESSION_STATE_EXITING, XR_SESSION_STATE_LOSS_PENDING -> {
                sessionRunning = false
                initialized = false
                logger.info("OpenXR session exiting.")
            }
        }
    }

    /** Queries the interaction profile currently bound to the controllers. */
    protected fun updateInteractionProfile() {
        val xrInstance = instance ?: return
        val xrSession = session ?: return

        stackPush().use { stack ->
            val state = XrInteractionProfileState.calloc(stack)
                .type(XR_TYPE_INTERACTION_PROFILE_STATE)

            val path = handPaths[TrackerRole.RightHand] ?: return

            if (xrGetCurrentInteractionProfile(xrSession, path, state) == XR_SUCCESS && state.interactionProfile() != NULL) {
                interactionProfile = pathToString(xrInstance, state.interactionProfile())

                manufacturer = when {
                    interactionProfile.contains("htc") -> Manufacturer.HTC
                    interactionProfile.contains("oculus") -> Manufacturer.Oculus
                    interactionProfile.contains("microsoft") -> Manufacturer.WindowsMR
                    interactionProfile.contains("valve") -> Manufacturer.Valve
                    else -> Manufacturer.Other
                }

                logger.info("Active interaction profile: $interactionProfile ($manufacturer)")

                // Render models only become available once a profile is bound, which happens
                // after the controllers were first seen, so any placeholder geometry handed out
                // then is replaced here.
                refreshRenderModels()
            }
        }
    }

    /**
     * Re-attempts render model loading for controllers that were connected before the runtime
     * bound an interaction profile, replacing their placeholder geometry.
     */
    protected fun refreshRenderModels() {
        if (!renderModelsSupported) {
            return
        }

        trackedDevices.values.filter { it.type == TrackedDeviceType.Controller }.forEach { device ->
            val model = device.model ?: return@forEach

            if (model.metadata["renderModelLoaded"] == true) {
                return@forEach
            }

            try {
                val replacement = Mesh()
                replacement.name = device.name

                if (loadRenderModel(device, replacement)) {
                    replacement.metadata["renderModelLoaded"] = true

                    // The node is already in the scene graph, so its children are swapped rather
                    // than the node itself being replaced.
                    model.children.toList().forEach { model.removeChild(it) }
                    replacement.children.toList().forEach { model.addChild(it) }
                    model.metadata["renderModelLoaded"] = true

                    logger.info("Replaced placeholder model for ${device.name} with the runtime's render model.")
                }
            } catch (e: Exception) {
                logger.warn("Could not refresh render model for ${device.name}: $e")
            }
        }
    }

    /**
     * Waits for the next frame, locates views and devices, and processes input. The OpenXR
     * equivalent of OpenVR's `WaitGetPoses`; blocks until the runtime wants rendering to begin.
     */
    @Synchronized
    override fun update() {
        if (!initialized) {
            return
        }

        pollEvents()

        val xrSession = session ?: return

        if (!sessionRunning) {
            return
        }

        stackPush().use { stack ->
            val frameWaitInfo = XrFrameWaitInfo.calloc(stack).type(XR_TYPE_FRAME_WAIT_INFO)
            val state = XrFrameState.calloc(stack).type(XR_TYPE_FRAME_STATE)

            val waitResult = xrWaitFrame(xrSession, frameWaitInfo, state)
            if (waitResult != XR_SUCCESS) {
                logger.debug("xrWaitFrame failed: ${resultToString(waitResult)}")
                return
            }

            predictedDisplayTime = state.predictedDisplayTime()
            shouldRender = state.shouldRender()

            val frameBeginInfo = XrFrameBeginInfo.calloc(stack).type(XR_TYPE_FRAME_BEGIN_INFO)
            val beginResult = xrBeginFrame(xrSession, frameBeginInfo)

            // XR_FRAME_DISCARDED: begin called twice without an end; still valid to render.
            if (beginResult != XR_SUCCESS && beginResult != XR_FRAME_DISCARDED) {
                logger.debug("xrBeginFrame failed: ${resultToString(beginResult)}")
                return
            }

            frameStarted = true

            locateViews(stack)
            updateTrackedDevices(stack)
            processInput(stack)
        }
    }

    /**
     * Locates the per-eye views, refreshing the projection and head-to-eye caches. OpenXR may
     * change the FOV per frame, so these cannot be cached once as in OpenVR.
     */
    protected fun locateViews(stack: MemoryStack) {
        val xrSession = session ?: return
        val space = referenceSpace ?: return
        val viewBuffer = views ?: return

        val viewLocateInfo = XrViewLocateInfo.calloc(stack)
            .type(XR_TYPE_VIEW_LOCATE_INFO)
            .viewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)
            .displayTime(predictedDisplayTime)
            .space(space)

        val viewState = XrViewState.calloc(stack).type(XR_TYPE_VIEW_STATE)
        val viewCountOutput = stack.callocInt(1)

        // Located in the reference space, as the composition layer submitted in endFrame needs
        // the eye poses in the same space it is submitted against.
        val result = xrLocateViews(xrSession, viewLocateInfo, viewState, viewCountOutput, viewBuffer)
        if (result != XR_SUCCESS) {
            return
        }

        if (viewState.viewStateFlags() and XR_VIEW_STATE_ORIENTATION_VALID_BIT.toLong() == 0L) {
            return
        }

        // Refreshed every frame, as OpenXR may change the FOV per frame.
        for (eye in 0 until minOf(2, viewCountOutput[0])) {
            eyeProjectionCache[eye] = viewBuffer[eye].fov().toProjectionMatrix(cachedNearPlane, cachedFarPlane)
        }

        // scenery expects a head-relative eye transform, matching OpenVR's eye-to-head matrix,
        // whereas the poses above are in the reference space. Locating the views a second time
        // against the VIEW space yields exactly that head-relative transform.
        val headSpace = viewSpace ?: return
        val headRelative = XrView.calloc(viewBuffer.remaining(), stack)
        headRelative.forEach { it.type(XR_TYPE_VIEW) }

        viewLocateInfo.space(headSpace)

        if (xrLocateViews(xrSession, viewLocateInfo, viewState, viewCountOutput, headRelative) != XR_SUCCESS) {
            return
        }

        for (eye in 0 until minOf(2, viewCountOutput[0])) {
            eyeTransformCache[eye] = headRelative[eye].pose().toMatrix4f()
        }
    }

    /** Locates HMD and controllers, firing connect events for new devices. */
    protected fun updateTrackedDevices(stack: MemoryStack) {
        val space = referenceSpace ?: return

        // HMD, via the VIEW space.
        viewSpace?.let { hmdSpace ->
            val location = XrSpaceLocation.calloc(stack).type(XR_TYPE_SPACE_LOCATION)
            val velocity = XrSpaceVelocity.calloc(stack).type(XR_TYPE_SPACE_VELOCITY)
            location.next(velocity.address())

            if (xrLocateSpace(hmdSpace, space, predictedDisplayTime, location) == XR_SUCCESS &&
                location.locationFlags() and XR_SPACE_LOCATION_ORIENTATION_VALID_BIT.toLong() != 0L
            ) {
                val device = trackedDevices.computeIfAbsent("HMD-0") {
                    val td = TrackedDevice(TrackedDeviceType.HMD, "HMD-0", Matrix4f().identity(), System.nanoTime())
                    fireDeviceConnect(td)
                    td
                }

                // scenery expects the inverse (view) matrix, matching OpenXRHMD.
                device.pose = location.pose().toMatrix4f().invert()
                device.timestamp = System.nanoTime()

                if (velocity.velocityFlags() and XR_SPACE_VELOCITY_LINEAR_VALID_BIT.toLong() != 0L) {
                    device.velocity = velocity.linearVelocity().toVector3f()
                    device.angularVelocity = velocity.angularVelocity().toVector3f()
                }
            }
        }

        // Controllers, via their action spaces.
        listOf(TrackerRole.LeftHand, TrackerRole.RightHand).forEach { role ->
            val handSpace = poseSpaces[role] ?: return@forEach
            val name = "Controller-${if (role == TrackerRole.LeftHand) "left" else "right"}"

            val location = XrSpaceLocation.calloc(stack).type(XR_TYPE_SPACE_LOCATION)
            val velocity = XrSpaceVelocity.calloc(stack).type(XR_TYPE_SPACE_VELOCITY)
            location.next(velocity.address())

            if (xrLocateSpace(handSpace, space, predictedDisplayTime, location) != XR_SUCCESS) {
                return@forEach
            }

            if (location.locationFlags() and XR_SPACE_LOCATION_ORIENTATION_VALID_BIT.toLong() == 0L) {
                return@forEach
            }

            val device = trackedDevices.computeIfAbsent(name) {
                val td = TrackedDevice(TrackedDeviceType.Controller, name, Matrix4f().identity(), System.nanoTime())
                td.role = role
                fireDeviceConnect(td)
                td
            }

            device.role = role
            device.pose = location.pose().toMatrix4f()
            device.timestamp = System.nanoTime()

            if (velocity.velocityFlags() and XR_SPACE_VELOCITY_LINEAR_VALID_BIT.toLong() != 0L) {
                device.velocity = velocity.linearVelocity().toVector3f()
                device.angularVelocity = velocity.angularVelocity().toVector3f()
            }
        }
    }

    /** Loads a model for [device] asynchronously and notifies the connect handlers. */
    protected fun fireDeviceConnect(device: TrackedDevice) {
        val timestamp = device.timestamp

        GlobalScope.launch {
            try {
                val mesh = Mesh()
                mesh.name = device.name
                loadModelForMesh(device, mesh)
                device.model = mesh
            } catch (e: Exception) {
                logger.warn("Could not load model for ${device.name}, device will not be visible in the scene. ($e)")
                device.model = null
            }

            events.onDeviceConnect.forEach { it.invoke(this@OpenXRHMD, device, timestamp) }
        }
    }

    /**
     * Syncs actions and translates state changes into the key events scenery's behaviour system
     * consumes, as [OpenVRHMD] does.
     */
    protected fun processInput(stack: MemoryStack) {
        val xrSession = session ?: return
        val set = actionSet ?: return

        val activeActionSet = XrActiveActionSet.calloc(1, stack)
        activeActionSet[0].actionSet(set).subactionPath(XR_NULL_PATH)

        val syncInfo = XrActionsSyncInfo.calloc(stack)
            .type(XR_TYPE_ACTIONS_SYNC_INFO)
            .activeActionSets(activeActionSet)

        // Actions are only active while focused, so failure here is expected in runtime UI.
        if (xrSyncActions(xrSession, syncInfo) != XR_SUCCESS) {
            return
        }

        listOf(TrackerRole.LeftHand, TrackerRole.RightHand).forEach { role ->
            val subactionPath = handPaths[role] ?: return@forEach

            buttonActions.forEach buttons@{ (button, action) ->
                val getInfo = XrActionStateGetInfo.calloc(stack)
                    .type(XR_TYPE_ACTION_STATE_GET_INFO)
                    .action(action)
                    .subactionPath(subactionPath)

                val state = XrActionStateBoolean.calloc(stack).type(XR_TYPE_ACTION_STATE_BOOLEAN)

                if (xrGetActionStateBoolean(xrSession, getInfo, state) != XR_SUCCESS || !state.isActive()) {
                    return@buttons
                }

                if (state.changedSinceLastSync()) {
                    if (state.currentState()) {
                        pressButton(button, role)
                    } else {
                        releaseButton(button, role)
                    }
                }
            }

            processThumbstick(stack, xrSession, role, subactionPath)
        }

        // Repeat held keys that allow repeats.
        keysDown.forEach {
            if (it in allowRepeats) {
                inputHandler.keyPressed(it.first.toKeyEvent(it.second).first)
            }
        }

        if (keysDown.isNotEmpty()) {
            // Simulated mouse move to trigger drag behaviour updates.
            inputHandler.mouseMoved(
                MouseEvent(
                    object : Component() {}, MouseEvent.MOUSE_CLICKED, System.nanoTime(),
                    0, 0, 0, 0, 0, 1, false, 0
                )
            )
        }
    }

    /**
     * Turns thumbstick deflection into edge-triggered directional events, so behaviours bound to
     * the D-pad keep working as under OpenVR.
     */
    protected fun processThumbstick(stack: MemoryStack, xrSession: XrSession, role: TrackerRole, subactionPath: Long) {
        val action = thumbstickAction ?: return

        val getInfo = XrActionStateGetInfo.calloc(stack)
            .type(XR_TYPE_ACTION_STATE_GET_INFO)
            .action(action)
            .subactionPath(subactionPath)

        val state = XrActionStateVector2f.calloc(stack).type(XR_TYPE_ACTION_STATE_VECTOR2F)

        if (xrGetActionStateVector2f(xrSession, getInfo, state) != XR_SUCCESS || !state.isActive()) {
            return
        }

        val x = state.currentState().x()
        val y = state.currentState().y()

        val direction = when {
            x > 0.5f && abs(y) < 0.5f -> OpenXRButton.Right
            x < -0.5f && abs(y) < 0.5f -> OpenXRButton.Left
            y > 0.5f && abs(x) < 0.5f -> OpenXRButton.Up
            y < -0.5f && abs(x) < 0.5f -> OpenXRButton.Down
            else -> null
        }

        val previous = lastThumbstickDirection[role]

        if (direction != previous) {
            previous?.let { releaseButton(it, role) }
            direction?.let { pressButton(it, role) }
            lastThumbstickDirection[role] = direction
        }
    }

    /** Dispatches a key press for [button] on [role]. */
    protected fun pressButton(button: OpenXRButton, role: TrackerRole) {
        val keyEvent = button.toKeyEvent(role)
        logger.debug("Button pressed: {} on {}", button, role)

        GlobalKeyEventDispatcher.getInstance().dispatchKeyEvent(keyEvent.first)
        inputHandler.keyPressed(keyEvent.first)
        keysDown.add(button to role)
        GlobalKeyEventDispatcher.getInstance().dispatchKeyEvent(keyEvent.second)
    }

    /** Dispatches a key release for [button] on [role]. */
    protected fun releaseButton(button: OpenXRButton, role: TrackerRole) {
        val keyEvent = button.toKeyEvent(role)
        logger.debug("Button released: {} on {}", button, role)

        GlobalKeyEventDispatcher.getInstance().dispatchKeyEvent(keyEvent.second)
        inputHandler.keyReleased(keyEvent.second)
        keysDown.remove(button to role)
    }

    /**
     * Submits the rendered image to the compositor. The renderer's double-wide image is blitted
     * into the swapchain image, presented as one projection layer with a subimage per eye.
     */
    override fun submitToCompositorVulkan(
        width: Int, height: Int, format: Int,
        instance: VkInstance, device: VulkanDevice,
        queueWithMutex: VulkanDevice.QueueWithMutex, image: Long
    ) {
        if (!initialized || disableSubmission) {
            return
        }

        // The session needs the Vulkan handles, which are only known here.
        if (!sessionInitialized) {
            initializeSession(instance, device, device.queueIndices.graphicsQueue.first, 0)

            if (!sessionInitialized) {
                return
            }

            // Drive the session to READY before the first frame.
            pollEvents()
        }

        if (!sessionRunning) {
            pollEvents()
            return
        }

        if (swapchain == null) {
            createSwapchain(format)
        }

        val xrSession = session ?: return
        val xrSwapchain = swapchain ?: return
        val images = swapchainImages ?: return

        // Nothing to do if update() has not started a frame.
        if (!frameStarted) {
            return
        }

        try {
            stackPush().use { stack ->
                if (shouldRender) {
                    val indexBuffer = stack.callocInt(1)
                    val acquireInfo = XrSwapchainImageAcquireInfo.calloc(stack)
                        .type(XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO)

                    checkResult(
                        xrAcquireSwapchainImage(xrSwapchain, acquireInfo, indexBuffer),
                        "Acquiring swapchain image"
                    )

                    val waitInfo = XrSwapchainImageWaitInfo.calloc(stack)
                        .type(XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO)
                        .timeout(XR_INFINITE_DURATION)

                    if (xrWaitSwapchainImage(xrSwapchain, waitInfo) == XR_SUCCESS) {
                        blitToSwapchainImage(
                            device, queueWithMutex, image, width, height,
                            images[indexBuffer[0]].image()
                        )
                    }

                    val releaseInfo = XrSwapchainImageReleaseInfo.calloc(stack)
                        .type(XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO)

                    checkResult(
                        xrReleaseSwapchainImage(xrSwapchain, releaseInfo),
                        "Releasing swapchain image"
                    )
                }

                endFrame(stack, xrSession)
            }
        } catch (e: Exception) {
            logger.error("OpenXR submission failed: $e. Disabling submission.")
            disableSubmission = true
        } finally {
            frameStarted = false
        }
    }

    /**
     * Ends the frame. If the runtime asked us not to render, an empty frame is submitted instead,
     * which is required to keep the frame loop running.
     */
    protected fun endFrame(stack: MemoryStack, xrSession: XrSession) {
        val viewBuffer = views ?: return
        val space = referenceSpace ?: return
        val xrSwapchain = swapchain

        val frameEndInfo = XrFrameEndInfo.calloc(stack)
            .type(XR_TYPE_FRAME_END_INFO)
            .displayTime(predictedDisplayTime)
            .environmentBlendMode(XR_ENVIRONMENT_BLEND_MODE_OPAQUE)

        if (shouldRender && xrSwapchain != null) {
            val projectionViews = XrCompositionLayerProjectionView.calloc(2, stack)

            for (eye in 0 until 2) {
                val view = viewBuffer[eye]

                projectionViews[eye]
                    .type(XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW)
                    .pose(view.pose())
                    .fov(view.fov())

                // Each eye reads its half of the double-wide image.
                projectionViews[eye].subImage()
                    .swapchain(xrSwapchain)
                    .imageArrayIndex(0)
                    .imageRect { rect ->
                        rect.offset { it.x(eye * recommendedSize.x()).y(0) }
                        rect.extent { it.width(recommendedSize.x()).height(recommendedSize.y()) }
                    }
            }

            val layer = XrCompositionLayerProjection.calloc(stack)
                .type(XR_TYPE_COMPOSITION_LAYER_PROJECTION)
                .layerFlags(0)
                .space(space)
                .views(projectionViews)

            frameEndInfo.layers(stack.pointers(layer.address()))
        } else {
            frameEndInfo.layers(null)
        }

        val result = xrEndFrame(xrSession, frameEndInfo)
        if (result != XR_SUCCESS) {
            logger.debug("xrEndFrame failed: ${resultToString(result)}")
        }
    }

    /** Blits [sourceImage] into [targetImage], with the required layout transitions. */
    protected fun blitToSwapchainImage(
        device: VulkanDevice, queue: VulkanDevice.QueueWithMutex,
        sourceImage: Long, sourceWidth: Int, sourceHeight: Int, targetImage: Long
    ) {
        if (commandPool == -1L) {
            commandPool = device.createCommandPool(device.queueIndices.graphicsQueue.first)
        }

        stackPush().use { stack ->
            val subresourceRange = VkImageSubresourceRange.calloc(stack)
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1)

            with(VU.newCommandBuffer(device, commandPool, autostart = true)) {
                VulkanTexture.transitionLayout(
                    sourceImage,
                    KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    subresourceRange = subresourceRange,
                    commandBuffer = this,
                    srcStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                    dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT
                )

                // Contents are undefined after acquisition, so the old layout is irrelevant.
                VulkanTexture.transitionLayout(
                    targetImage,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    subresourceRange = subresourceRange,
                    commandBuffer = this,
                    srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT
                )

                val targetWidth = recommendedSize.x() * 2
                val targetHeight = recommendedSize.y()

                // A blit, as the renderer's resolution may differ from the recommended size.
                val region = VkImageBlit.calloc(1, stack)
                region.srcSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1)
                region.srcOffsets(0).set(0, 0, 0)
                region.srcOffsets(1).set(sourceWidth, sourceHeight, 1)

                region.dstSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1)
                region.dstOffsets(0).set(0, 0, 0)
                region.dstOffsets(1).set(targetWidth, targetHeight, 1)

                vkCmdBlitImage(
                    this,
                    sourceImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    targetImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    region, VK_FILTER_LINEAR
                )

                // OpenXR expects COLOR_ATTACHMENT_OPTIMAL on release.
                VulkanTexture.transitionLayout(
                    targetImage,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    subresourceRange = subresourceRange,
                    commandBuffer = this,
                    srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                    dstStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                )

                VulkanTexture.transitionLayout(
                    sourceImage,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    subresourceRange = subresourceRange,
                    commandBuffer = this,
                    srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                    dstStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                )

                endCommandBuffer(device, commandPool, queue, flush = true, dealloc = true)
            }
        }
    }

    /** Not supported; use [submitToCompositorVulkan] with the Vulkan renderer. */
    override fun submitToCompositor(textureId: Int) {
        logger.error("OpenGL compositor submission is not supported by OpenXRHMD, please use the Vulkan renderer.")
    }

    /**
     * Returns a [List] of Vulkan instance extensions required by the OpenXR runtime.
     */
    override fun getVulkanInstanceExtensions(): List<String> {
        val xrInstance = instance ?: return listOf()

        stackPush().use { stack ->
            val count = stack.callocInt(1)
            if (xrGetVulkanInstanceExtensionsKHR(xrInstance, systemId, count, null) != XR_SUCCESS || count[0] == 0) {
                return listOf()
            }

            val buffer = stack.calloc(count[0])
            if (xrGetVulkanInstanceExtensionsKHR(xrInstance, systemId, count, buffer) != XR_SUCCESS) {
                return listOf()
            }

            val extensions = memUTF8(buffer, count[0] - 1).split(" ").filter { it.isNotBlank() }
            logger.debug("Vulkan required instance extensions are: ${extensions.joinToString(", ")}")
            return extensions
        }
    }

    /**
     * Returns a [List] of Vulkan device extensions required by the OpenXR runtime.
     */
    override fun getVulkanDeviceExtensions(physicalDevice: VkPhysicalDevice): List<String> {
        val xrInstance = instance ?: return listOf(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME)

        stackPush().use { stack ->
            val count = stack.callocInt(1)

            val extensions = if (xrGetVulkanDeviceExtensionsKHR(xrInstance, systemId, count, null) != XR_SUCCESS || count[0] == 0) {
                listOf()
            } else {
                val buffer = stack.calloc(count[0])
                if (xrGetVulkanDeviceExtensionsKHR(xrInstance, systemId, count, buffer) != XR_SUCCESS) {
                    listOf()
                } else {
                    memUTF8(buffer, count[0] - 1).split(" ").filter { it.isNotBlank() }
                }
            }

            logger.debug("Vulkan required device extensions are: ${extensions.joinToString(", ")}")
            // The renderer still needs a swapchain for its mirror window.
            return (extensions + KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME).distinct()
        }
    }

    /** Returns the physical device the runtime wants to render on, or null on failure. */
    fun getVulkanPhysicalDevice(vkInstance: VkInstance): VkPhysicalDevice? {
        val xrInstance = instance ?: return null

        stackPush().use { stack ->
            val physicalDevice = stack.callocPointer(1)
            if (xrGetVulkanGraphicsDeviceKHR(xrInstance, systemId, vkInstance, physicalDevice) != XR_SUCCESS) {
                return null
            }

            return VkPhysicalDevice(physicalDevice[0], vkInstance)
        }
    }

    /**
     * Returns the optimal per-eye render target size for the HMD as 2D vector.
     */
    override fun getRenderTargetSize(): Vector2i = Vector2i(recommendedSize)

    /**
     * Returns the per-eye projection matrix.
     *
     * @param[eye] The index of the eye
     * @return Matrix4f containing the per-eye projection matrix
     */
    @Synchronized
    override fun getEyeProjection(eye: Int, nearPlane: Float, farPlane: Float): Matrix4f {
        // Near/far are only known here, so a change invalidates the cache.
        if (nearPlane != cachedNearPlane || farPlane != cachedFarPlane) {
            cachedNearPlane = nearPlane
            cachedFarPlane = farPlane

            views?.let { viewBuffer ->
                for (i in 0 until minOf(2, viewBuffer.remaining())) {
                    eyeProjectionCache[i] = viewBuffer[i].fov().toProjectionMatrix(nearPlane, farPlane)
                }
            }
        }

        return eyeProjectionCache[eye] ?: Matrix4f().perspective(
            Math.toRadians(70.0).toFloat(),
            recommendedSize.x().toFloat() / recommendedSize.y().toFloat(),
            nearPlane, farPlane
        )
    }

    /**
     * Returns the per-eye transform that moves from head to eye.
     *
     * @param[eye] The eye index
     * @return Matrix4f containing the transform
     */
    @Synchronized
    override fun getHeadToEyeTransform(eye: Int): Matrix4f {
        val current = eyeTransformCache[eye] ?: return Matrix4f().identity()
        return Matrix4f(current)
    }

    /** Returns the IPD, derived from the distance between the two located eye poses. */
    override fun getIPD(): Float {
        val viewBuffer = views ?: return 0.065f

        if (viewBuffer.remaining() < 2) {
            return 0.065f
        }

        val left = viewBuffer[0].pose().`position$`()
        val right = viewBuffer[1].pose().`position$`()

        val ipd = Vector3f(left.x(), left.y(), left.z())
            .sub(Vector3f(right.x(), right.y(), right.z()))
            .length()

        return if (ipd > 0.0f) ipd else 0.065f
    }

    /**
     * Returns the orientation of the HMD.
     */
    override fun getOrientation(): Quaternionf = Quaternionf().setFromUnnormalized(getPose())

    /**
     * Returns the orientation of the given device, or a unit quaternion if the device is not found.
     */
    override fun getOrientation(id: String): Quaternionf {
        val device = trackedDevices[id] ?: return Quaternionf()
        return Quaternionf().setFromUnnormalized(device.pose)
    }

    /**
     * Returns the absolute position as Vector3f.
     */
    override fun getPosition(): Vector3f {
        val m = getPose()
        val d = Vector3f(-1.0f * m.get(3, 0), -1.0f * m.get(3, 1), -1.0f * m.get(3, 2))
        // The position is already rotated by the orientation, so it is un-rotated here to get
        // the absolute position.
        return d.rotate(getOrientation().conjugate())
    }

    /**
     * Returns the HMD pose.
     */
    override fun getPose(): Matrix4f {
        return trackedDevices.values.firstOrNull { it.type == TrackedDeviceType.HMD }?.pose ?: Matrix4f().identity()
    }

    /**
     * Returns the poses of all devices of the given [type].
     */
    override fun getPose(type: TrackedDeviceType): List<TrackedDevice> {
        return trackedDevices.values.filter { it.type == type }
    }

    /**
     * Returns the HMD pose for a given eye.
     */
    override fun getPoseForEye(eye: Int): Matrix4f {
        val p = this.getPose()
        val e = this.getHeadToEyeTransform(eye).invert()

        e.mul(p)

        return e
    }

    /**
     * Returns all tracked devices of a given type.
     */
    override fun getTrackedDevices(ofType: TrackedDeviceType): Map<String, TrackedDevice> {
        return trackedDevices.filter { it.value.type == ofType }
    }

    /**
     * Triggers a haptic pulse on [controller] for [duration] milliseconds.
     */
    fun vibrate(controller: TrackedDevice, duration: Int = 1000) {
        val xrSession = session ?: return
        val action = hapticActions[controller.role] ?: return
        val subactionPath = handPaths[controller.role] ?: return

        stackPush().use { stack ->
            val vibration = XrHapticVibration.calloc(stack)
                .type(XR_TYPE_HAPTIC_VIBRATION)
                .duration(duration * 1000L * 1000L)
                .frequency(XR_FREQUENCY_UNSPECIFIED)
                .amplitude(1.0f)

            val actionInfo = XrHapticActionInfo.calloc(stack)
                .type(XR_TYPE_HAPTIC_ACTION_INFO)
                .action(action)
                .subactionPath(subactionPath)

            xrApplyHapticFeedback(xrSession, actionInfo, XrHapticBaseHeader.create(vibration.address()))
        }
    }

    /**
     * Query whether a compositor is used or the renderer should take care of displaying on the
     * HMD on its own.
     */
    override fun hasCompositor(): Boolean = useCompositor && initialized

    /**
     * Check whether the HMD is initialized and working.
     */
    override fun initializedAndWorking(): Boolean = initialized

    /**
     * Returns a [TrackerInput] instance, if working currently.
     */
    override fun getWorkingTracker(): TrackerInput? = if (initialized) this else null

    /**
     * Returns a [Display] instance, if working currently.
     */
    override fun getWorkingDisplay(): Display? = if (initialized) this else null

    /**
     * No-op: OpenXR has no compositor fade. Fading has to be done in the scene itself, e.g. with
     * a quad in front of the camera.
     */
    override fun fadeToColor(color: Vector4f, seconds: Float) {
        logger.debug("fadeToColor is not supported by OpenXR, ignoring.")
    }

    /** Fades the view on the HMD to black. Not supported by OpenXR, see [fadeToColor]. */
    fun fadeToBlack(seconds: Float = 0.1f) {
        fadeToColor(Vector4f(0f, 0f, 0f, 1f), seconds)
    }

    /** Removes any previously overlayed color. Not supported by OpenXR, see [fadeToColor]. */
    fun fateToClear(seconds: Float = 0.1f) {
        fadeToColor(Vector4f(0f), seconds)
    }

    /**
     * Loads a model representing the [TrackedDevice], preferring the runtime's own render model
     * via `XR_EXT_render_model` and falling back to placeholder geometry.
     */
    override fun loadModelForMesh(device: TrackedDevice, mesh: Mesh): Mesh {
        if (device.type == TrackedDeviceType.Controller && renderModelsSupported) {
            try {
                if (loadRenderModel(device, mesh)) {
                    mesh.metadata["renderModelLoaded"] = true
                    return mesh
                }
            } catch (e: Exception) {
                logger.warn("Could not load render model for ${device.name}, using a placeholder. ($e)")
            }
        }

        return loadModelForMesh(device.type, mesh)
    }

    /**
     * Builds a placeholder model for [type], used when the runtime provides no render model.
     * The child node named `collider` marks the controller tip, which the VR behaviours use as
     * their interaction point.
     */
    override fun loadModelForMesh(type: TrackedDeviceType, mesh: Mesh): Mesh {
        if (type != TrackedDeviceType.Controller) {
            logger.debug("No model available for $type.")
            return mesh
        }

        // The body is offset backwards so the collider sits at the controller's tip.
        val body = Box(Vector3f(0.03f, 0.03f, 0.12f))
        body.name = "body"
        body.spatial().position = Vector3f(0.0f, 0.0f, 0.06f)
        body.ifMaterial { diffuse = Vector3f(0.1f, 0.1f, 0.1f) }

        val collider = Box(Vector3f(0.02f, 0.02f, 0.02f))
        collider.name = "collider"
        collider.ifMaterial { diffuse = Vector3f(0.1f, 0.1f, 0.1f) }

        // The collider is added first, as several behaviours fall back to children.first().
        mesh.addChild(collider)
        mesh.addChild(body)

        return mesh
    }

    /**
     * Loads the runtime's render model for [device] into [mesh], via `XR_EXT_render_model`.
     *
     * The runtime enumerates the models bound to the current interaction profile and hands out a
     * glTF asset per model, which [GLBReader] turns into scenery geometry. Returns false if no
     * model could be obtained, so the caller can fall back to placeholder geometry.
     */
    protected fun loadRenderModel(device: TrackedDevice, mesh: Mesh): Boolean {
        val xrSession = session ?: run {
            logger.debug("No session yet, cannot load a render model for ${device.name}.")
            return false
        }
        val topLevelPath = handPaths[device.role] ?: run {
            logger.debug("No hand path for role ${device.role}, cannot load a render model.")
            return false
        }

        stackPush().use { stack ->
            val enumerateInfo = XrInteractionRenderModelIdsEnumerateInfoEXT.calloc(stack)
                .type(XR_TYPE_INTERACTION_RENDER_MODEL_IDS_ENUMERATE_INFO_EXT)

            val idCount = stack.callocInt(1)
            val enumerateResult = xrEnumerateInteractionRenderModelIdsEXT(xrSession, enumerateInfo, idCount, null)

            if (enumerateResult != XR_SUCCESS) {
                logger.debug("Enumerating render models failed: {}", resultToString(enumerateResult))
                return false
            }

            if (idCount[0] == 0) {
                // Models only appear once the runtime has bound an interaction profile, which
                // happens after the controllers have been seen for the first time.
                logger.debug("Runtime reported no render models for {} yet.", device.role)
                return false
            }

            logger.debug("Runtime reported {} render model(s) for {}", idCount[0], device.role)

            val ids = stack.callocLong(idCount[0])
            if (xrEnumerateInteractionRenderModelIdsEXT(xrSession, enumerateInfo, idCount, ids) != XR_SUCCESS) {
                return false
            }

            // The enumeration covers both hands, so each model is matched against the hand it
            // belongs to via its top-level user path.
            for (i in 0 until idCount[0]) {
                val renderModelId = ids[i]
                if (renderModelId == XR_NULL_RENDER_MODEL_ID_EXT.toLong()) {
                    continue
                }

                val createInfo = XrRenderModelCreateInfoEXT.calloc(stack)
                    .type(XR_TYPE_RENDER_MODEL_CREATE_INFO_EXT)
                    .renderModelId(renderModelId)
                    .gltfExtensions(null)

                val modelPointer = stack.callocPointer(1)
                val createResult = xrCreateRenderModelEXT(xrSession, createInfo, modelPointer)
                if (createResult != XR_SUCCESS) {
                    logger.debug("xrCreateRenderModelEXT failed for id {}: {}", renderModelId, resultToString(createResult))
                    continue
                }

                val renderModel = XrRenderModelEXT(modelPointer[0], xrSession)

                try {
                    if (!modelBelongsToHand(stack, renderModel, topLevelPath)) {
                        logger.debug("Render model {} does not belong to {}", renderModelId, device.role)
                        continue
                    }

                    val properties = XrRenderModelPropertiesEXT.calloc(stack)
                        .type(XR_TYPE_RENDER_MODEL_PROPERTIES_EXT)

                    val propertiesResult = xrGetRenderModelPropertiesEXT(renderModel, null, properties)
                    if (propertiesResult != XR_SUCCESS) {
                        logger.debug("xrGetRenderModelPropertiesEXT failed: {}", resultToString(propertiesResult))
                        continue
                    }

                    val cacheId = properties.cacheId().uuidString()

                    // Both controllers usually share a model, so parse each asset only once.
                    renderModelCache[cacheId]?.let { cached ->
                        logger.debug("Reusing cached render model $cacheId for ${device.role}")
                        mesh.addChild(cached.duplicateGeometry())
                        addCollider(mesh)
                        return true
                    }

                    val gltf = loadRenderModelAsset(stack, xrSession, properties.cacheId()) ?: continue
                    val model = try {
                        GLBReader.parseGLB(gltf)
                    } finally {
                        memFree(gltf)
                    } ?: continue

                    model.name = "rendermodel"
                    renderModelCache[cacheId] = model

                    mesh.addChild(model.duplicateGeometry())
                    addCollider(mesh)

                    logger.info("Loaded render model $cacheId for ${device.role}")
                    return true
                } finally {
                    xrDestroyRenderModelEXT(renderModel)
                }
            }
        }

        return false
    }

    /**
     * Returns whether [renderModel] belongs to the hand identified by [topLevelPath]. Runtimes
     * that report no path are treated as a match, so single-model runtimes still return geometry.
     */
    protected fun modelBelongsToHand(stack: MemoryStack, renderModel: XrRenderModelEXT, topLevelPath: Long): Boolean {
        val info = XrInteractionRenderModelTopLevelUserPathGetInfoEXT.calloc(stack)
            .type(XR_TYPE_INTERACTION_RENDER_MODEL_TOP_LEVEL_USER_PATH_GET_INFO_EXT)

        val path = stack.callocLong(1)
        if (xrGetRenderModelPoseTopLevelUserPathEXT(renderModel, info, path) != XR_SUCCESS) {
            return true
        }

        return path[0] == XR_NULL_PATH || path[0] == topLevelPath
    }

    /**
     * Fetches the glTF asset bytes for the model identified by [cacheId], or null on failure.
     */
    protected fun loadRenderModelAsset(stack: MemoryStack, xrSession: XrSession, cacheId: XrUuidEXT): ByteBuffer? {
        val assetCreateInfo = XrRenderModelAssetCreateInfoEXT.calloc(stack)
            .type(XR_TYPE_RENDER_MODEL_ASSET_CREATE_INFO_EXT)
            .cacheId(cacheId)

        val assetPointer = stack.callocPointer(1)
        if (xrCreateRenderModelAssetEXT(xrSession, assetCreateInfo, assetPointer) != XR_SUCCESS) {
            return null
        }

        val asset = XrRenderModelAssetEXT(assetPointer[0], xrSession)

        try {
            val assetData = XrRenderModelAssetDataEXT.calloc(stack)
                .type(XR_TYPE_RENDER_MODEL_ASSET_DATA_EXT)

            if (xrGetRenderModelAssetDataEXT(asset, null, assetData) != XR_SUCCESS
                || assetData.bufferCountOutput() == 0) {
                return null
            }

            // The buffer outlives the stack frame, as parsing happens after this returns.
            val size = assetData.bufferCountOutput()
            val buffer = memAlloc(size)

            assetData.bufferCapacityInput(size)
            assetData.buffer(buffer)

            if (xrGetRenderModelAssetDataEXT(asset, null, assetData) != XR_SUCCESS) {
                memFree(buffer)
                return null
            }

            return buffer
        } finally {
            xrDestroyRenderModelAssetEXT(asset)
        }
    }

    /** Adds the `collider` child the VR behaviours use as their interaction point. */
    protected fun addCollider(mesh: Mesh) {
        if (mesh.children.any { it.name == "collider" }) {
            return
        }

        val collider = Box(Vector3f(0.02f, 0.02f, 0.02f))
        collider.name = "collider"
        collider.visible = false
        mesh.addChild(collider)
    }

    /**
     * Attaches a given [TrackedDevice] to a scene graph [Node], camera-relative in case [camera]
     * is non-null.
     */
    override fun attachToNode(device: TrackedDevice, node: Node, camera: Camera?) {
        if (device.type != TrackedDeviceType.Controller) {
            logger.warn("No idea how to attach device type ${device.type} to a node, sorry.")
            return
        }

        logger.info("Adding child $node to $camera")
        camera?.getScene()?.addChild(node)

        node.update.add {
            this.getPose(TrackedDeviceType.Controller).firstOrNull { it.name == device.name }?.let { controller ->
                node.metadata["TrackedDevice"] = controller
                node.ifSpatial {
                    wantsComposeModel = false
                    model.identity()
                    camera?.let {
                        model.translate(it.spatial().position)
                    }
                    model.mul(controller.pose)

                    needsUpdate = false
                    needsUpdateWorld = true
                }
            }
        }
    }

    /**
     * Adds a behaviour to the map of behaviours, making them available for key bindings.
     */
    fun addBehaviour(behaviourName: String, behaviour: Behaviour) {
        behaviourMap.put(behaviourName, behaviour)
    }

    /**
     * Removes a behaviour from the map of behaviours.
     */
    fun removeBehaviour(behaviourName: String) {
        behaviourMap.remove(behaviourName)
    }

    /**
     * Adds a key binding for a given behaviour.
     */
    fun addKeyBinding(behaviourName: String, vararg keys: String) {
        keys.forEach { key ->
            config.inputTriggerAdder(inputMap, "all").put(behaviourName, key)
        }
    }

    /**
     * Adds a key binding for [behaviourName] on [button] of [hand].
     */
    fun addKeyBinding(behaviourName: String, hand: TrackerRole, button: OpenXRButton) {
        config.inputTriggerAdder(inputMap, "all").put(behaviourName, keyBinding(hand, button))
    }

    /**
     * Removes a key binding for a given behaviour.
     */
    @Suppress("unused")
    fun removeKeyBinding(behaviourName: String) {
        config.inputTriggerAdder(inputMap, "all").put(behaviourName)
    }

    /**
     * Returns the behaviour with the given name, if it exists. Otherwise null is returned.
     */
    fun getBehaviour(behaviourName: String): Behaviour? = behaviourMap.get(behaviourName)

    /** Destroys all handles in reverse order of creation. */
    @Synchronized
    fun close() {
        initialized = false
        sessionRunning = false

        session?.let { s ->
            if (sessionState != XR_SESSION_STATE_IDLE && sessionState != XR_SESSION_STATE_UNKNOWN) {
                xrRequestExitSession(s)
            }
        }

        swapchain?.let { xrDestroySwapchain(it) }
        swapchain = null

        poseSpaces.values.forEach { xrDestroySpace(it) }
        poseSpaces.clear()

        viewSpace?.let { xrDestroySpace(it) }
        viewSpace = null

        referenceSpace?.let { xrDestroySpace(it) }
        referenceSpace = null

        // Actions are shared between hands, so destroy each handle once.
        buttonActions.values.distinct().forEach { xrDestroyAction(it) }
        buttonActions.clear()
        thumbstickAction?.let { xrDestroyAction(it) }
        thumbstickAction = null
        poseActions.values.distinct().forEach { xrDestroyAction(it) }
        poseActions.clear()
        hapticActions.values.distinct().forEach { xrDestroyAction(it) }
        hapticActions.clear()

        actionSet?.let { xrDestroyActionSet(it) }
        actionSet = null

        renderModelCache.clear()

        session?.let { xrDestroySession(it) }
        session = null
        sessionInitialized = false

        swapchainImages?.free()
        swapchainImages = null
        views?.free()
        views = null
        viewConfigurationViews?.free()
        viewConfigurationViews = null

        instance?.let { xrDestroyInstance(it) }
        instance = null
    }

    /** Throws an [IllegalStateException] if [result] indicates a failure. */
    protected fun checkResult(result: Int, operation: String) {
        if (result < 0) {
            throw IllegalStateException("$operation failed: ${resultToString(result)} ($result)")
        }
    }

    /** Converts a result code to a string, using the runtime's translation where available. */
    protected fun resultToString(result: Int): String {
        val xrInstance = instance ?: return resultName(result)

        stackPush().use { stack ->
            val buffer = stack.calloc(XR_MAX_RESULT_STRING_SIZE)
            return if (xrResultToString(xrInstance, result, buffer) == XR_SUCCESS) {
                memUTF8(buffer).trimEnd(' ')
            } else {
                resultName(result)
            }
        }
    }

    /** Formats an XrVersion or Vulkan version as major.minor.patch. */
    protected fun versionString(version: Long): String =
        "${(version shr 48) and 0xffff}.${(version shr 32) and 0xffff}.${version and 0xffffffffL}"

    /**
     * Names a result code without needing an instance, for errors that happen before or during
     * [xrCreateInstance] -- where [xrResultToString] is not yet available.
     */
    protected fun resultName(result: Int): String = when (result) {
        XR_SUCCESS -> "XR_SUCCESS"
        XR_ERROR_VALIDATION_FAILURE -> "XR_ERROR_VALIDATION_FAILURE"
        XR_ERROR_RUNTIME_FAILURE -> "XR_ERROR_RUNTIME_FAILURE"
        XR_ERROR_OUT_OF_MEMORY -> "XR_ERROR_OUT_OF_MEMORY"
        XR_ERROR_API_VERSION_UNSUPPORTED -> "XR_ERROR_API_VERSION_UNSUPPORTED"
        XR_ERROR_INITIALIZATION_FAILED -> "XR_ERROR_INITIALIZATION_FAILED"
        XR_ERROR_FUNCTION_UNSUPPORTED -> "XR_ERROR_FUNCTION_UNSUPPORTED"
        XR_ERROR_FEATURE_UNSUPPORTED -> "XR_ERROR_FEATURE_UNSUPPORTED"
        XR_ERROR_EXTENSION_NOT_PRESENT -> "XR_ERROR_EXTENSION_NOT_PRESENT"
        XR_ERROR_LIMIT_REACHED -> "XR_ERROR_LIMIT_REACHED"
        XR_ERROR_SIZE_INSUFFICIENT -> "XR_ERROR_SIZE_INSUFFICIENT"
        XR_ERROR_HANDLE_INVALID -> "XR_ERROR_HANDLE_INVALID"
        XR_ERROR_INSTANCE_LOST -> "XR_ERROR_INSTANCE_LOST"
        XR_ERROR_API_LAYER_NOT_PRESENT -> "XR_ERROR_API_LAYER_NOT_PRESENT"
        XR_ERROR_NAME_INVALID -> "XR_ERROR_NAME_INVALID"
        XR_ERROR_RUNTIME_UNAVAILABLE -> "XR_ERROR_RUNTIME_UNAVAILABLE"
        XR_ERROR_FORM_FACTOR_UNSUPPORTED -> "XR_ERROR_FORM_FACTOR_UNSUPPORTED"
        XR_ERROR_FORM_FACTOR_UNAVAILABLE -> "XR_ERROR_FORM_FACTOR_UNAVAILABLE"
        else -> "XrResult(" + result + ")"
    }

    /** Converts a string into an `XrPath`. */
    protected fun stringToPath(xrInstance: XrInstance, path: String): Long {
        stackPush().use { stack ->
            val pathBuffer = stack.callocLong(1)
            val result = xrStringToPath(xrInstance, stack.UTF8(path), pathBuffer)

            if (result != XR_SUCCESS) {
                logger.warn("Could not convert $path to an XrPath: ${resultToString(result)}")
                return XR_NULL_PATH
            }

            return pathBuffer[0]
        }
    }

    /** Converts an `XrPath` back into a string. */
    protected fun pathToString(xrInstance: XrInstance, path: Long): String {
        stackPush().use { stack ->
            val count = stack.callocInt(1)
            if (xrPathToString(xrInstance, path, count, null) != XR_SUCCESS || count[0] == 0) {
                return ""
            }

            val buffer = stack.calloc(count[0])
            if (xrPathToString(xrInstance, path, count, buffer) != XR_SUCCESS) {
                return ""
            }

            return memUTF8(buffer, count[0] - 1)
        }
    }

    /** Returns a human-readable name for a session state. */
    protected fun sessionStateToString(state: Int): String = when (state) {
        XR_SESSION_STATE_IDLE -> "IDLE"
        XR_SESSION_STATE_READY -> "READY"
        XR_SESSION_STATE_SYNCHRONIZED -> "SYNCHRONIZED"
        XR_SESSION_STATE_VISIBLE -> "VISIBLE"
        XR_SESSION_STATE_FOCUSED -> "FOCUSED"
        XR_SESSION_STATE_STOPPING -> "STOPPING"
        XR_SESSION_STATE_LOSS_PENDING -> "LOSS_PENDING"
        XR_SESSION_STATE_EXITING -> "EXITING"
        else -> "UNKNOWN($state)"
    }

    /** Converts an [XrPosef] to a [Matrix4f]. */
    protected fun XrPosef.toMatrix4f(): Matrix4f {
        val o = this.orientation()
        val p = this.`position$`()

        return Matrix4f().translationRotateScale(
            p.x(), p.y(), p.z(),
            o.x(), o.y(), o.z(), o.w(),
            1.0f, 1.0f, 1.0f
        )
    }

    /** Converts an [XrVector3f] to a [Vector3f]. */
    protected fun XrVector3f.toVector3f(): Vector3f = Vector3f(this.x(), this.y(), this.z())

    /**
     * Builds an asymmetric projection from an [XrFovf]. OpenXR gives the FOV as four angles from
     * the view axis, turned here into the tangents of an off-centre frustum.
     */
    protected fun XrFovf.toProjectionMatrix(nearPlane: Float, farPlane: Float): Matrix4f {
        val near = if (nearPlane > 0.0f) nearPlane else 0.05f
        val far = if (farPlane > near) farPlane else 1000.0f

        val left = tan(this.angleLeft()) * near
        val right = tan(this.angleRight()) * near
        val down = tan(this.angleDown()) * near
        val up = tan(this.angleUp()) * near

        return Matrix4f().frustum(left, right, down, up, near, far)
    }

    /** Renders the UUID as a hex string, used to key [renderModelCache]. */
    protected fun XrUuidEXT.uuidString(): String {
        val data = this.data()
        return (0 until data.remaining()).joinToString("") { "%02x".format(data.get(it)) }
    }


    companion object {
        private val logger by lazyLogger()

        /**
         * Maps hand/button combinations onto AWT keys, reusing scenery's existing binding
         * machinery. Mirrors [OpenVRHMD]'s mapping so bindings carry over.
         */
        protected val keyMap: HashMap<Pair<TrackerRole, OpenXRButton>, AWTKey> = hashMapOf(
            (TrackerRole.LeftHand to OpenXRButton.Left) to AWTKey(KeyEvent.VK_H),
            (TrackerRole.LeftHand to OpenXRButton.Right) to AWTKey(KeyEvent.VK_L),
            (TrackerRole.LeftHand to OpenXRButton.Up) to AWTKey(KeyEvent.VK_K),
            (TrackerRole.LeftHand to OpenXRButton.Down) to AWTKey(KeyEvent.VK_J),
            (TrackerRole.LeftHand to OpenXRButton.Menu) to AWTKey(KeyEvent.VK_M),
            (TrackerRole.LeftHand to OpenXRButton.Trigger) to AWTKey(KeyEvent.VK_T),
            (TrackerRole.LeftHand to OpenXRButton.Side) to AWTKey(KeyEvent.VK_X),
            (TrackerRole.LeftHand to OpenXRButton.A) to AWTKey(KeyEvent.VK_Q),
            (TrackerRole.LeftHand to OpenXRButton.B) to AWTKey(KeyEvent.VK_O),
            (TrackerRole.LeftHand to OpenXRButton.System) to AWTKey(KeyEvent.VK_E),
            (TrackerRole.LeftHand to OpenXRButton.Touchpad) to AWTKey(KeyEvent.VK_R),
            (TrackerRole.LeftHand to OpenXRButton.Thumbstick) to AWTKey(KeyEvent.VK_Z),

            (TrackerRole.RightHand to OpenXRButton.Left) to AWTKey(KeyEvent.VK_A),
            (TrackerRole.RightHand to OpenXRButton.Right) to AWTKey(KeyEvent.VK_D),
            (TrackerRole.RightHand to OpenXRButton.Up) to AWTKey(KeyEvent.VK_W),
            (TrackerRole.RightHand to OpenXRButton.Down) to AWTKey(KeyEvent.VK_S),
            (TrackerRole.RightHand to OpenXRButton.Menu) to AWTKey(KeyEvent.VK_N),
            (TrackerRole.RightHand to OpenXRButton.Trigger) to AWTKey(KeyEvent.VK_U),
            (TrackerRole.RightHand to OpenXRButton.Side) to AWTKey(KeyEvent.VK_Y),
            (TrackerRole.RightHand to OpenXRButton.A) to AWTKey(KeyEvent.VK_1),
            (TrackerRole.RightHand to OpenXRButton.B) to AWTKey(KeyEvent.VK_6),
            (TrackerRole.RightHand to OpenXRButton.System) to AWTKey(KeyEvent.VK_2),
            (TrackerRole.RightHand to OpenXRButton.Touchpad) to AWTKey(KeyEvent.VK_3),
            (TrackerRole.RightHand to OpenXRButton.Thumbstick) to AWTKey(KeyEvent.VK_4)
        )

        /**
         * Returns the key string mapped to [button] on [hand].
         */
        @JvmStatic
        fun keyBinding(hand: TrackerRole, button: OpenXRButton): String {
            val binding = keyMap[hand to button]
                ?: throw UnsupportedOperationException("Key $button on $hand not found.")

            logger.debug("Binding for $button on $hand is ${binding.string}")
            return binding.string
        }

        private fun OpenXRButton.toKeyEvent(role: TrackerRole): Pair<KeyEvent, KeyEvent> {
            val keycode = this.toAWTKeyCode(role)
            return KeyEvent(object : Component() {}, KeyEvent.KEY_PRESSED, System.nanoTime(), 0, keycode.code, keycode.char) to
                KeyEvent(object : Component() {}, KeyEvent.KEY_RELEASED, System.nanoTime() + 10e5.toLong(), 0, keycode.code, keycode.char)
        }

        private fun OpenXRButton.toAWTKeyCode(role: TrackerRole = TrackerRole.LeftHand): AWTKey {
            return keyMap[role to this] ?: run {
                logger.warn("Unknown key: $this for role $role")
                AWTKey(KeyEvent.VK_ESCAPE)
            }
        }
    }
}
