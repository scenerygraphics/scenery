# CHANGELOG

# scenery-0.7.0-beta-1 to scenery-0.7.0-beta-2

## Additions and Changes

* Camera: add frustum culling, add `canSee(Node)` function
* adds Arrow class
* adds LogbackUtils class for controlling logback log levels, e.g. in Fiji
* Node: remove `instanceMaster` and `instanceOf` properties, this is now handled via `instances` and `parent` (BREAKING CHANGE)
* Volume: in alpha blending mode, use 0 occlusion steps by default for performance reasons
* Renderer: change window title only if not running in embedded mode
* OpenVRHMD: log exception in case model loading fails
* TrackerInput/OpenVRHMD/Hololens/TrackedStereoGlasses: add device connect/disconnect handlers
* SceneryBase: add `force` flag to replaceRenderer()
* ExampleRunner: run tests in stereo and non-stereo modes
* Line: enable transparent rendering
* VulkanRenderer/OpenGLRenderer: optimise UBO updates
* DeferredLighting: optimize Z reconstruction
* DeferredLighting: improve light falloff
* OpenGLRenderer: initialise renderer synchronously
* VulkanRenderer: create descriptor sets based on pass type, not name
* OpenGLRenderer: limit state changes for shaders

## Fixes

* SwingSwapchain: add resize handlers
* SwingSwapchain: don't impose preferred dimensions on SceneryJPanels
* OpenGLRenderer/VulkanRenderer: fix wrong buffer size issues when adding new instances or removing old ones and using `CopyOnWriteArrayList` the way it is supposed to
* OpenGLRenderer/VulkanRenderer: do not overwrite pre-existing settings
* PointLight: calculate bounding boxes correctly

## Dependency Updates

* bumps SciJava parent POM to 25.0.0
* bumps Kotlin to 1.3.21
* bumps kotlinx-coroutines-core to 1.1.1
* bumps dokka to 0.9.18

# scenery-0.6.2 to scenery-0.7.0-beta-1

## Additions and Changes

* introduces preliminary support for rendering BigDataViewer volumetric datasets
* ShaderProperty/Node: Add support to store shader properties in HashMap
* OpenGLObjectState: add getUBO() and getBackedUBO() methods
* OpenGLRenderer: Cut down on non-null asserted alls
* VulkanRenderer/OpenGLRenderer: Support rendering to Swing windows via SceneryJPanel
* VulkanRenderer: Add AWTSwapchain for drawing via lwjgl3-awt
* SceneryBase: Only show REPL after renderer initialisation is complete
* SceneryBase: Call XInitThreads on Linux when running X11
* Renderer: Use ACES tonemapping operator for HDR rendering
* OpenGLRenderer/VulkanRenderer: Add support for GenericTexture's incremental updates
* VulkanShaderModule: Improve detection of inconsistencies
* Renderer: Add `DeferredShadingLowEnd.yml` pipeline configuration for better performance on low-end GPUs
* VulkanRenderer: Remove nullability of various members that do not require to be null
* VulkanShaderModule: Store type of UBO
* Camera: store projection type, and viewport sizes, add aspectRatio() function
* PupilEyeTracker: Improve calibration routine
* EyeTrackingExample: Improve feedback on failed/successful calibration
* ProceduralVolumeExample: Support procedural generation of 16bit volumes

## Fixes

* OpenGLRenderer: Fix handling of sRGB textures and render targets
* OpenGLRenderer: Ensure object metadata was created when updating instances
* VulkanRenderer: Don't fail to initialize if NvidiaGPUStats fail for some reason
* VulkanRenderer: Correctly determine number of necessary mipmap levels
* VulkanRenderpass: Mark old DSLs for garbage collection and delete them only upon renderpass closure
* Shaders: Use correct key for caching ShaderPackages

## Dependency Updates

