# VulkanRenderer

**Warning** -- This renderer is heavily work in progress and not feature-complete yet. Don't complain if it crashes your computer, makes you angry or if your coffee machine stops working.

## Usage

To use this renderer, set the `scenery.Renderer` system property  to `VulkanRenderer`, e.g. by passing a command line flag to Java:
```
java -Dscenery.Renderer=VulkanRenderer
```


## Validation Layers

The Vulkan Renderer supports usage of validation layers. For that, copy the validation layer's DLLs/SOs and JSONs to the working directory, and run with the system property 
```
scenery.VulkanRenderer.EnableValidation=true
```

## Device Selection

If you have multiple GPUs installed in your system, the Vulkan Renderer will let you know about the devices it discovered:

```
  0: Nvidia GTX TITAN X (Discrete GPU, driver version 376.76.0, Vulkan API 1.0.24) (selected)
  1: AMD AMD FirePro W9100 (Discrete GPU, driver version 1.3.0, Vulkan API 1.0.26) 
``` 

By default, it'll use the first device discovered. You can override this by setting the system property

```
scenery.VulkanRenderer.Device=id
```
where `id` is the number in front of the device in the log.