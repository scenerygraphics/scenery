# scenery REPL python init file
from graphics.scenery.volumes import TransferFunction, Colormap, Volume
from org.joml import Vector2f, Vector3f, Vector4f, Vector2i, Vector3i, Vector4i, Matrix3f, Matrix4f, Matrix4x3f
from graphics.scenery import *
from graphics.scenery import SceneryElement
from graphics.scenery.utils import *
from graphics.scenery.net import *
from graphics.scenery.compute import *
from graphics.scenery.numerics import Random, OpenSimplexNoise

def locate(name):
    objects = object.getIndex().toArray()

    for obj in objects:
        objectName = obj.toString().split('\n')[0]
        print("Found object: " + objectName)
        if name in objectName:
            return obj

    return None

# define standard variables
scene = locate("Scene")
stats = locate("Statistics")
hub = locate("Hub")

if hub != None:
    settings = hub.get(SceneryElement.Settings)
    renderer = hub.get(SceneryElement.Renderer)
    base = hub.getApplication()

# and say hello :-)
print("\n\n")
print("this is scenery.")
print("Standard library imported.\n")
print("Try scene.addChild(a = Box(Vector3f(1.0, 1.0, 1.0)))")
print("------------------------------------------------------------")
print("")
print("")
