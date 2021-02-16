[![scenery logo](./artwork/logo-light-small.png)](./artwork/logo-light.png)  

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/graphics.scenery/scenery/badge.svg)](https://maven-badges.herokuapp.com/maven-central/graphics.scenery/scenery) 
[![DOI](https://zenodo.org/badge/49890276.svg)](https://zenodo.org/badge/latestdoi/49890276) 
[![Join the chat at https://gitter.im/scenerygraphics/SciView](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/scenerygraphics/SciView?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) 
// [![pipeline status](https://gitlab.com/hzdr/crp/scenery/badges/master/pipeline.svg)](https://gitlab.com/hzdr/crp/scenery/-/commits/master)
[![Build Status](https://github.com/scenerygraphics/scenery/workflows/build/badge.svg)](https://github.com/scenerygraphics/scenery/actions?workflow=build)
[![Travis Build Status](https://travis-ci.org/scenerygraphics/scenery.svg?branch=master)](https://travis-ci.org/scenerygraphics/scenery) 
[![Appveyor Build status](https://ci.appveyor.com/api/projects/status/vysiatrptqas4cfy/branch/master?svg=true)](https://ci.appveyor.com/project/skalarproduktraum/scenery/branch/master) 
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/3940e961b2fc41b5a6d17e2b4fff333b)](https://www.codacy.com/app/skalarproduktraum/scenery)  

# scenery  // Flexible VR Visualisation for Volumetric and Geometric Data on the Java VM

![Blood Cells Example, scenery running on a CAVE with a scientist exploring a Drosophila melanogaster microscopy dataset, APR representation of Zebrafish head vasculature, Rendering multiple volumes in a single scene, Interacting with microscopy data in realtime](https://ulrik.is/scenery-teaser-2019.gif)

[BloodCellsExample](./src/test/kotlin/graphics/scenery/tests/examples/advanced/BloodCellsExample.kt) / [scenery running on a CAVE](./src/test/kotlin/graphics/scenery/tests/examples/cluster/DemoReelExample.kt) with a scientist exploring a Drosophila melanogaster microscopy dataset / [Adaptive Particle Representation](https://www.nature.com/articles/s41467-018-07390-9) rendering of Zebrafish head vasculature / [Rendering six different out-of-core volumes from two datasets in a single scene](./src/test/kotlin/graphics/scenery/tests/examples/bdv/BDVExample.kt) / VR interaction with microscopy data in realtime


## Synopsis

scenery is a scenegraphing and rendering library. It allows you to quickly create high-quality 3D visualisations based on mesh data. scenery contains both a OpenGL 4.1 and Vulkan renderer. The rendering pipelines of both renderers are configurable using YAML files, so it's easy to switch between e.g. [Forward Shading](./src/main/resources/graphics/scenery/backends/ForwardShading.yml) and [Deferred Shading](./src/main/resources/graphics/scenery/backends/DeferredShading.yml), as well as [stereo rendering](./src/main/resources/graphics/scenery/backends/DeferredShadingStereo.yml). Rendering pipelines can be switched on-the-fly.

Both renderers support rendering to head-mounted VR goggles like the HTC Vive or Oculus Rift via [OpenVR/SteamVR](https://github.com/ValveSoftware/openvr).

## Visit our wiki for additional info:

- [how to build](Building)
- [how to cite](Citation), for scientific publications
- [docs](Documentation)
- [examples](Examples)
- [gpu compatibility table](GpuCompatibility), per GPU and OS
- [how to import Scenery](Importing)
- [key bindings](KeyBindings)
- [logging](Logging)
- [renderer selection](Renderer)