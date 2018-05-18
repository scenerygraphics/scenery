# CHANGELOG

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