* bumps jackson to 2.9.8
* bumps ClearGL to 2.2.5
* bumps SciJava Parent POM to 24.0.0
* bumps lwjgl to 3.2.1
* bumps Kotlin to 1.3.11
* bumps spirvcrossj to 0.5.0-1.1.85
* bumps coremem to 0.4.6
* bumps kotlinx-coroutines to 1.0.0
* bumps JInput to 2.0.9
* bumps ffmpeg-platform to 4.0.2-1.4.3
* bumps msgpack to 0.8.16
* bumps Kryo to 4.0.2
* bumps JOCL to 2.0.1
* bumps jna to 4.5.2

# scenery-0.6.1 to scenery-0.6.2

## Fixes

* Volume: circumvent race condition when updating volumes with improved locking code
* VulkanRenderer/OpenGLRenderer: initialize spirvcrossj statically (once-per-process)

## Additions and Changes

* POM: Add Sonatype repository before ImageJ Maven
* VulkanRenderer: revamps JavaFX swapchain handling to enable embedding in JFXPanels
* VulkanSwapchain/HeadlessSwapchain/JavaFXSwapchain/OpenGLSwapchain: record the number of images presented with this swapchain
* Renderer: Output exception name in case Vulkan cannot be initialiased, and full stack trace if debug logging is enabled
* VulkanRenderer/OpenGLRenderer: Determine renderer window size depending on size of embedded component
* JavaFXMouseAndKeyHandler: Attach event handlers to JavaFX `Scene`, and not to `Stage`

## Dependency Updates

* bumps spirvcrossj to 0.4.2

# scenery-0.6.0 to scenery-0.6.1

## Fixes

* REPL: Fix typo in `startup.js` launch script that caused `graphics.scenery.volumes` not getting imported
* Volume: Fix normalisation for the different rendering modes

## Additions and Changes

* TransferFunction: add rampMax parameter to ramp factory function to set a maximum value to ramp to
* Volume: Make local maximum intensity projection (LMIP) the default rendering methods due to performance issues with alpha blending

# scenery-0.6.0-beta-1 to scenery-0.6.0

## Fixes

* VulkanRenderer: Do not fail if a descriptor set is not found, but emit strong error message and continue rendering
* VulkanRenderer: Fix incorrect initialisation of input descriptor sets in case the default shader does not consume the pass' inputs
* OpenVRHMD: Transition Vulkan input texture to layout required by OpenVR
* FXSwapchain: Force correct image flipping in SceneryPanels and remove KHRSwapchain remnants not needed anymore due to HeadlessSwapchain
* REPL: Add backends to REPL startup script, fix issue with object location there, and add SceneryBase object to REPL accessible objects
* HasGeometry: Refactor material import code for MTL files to prevent output of false positive errors

## Additions and Changes

* adds HBAO as new default AO option, and SSAO as a faster, lower quality option
* Adds support for on-the-fly renderer switching, closes #106.
* SceneryBase: Use canonical way to close renderer 
* VulkanRenderer: Support filtering of event types for strict validation, by setting `scenery.VulkanRenderer.StrictValidation` to a list of event types (e.g. '10,6')
* Renderer: Update rendering pipelines with single-component render targets for AO
* Move raycasting code from SelectCommand to Scene.raycast() and Camera.getNodesForScreenSpacePosition()
* Add EnumCycleCommand for shifting through enums
* adds the capability to change shaders as part of rendering quality options
* VulkanRenderer: improves the construction of a pass' input descriptor sets
* VulkanRenderpass: Improve consistency checks on rendertargets

## Dependency Updates

* bumps Kotlin to 1.2.71

# scenery-0.5.1 to scenery-0.6.0-beta-1

## Fixes

* Renderer: Don't overwrite user-defined settings
* Fix normalisation bug in DeferredLighting.frag
* Volume: write depth correctly
* SceneryPanel: Remove unnecessary texture locking, improving stuttering
* JavaFXMouseAndKeyHandler: Handle shift-scroll correctly on Windows
* Renderer: Save screenshots even if push mode is active. Fixes #213.
* Line: add geometry shader to expected shaders


## Additions and Changes

