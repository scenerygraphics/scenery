[![scenery logo](./artwork/logo-light-small.png)](./artwork/logo-light.png)

[![Travis Build Status](https://travis-ci.org/ClearVolume/scenery.svg?branch=master)](https://travis-ci.org/ClearVolume/scenery) [![Appveyor Build status](https://ci.appveyor.com/api/projects/status/vysiatrptqas4cfy?svg=true)](https://ci.appveyor.com/project/skalarproduktraum/scenery)  [![Join the chat at https://gitter.im/ClearVolume/ClearVolume](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/ClearVolume/ClearVolume?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

# scenery  // flexible scenegraphing and rendering for scientific visualisation

## Synopsis

scenery is a scenegraphing and rendering library. It allows you to quickly create high-quality 3D visualisations based on mesh data. Currently, scenery contains an OpenGL 4-based [Deferred Shading](https://en.wikipedia.org/wiki/Deferred_Shading) renderer and an experimental [Vulkan](https://www.khronos.org/vulkan) renderer with both forward and deferred shading. Both support Virtual Reality headsets like the HTC Vive or Oculus Rift via [OpenVR/SteamVR](https://github.com/ValveSoftware/openvr). A software renderer is planned for the future.

## Examples

Have a look in the [src/test/tests/graphics/scenery/tests/examples](./src/test/tests/graphics/scenery/tests/examples/) directory, there you'll find plenty of examples how to use _scenery_ in Kotlin, and a few Java examples.

Some of the examples need additional meshes, which are not part of the repository due to their size. These meshes can be downloaded [here](https://github.com/ClearVolume/scenery/releases/download/untagged-9774714572ebaff08515/demo-models.zip) and extracted to a directory of choice. When running the examples, the environment variable `SCENERY_DEMO_FILES` should point to this directory, otherwise the models will not be loaded.

## Key bindings

Most of the demos use the following key bindings:

### Movement
| Key | Action |
| --- | --- |
| Mouse drag | Look-around |
| `W, A, S, D` | Move forward, left, back, right |
| `Shift` - `W, A, S, D` | Move forward, left, back, right fast |
| `Space` | Move upwards |
| `Shift` - `Space` | Move downwards |
| `C` | Switch between FPS and Arcball camera control modes (only used in `SponzaExample`) |

If a gamepad is connected (such as a PlayStation 3 or 4 controller), the hats can be used for movement and look-around.

### Rendering
| Key | Action |
| --- | --- |
| `K`, `L` | Increase/decrease exposure for HDR rendering |
| `Shift`-`K`, `Shift-L` | Increase/decrease gamma for HDR rendering |
| `F` | Toggle fullscreen |
| `Q` | Toggle deferred shading buffer debug view |
| `O` | Toggle SSAO (Screen-space ambient occlusion |
| `P` | Save screenshot to Desktop as PNG |

All keybindings are also listed in the [InputHandler class](./src/main/kotlin/graphics/scenery/controls/InputHandler.kt#L198).

## Building

- Create a base directory named e.g. `my_scenery_base`. Into this directory, clone the Git repository of scenery.

- Into the same directory, clone the 2.0 version of ClearGL’s Git repository: 
```shell
git clone https://github.com/ClearVolume/ClearGL.git
```

- in both the `ClearGL` and the `scenery` directory, run `mvn clean install` to build and install both packages into your local Maven repository

## Using _scenery_ in a project

### Maven artifacts

Artifacts are currently published to the ImageJ repository at `https://maven.imagej.net/content/groups/public`. If you want to use the artifacts directly, add this repository to your Maven or Gradle repository configuration.

### Using _scenery_ in a Maven project

Make sure you have followed the instructions in _Building_, such that both the scenery and ClearGL JARs have been installed into your local Maven repository.

Add these dependencies to your project's `pom.xml`:

```xml
<dependencies>
  <dependency>
    <groupId>graphics.scenery</groupId>
    <artifactId>scenery</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </dependency>

  <dependency>
    <groupId>net.clearvolume</groupId>
    <artifactId>cleargl</artifactId>
    <version>2.0.1</version>
  </dependency>
</dependencies>
```

### Using _scenery_ in a Gradle project

Make sure you have followed the instructions in _Building_, such that both the scenery and ClearGL JARs have been installed into your local Maven repository.

Add these dependencies to your project's `build.gradle`:

```groovy
compile group: 'graphics.scenery', name: 'scenery', version: '1.0.0-SNAPSHOT'
compile group: 'net.clearvolume', name: 'cleargl', version: '2.0.1'
```
