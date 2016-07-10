[![scenery logo](./artwork/logo-light-small.png)](./artwork/logo-light.png)

[![Build Status](https://travis-ci.org/ClearVolume/scenery.svg?branch=master)](https://travis-ci.org/ClearVolume/scenery)  [![Join the chat at https://gitter.im/ClearVolume/ClearVolume](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/ClearVolume/ClearVolume?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

# scenery  // flexible scenegraphing and rendering for scientific visualisation

## Synopsis

scenery is a scenegraphing and rendering library. It allows you to quickly create high-quality 3D visualisations based on mesh data. Currently, scenery contains an OpenGL 4-based [Deferred Shading](https://en.wikipedia.org/wiki/Deferred_Shading) renderer that also supports [OpenVR](https://github.com/ValveSoftware/openvr). Both a software-based and a [Vulkan](https://www.khronos.org/vulkan)-based renderer are planned.

## Examples

* have a look in the [src/test/kotlin/scenery/examples](./src/test/kotlin/scenery/tests/examples) directory, there you'll find plenty of examples how to use _scenery_ in Kotlin
* Java examples are coming soon.

## Building

- Create a base directory named e.g. `my_scenery_base`. Into this directory, clone the Git repository of scenery.

- Into the same directory, clone the 2.0 version of ClearGL’s Git repository: 
```shell
git clone https://github.com/ClearVolume/ClearGL.git
```

- In the `my_scenery_base` directory, create a file named `settings.gradle`, with this content:

```groovy
include "ClearGL"
include "scenery"
```
 
- Run `gradle install -Plocal=true` from both the `my_scenery_base/scenery` directory and the `my_scenery_base/ClearGL` directory. This will install the JARs into your local Maven repository, both with version `1.0-SNAPSHOT`.

## Using _scenery_

### Using _scenery_ in a Maven project

Make sure you have followed the instructions in _Building_, such that both the scenery and ClearGL JARs have been installed into your local Maven repository.

Add these dependencies to your project's `pom.xml`:
```xml
<dependencies>
  <dependency>
    <groupId>net.clearvolume</groupId>
    <artifactId>scenery</artifactId>
    <version>1.0-SNAPSHOT</version>
  </dependency>

  <dependency>
    <groupId>net.clearvolume</groupId>
    <artifactId>cleargl</artifactId>
    <version>2.0.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

### Using _scenery_ in a Gradle project

Make sure you have followed the instructions in _Building_, such that both the scenery and ClearGL JARs have been installed into your local Maven repository.

Add these dependencies to your project's `build.gradle`:
```groovy
compile group: 'net.clearvolume', name: 'scenery', version: '1.0-SNAPSHOT'
compile group: 'net.clearvolume', name: 'cleargl', version: '2.0.0-SNAPSHOT'
```
