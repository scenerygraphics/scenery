# VulkanRenderer

## Usage

To use this renderer, set the `scenery.Renderer` system property  to `VulkanRenderer`, e.g. by passing a command line flag to Java:
```
java -Dscenery.Renderer=VulkanRenderer
```

## Validation Layers

The Vulkan Renderer supports usage of validation layers. For that, copy the validation layer's DLLs/SOs and JSONs to the working directory, and run with the system property 
```
scenery.VulkanRenderer.EnableValidations=true
```

## Device Selection

If you have multiple GPUs installed in your system, the Vulkan Renderer will let you know about the devices it discovered:

```
[main] INFO VulkanDevice -   0: Nvidia GeForce GTX TITAN X (DiscreteGPU, driver version 390.260.0, Vulkan API 1.0.65) (selected)
[main] INFO VulkanDevice -   1: AMD AMD Radeon (TM) R9 Fury Series (DiscreteGPU, driver version 1.7.0, Vulkan API 1.0.54) 
``` 

By default, it'll use the first device discovered. You can override this by setting the system property

```
scenery.VulkanRenderer.Device=id
```
where `id` is the number in front of the device in the log, as exemplified above.