* __Breaking change__: This Release changes the way shaders are handled by Nodes. It removes Node.useClassDerivedShader and replaces it with a unified and more flexible system that also allows for shader factories. See Shaders and ShaderFactory (#203)
* Volume: Add local ambient occlusion
* Volume: allow for different rendering methods (maximum projection, local maximum projection, and alpha blending)
* Volume : adds customizable transfer functions
* Volume: adds alpha blending rendering mode in addition to max projection and local max projection
* SceneryBase: Set running flag to true directly after initialisation
* VulkanRenderer: Add options to force Vsync and undecorated windows
* Settings: add setIfUnset()
* VulkanDevice: Improve queue creation for multiple queues
* Renderer: Add option to overwrite existing screenshots. Closes #213.
* Renderer: add firstImageReady property to indicate first image has been rendered
* VulkanRenderer: Don't request swapchain extensions if running headless
* Volume: do not discard fragments that don't need a raycast
* Volume: use stepSize instead of maxsteps as parameter, and improve local occlusion
* Volume: Introduce Colormap class, which can be backed by a file or a buffer
* ReaderExample: support reading of volume files (renamed from ReadModelExample)
* Hololens: Cache view matrices
* Preliminary support for directional lights
* PupilEyeTracker: improves eye tracking calibration routine
* EyeTrackingExample: Improve feedback on failed/successful calibration
* Camera: store projection type, and viewport sizes, add aspectRatio() function
* VulkanRenderer: Preparations for support of asynchronous texture uploads
* SPIRV: compile shaders, even if they have preprocessor defs, to check their validity (generated SPIRV is deleted afterwards)
* ArcballCameraControl: Support varying/moving targets
* Node: Introduce callbacks for Node added, Node removed, Node properties updated
* Scene: Support handing the Node in onNodePropertiesChanged
* VulkanBufferAllocation: Add slack to allocations, and keep suballocations sorted by offset
* ShaderMaterial: allow to specify expected shader types for fromClass()
* Shaders: Provide JVM constructor overloads for ShadersFromClassName()
* VulkanBufferAllocation: Add slack to allocations, and keep suballocations sorted by offset
* Hololens: Subscribe to view transforms via SUB socket, not via REQREP any more
* The following classes have had their documentation improved: DirectionalLight, Light, ShaderFactory, Shaders, VulkanFramebuffer, VulkanDevice


## Dependency updates

* bumps Kotlin to 1.2.61
* bumps lwjgl to 3.2.0
* bumps scijava parent POM to 23.1.1

# scenery-0.5.1 to scenery-0.5.2

## Fixes

* JavaFXMouseAndKeyHandler: Fixes handling of double-click events
* JavaFXMouseAndKeyHandler: Fixes handling of scrolling events

# scenery-0.5.0 to scenery-0.5.1

## Additions and Changes: 

* Introduces Renderable/Node.renderScale for global scales, such as coming from physical dimensions of volumes.
* PointCloud: readFromPALM now automatically detects record separators
* Volume: use volumetric data as normalised data, and hand data ranges to the shader, instead of using it as unnormalised data, which is not as well supported
* JavaFXMouseAndKeyHandler: Don't rely on AWT for double-click intervals
* UBO: Make cached size available as read-only
* Camera: Add offset parameter to viewportToWorld to set world-space offset
* DetachedHeadCamera: take HMD presence into account when returning projection matrix
* VulkanRenderer: uses buffer pooling now, with vertex buffers allocated centrally, and then sub-allocated to scene objects (#199)
* SceneryBase: Make `accumulator`, `currentTime`, `t`, and `shouldClose` protected instead of private
* PupilEyeTracker: Discard first 15 samples from each calibration point to allow for eye movement, and use lower default threshold
* EyeTrackingExample: Color reference target according to gaze confidence
* RunAllExamples: Renamed to ExampleRunner
* Added LotsOfSpheres stresstest
* Added PickerExample to demonstrate object picking via SelectCommand
* The following classes have had their documentation improved: 
ArcballCameraControl, Blending, DemoReelExample, DirectWriteableImage, HasGeometry, MouseAndKeyHandlerBase, Node, OpenCLContext, PupilEyeTracker, Random, SceneryPanel, SelectCommand, SystemHelpers, Volume, VulkanCommandBuffer, VulkanObjectState, VulkanTexture, VulkanUBO


## Fixes

* fixes bounding grids for Volumes (#195)
* VulkanRenderer: fixes an issue where the screenshot buffer would contain an old image
* VulkanRenderer: Do not initialize GLFW unless explicitly needed — this also fixes some issues that occured running on X in conjunction with AWT or JavaFX
* Volume: don't ignore gamma
* Volume: apply correct scaling for anisotropic volumes
* OpenGLRenderer: automatically resize UBO buffers if needed
* SceneryBase: fix high CPU usage issue when running under JavaFX
* OpenGLRenderer/VulkanRenderer: Prevent screenshots from being overwritten if taken in short succession (#193)

## Dependency updates

* bumps ClearGL to 2.2.1
* bumps scijava parent POM to 22.4.0
* bumps Kotlin to 1.2.51
* bumps kotlinx-coroutines to 0.23.4

## Continuous Integration

* Appveyor: update Maven version to 3.5.4
* Travis: collect coverage data via JaCoCo and upload to codacy

# scenery-0.4.2 to scenery-0.5.0

## Additions and Changes

* Renderer: introduces push rendering mode that only renders an image if the scene is changed or an input event happens. Activate by setting Renderer's pushMode property to true
* SDFFontAtlas: Add support for serialisation to and from file, greatly improving load times
* VulkanRenderer: does not allocate window surfaces in headless mode anymore
* VulkanRenderer: Refactor FXSwapchain and HeadlessSwapchain, let FXSwapchain inherit from HeadlessSwapchain
* BoundingGrid: allow for removal from Node by setting `BoundingGrid.node = null`
* the default movement speed has been changed to 0.5m/s, the fast movement speed to 1.0m/s
* The following classes have had their documentation improved: BufferUtils, Camera, FPSCameraControl, FXSwapchain, GPUStats, GamepadCameraControl, GamepadMovementControl, HeadlessSwapchain, LazyLogger, MovementCommand, OpenGLRenderer, OpenGLShaderProgram, OpenGLUBO, ParallelHelpers, ProceduralNoise, RenderConfigReader, Renderdoc, RingBuffer, Scene, SceneryWindow, ScreenConfig, Settings, Statistics, Swapchain, UBO, VU, Volume, VolumeEffector, SlicingVolumeEffector, VulkanBuffer, VulkanRenderpass, VulkanUBO
* The following classes have been refactored and/or cleaned up: FPSCameraControl, GamepadCameraControl, GamepadMovementControl, MovementCommand, RenderConfigReader, Scene, Settings, VulkanBuffer, VulkanRenderpass
* added @JvmStatic annotations to companion objects where applicable for better Java compatibility
* introduces StickyBoolean class for Booleans that stay true™
* POM: Move ClearGL version to property
* POM: Inherit scijava component versions from parent POM
* update SPIRV generator script
* Renderer: Throw RuntimeException in case renderer initialisation fails completely.
* RunAllExamples: Don't terminate GLFW after every run
* Rename Swapchain.next()'s waitForSemaphore to signalSemaphore (which is correct), add correct waitDstStageMask for FXSwapchain.present()

## Fixes

* Line: Don't cull to produce consistent results between OpenGL and Vulkan (#186)
* VulkanTexture: Print requested allocation size in debug mode in case allocation fails
* Volume: prevent unwanted volume deallocation
* JavaFXMouseAndKeyHandler: Improve Java9/10 compatibility by introducing method to bridge between KeyCode.impl_getCode() and KeyCode.getCode()
* VulkanRenderer: Don't ignore a material's CullingMode

## Dependency updates

* bumps ClearGL to 2.2.0

# scenery-0.4.1 to scenery-0.4.2

## Additions and Changes

* Remove `Material.doubleSided`, use the more flexible `Material.cullingMode` as a replacement (breaking change)
* Hub/SceneryBase: No more global hub storage to support multiple windows, etc.
* Node: Add `Node.runRecursive()` to run recursive operations on nodes from both Java and Kotlin
* Renderer/SceneryBase: decouples renderer from applications state, working towards #106
* Line: make vertexCount public get/private set
* Update SPIRV version of shaders, now compiled with Vulkan SDK 1.1.73 version of glslang
* OpenGLRenderer: In embedded configurations (such as JavaFX), use GL_BACK_BUFFER for reading
* adds Triangle.yml demo pipeline configuration, to be used as minimum working example for hardware compatibility

## Fixes

* Correct image flipping when rendering to a JavaFX SceneryPanel from OpenGL (#181)
* introduces codacy for code style checking, and fixes a few of the issues it found
* TextBoard: use default value of 12, in case sdf.maxDistance cannot be determined from Settings
* Line: Use Line.material's colors in the shader instead of ignoring it
* SystemHelpers: handle Linux/macOS path construction better
* VulkanRenderer: Bind `currentEye` push constants only if required by shader and improve push constant support in general
* VulkanBuffer/VulkanUBO: Guard better against double-closing
* H264Encoder: handle errors returned from ffmpeg better
* SceneryBase: Don't ignore custom arcball targets in setupCameraModeSwitching() (#173)
* OpenGLRenderer: Take image width into consideration when checking memory alignment. (#172)

## Dependency updates

* bumps dokka to 0.9.17
* bumps scijava-common to 2.74.2
* bumps pom-scijava to 22.3.0
* bumps Kotlin to 1.2.50
* bumps spirvcrossj to 0.4.1
* bumps javacpp/ffmpeg to 3.4.2-1.4.1

# scenery-0.4.0 to scenery-0.4.1

## Additions and Changes

* Volume: use pre-multiplied alpha when rendering volumes
* Volume: lazily deallocate out-of-use volumes
* REPL: generate JVM overloads for constructor
* Renderer: better error messages in case no renderer could be constructed
* VulkanRenderer: use regular UBOs for LightParameters and VRParameters
* VulkanDevice: introduce device/driver-specific workarounds and warnings
* DSSDO: Use zero samples for the moment (algorithm will be changed to HBAO or SAO in an upcoming release)

## Fixes

* Volume: improved check for near plane before discarding rays
* OpenGLRenderer: when loading textures, adjust GL_UNPACK_ALIGNMENT if texture would need to be padded

# scenery-0.3.1 to scenery-0.4.0

## Additions and Changes

* adds support for [Windows Mixed Reality](https://www.microsoft.com/en-us/windows/windows-mixed-reality) VR headsets
* VulkanRenderer: Support for headless rendering
* InputHandler: getAllBindings() added to query all current key bindings 
* OpenVRHMD: Add determination of controller handedness
* Allow SceneryBase and REPL to consume an existing SciJava context
* OpenVR: adds loading of controller etc. models originating from custom OpenVR drivers such as WMR
* OpenVR: rework eye pose model to support the different coord frames used by the HTC Vive and WMR headsets
* improves attaching VR controllers to nodes
* InputHandler/OpenVRHMD: Support adding multiple key bindings at once 
* HasGeometry: Emit warning if MTL file referred by an OBJ is not found, but do not fail
* introduces BoundingGrid class to show grids around existing nodes
* introduces Node::getMaximumBoundingBox() to get the maximum bounding box of the node and all children
* introduces Node intersections via bounding spheres
* Renderer: Add screenshot() (without parameters) for push-button functionality
* added ReadModelExample to load STL and OBJ models of the user's choice, remove BoxedProteinExample
* Node: add scaleUp parameter to fitInto(), to let the user set whether the Node should only be down, or also upscaled  
* introduces an experimental test runner for boosting reproducibility (RunAllExamples)
* VU: Throw RuntimeExceptions in case Vulkan commands return result codes < 1, indicating an error
* Update all examples to use the new, radius-based lighting model

## Fixes

* update GPU compatibility table for Broadwell and Haswell GPUs and fix wrong architecture for GeForce 750M
* ArcBallCameraControl: Fixed a sign error
* fixes and issue where Volume rendering in VR could be incorrect due to the volume being culled too early
* OpenVR: load best possible models for given device type, and adjust interfaces to support that — before, only the HTC Vive type controller modes were loaded
* VulkanSwapchain: Lazily deallocate old swapchains to prevent deallocation of a in-use swapchain
* HasGeometry: Use correct byte order when reading binary STL files
* fixes a mistake in the FXAA shader that caused incorrect sampling leading to no AA at all
* improves font rendering with transparent background
* VulkanRenderer: don't create H264Encoder unless really requested
* FXSwapchain/HeadlessSwapchain: Use correct src/dstStages for layout transitions

## Dependency updates

* bumps ClearGL to 2.1.6
* bumps Kotlin to 1.2.41

# scenery-0.3.0 to scenery-0.3.1

## Additions and changes

* `Renderer`: fall back to OpenGL in case Vulkan cannot be initialiased

## Fixes

* `OpenGLRenderer`: fixes an issue where volume/geometry intersections where incorrect due to wrong depth reconstruction in OpenGL
* `OpenGLRenderer`/`VulkanRenderer`: fixes an issue where the setting `Renderer.SupersamplingFactor` was not correctly taken into consideration when creating render targets
* `OpenGLRenderer`: fixes an issue where `Volume` appeared to be blank, due to unsigned int volumetric data being sampled linearly, which is unsupported on some platforms (e.g. Nvidia GT750M, AMD Radeon R9 M370X)
* `VulkanTexture`: converts RGB textures to RGBA, as RGB formats are not widely supported (#152, thanks for the report to @maarzt)
* `UBO`: fixes an issue that could cause incorrect UBO serialisation and adds unit tests to prevent such issues from reoccuring
* `Line`: fixes incorrect vertex in/outs in line shader
* fix lighting in examples: LocalisationExample, TexturedCubeJavaExample, JavaFXTexturedCubeExample, JavaFXTexturedCubeJavaExample


# scenery-0.2.3-1 to scenery-0.3.0

## Additions and changes

* Implement graphics quality options, governed by the renderer config file.
* Added `@JvmOverloads` annotation to GenericTexture constructor and manual overloads to HasGeometry.readFromOBJ
* introduces customizable framebuffer attachment sizes, such that e.g. AO can be done at a lower resolution
* simplifies the framebuffer attachment definition in the render config YAML files
* `VulkanFramebuffer`: Refactoring to remove redundant code
* Support for differently-sized rendertargets, e.g. for lower-resolution ambient occlusion and simplify rendertarget declaration in YAML files
* `TextBoard`: Improved font rendering for text boards with background color
* introduces preliminary AR support (see ARExample)
* initial support for [PupilLabs](https://www.pupil-labs.com) eye trackers (see `PupilEyeTracker` and `EyeTrackingExample`)
* support for [Renderdoc](https://www.renderdoc.org), activate by setting `scenery.AttachRenderdoc=true`, works with both OpenGL and Vulkan
* `OpenVRHMD`: Adds support for handling input from the sticks, and ui-behaviour-based input handling
* `Camera`: add `viewToWorld` and `viewportToWorld` functions
* `HasGeometry`: add option to recalculate OBJ normals upon import
* `Node`: add `Node.uuid` property to uniquely identify nodes
* adds support for preprocessor directives (like `#ifdef`), shaders that contain such are exempt from offline compilation
* compiled shaders are now post-processed using `spirv-opt` to generate optimised code
* shader loop unrolling is now supported
* add DSSDO as default ambient occlusion algorithm
* new examples: `ProceduralVolumeExample` and `ARExample`
* `PointLight`: Add a bit of margin to proxy geometry to render high-intensity lights correctly, and fix issue where position was multiplied twice with world matrix
* introduces a complete PBL implementation (based on Oren-Nayar and Cook-Torrance BRDF models), complete with an example how to use the new material properties (PBLExample): ![physically-based-lighting](https://user-images.githubusercontent.com/586495/36495656-43f8f69a-1736-11e8-934b-1e46777113cb.png)
* introduces a leaner G-Buffer for deferred rendering, and light volumes
* introduces DSSDO for directional occlusion, see https://people.mpi-inf.mpg.de/~ritschel/Papers/SSDO.pdf and https://github.com/kayru/dssdo
* introduces better instancing in OpenGLRenderer and VulkanRenderer with coroutine-based scene discovery
* `SceneryBase`: adds a shutdown hook
* adds preliminary video streaming support via `H264Renderer`

## Fixes

* `VulkanRenderer`: Fixes an issue where stale command buffers would not be re-recorded in stereo mode
* `VulkanRenderer`: Make sure to re-record both eye passes in case one becomes stale
* Fix bug in FXAA shader that would cause artifacts with small objects
* `Statistics`: Asynchronous updates
* `VulkanRenderer`: Remove superfluous waitForFence() call
* `VulkanRenderer`: Better statistics per renderpass
* `Renderer`: `VulkanRenderer` is now the default renderer on Windows and Linux, override by setting the system property `scenery.Renderer` to `OpenGLRenderer`
* `OpenGLRenderer`: fix weird window closing behaviour with JOGL throwing exceptions
* `OpenGLRenderer`/`VulkanRenderer`/`Display`/`TrackerInput`: Use per-eye view matrices for VR/AR rendering
* `OpenGLRenderer`: enable use of linear interpolation for 3D textures
* `VulkanRenderer`: fixes determination and allocation for descriptor set layouts for the created renderpasses
* `VulkanRenderer`: better determination of a node's pipeline by using `Node.uuid`
* `VulkanRenderer`: reduce per-frame allocations
* `VulkanRenderer`: Correct input DSL/DS determination for renderpasses that only require a subset of a given framebuffer
* `SceneryBase`: better frame time interpolation
* `TextBoard`: move all text rendering infrastructure away from the renderers
* `HasGeometry`: Fix face array splitting issue where first item might not be returned
* `Sphere`: Improves tessellation for `Sphere` class and makes it texturable.
* `OpenGLRenderer`: fixes issue when rendering stereo with OpenGL where projection matrix instead of inverse were used in UBO for position reconstruction.
* `UBO`: fixes issue where UBO buffer might be overstepped.
* `Volume`: Correct intersection of volumetric and geometric data.
* moves `Numerics` to package `graphics.scenery.numerics`
* moves `forEach(Parallel/Async)` and `map(Parallel/Async)` to package `graphics.scenery.utils`
* adds `ProceduralNoise` interface, with `OpenSimplexNoise` implementation based on Kurt Spencer's [Java implementation](https://gist.github.com/KdotJPG/b1270127455a94ac5d19)
* adds `Icosphere`
* DeferredLighting: Fix NdotL to be non-negative
* removes unnecessary usages of `System.err`/`System.out` and replaces them with `LazyLogger`
* fixes an issue with Numerics.randomFromRange, where numbers from the wrong range were generated
* `VulkanTexture`: Correct image transitions for mipmap creation
* fixes Node::composeModel matrix composition order
* fixes compatibility issues with JDK9 (duplicate module-info.class, return values of FloatBuffer.flip(), signature of Unsafe::putInt)
* optimises UBO.alignmentCache to use Trove's TIntObjectHashMap to lessen allocations in UBO serialisation
* Node.metadata can now contain `Any` instead of just `NodeMetadata`
* `VulkanSwapchain`: allow `VK_ERROR_OUT_OF_DATE_KHR` on `vkQueuePresentKHR`, as it is a valid return value and does not indicate an actual error (will trigger swapchain recreation though)
* `VulkanRenderer`: Fix resizing issue on Linux and retire old swapchains immediately.


## Dependency updates
* bumps spirvcrossj to 0.4.0, which includes updates to the Vulkan SDK 1.1.70 version of glslang and spirvcross
* bumps lwjgl to 3.1.6
* bumps jeromq to 0.4.3
* bumps ClearGL to 2.1.5
* bumps scijava-common to 2.69.0
* bumps Dokka to 0.9.16