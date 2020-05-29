# scenery REPL python init file
from org.joml import *
from graphics.scenery import *
from graphics.scenery.volumes import *
from graphics.scenery.volumes import TransferFunction
from graphics.scenery.volumes import Colormap
from graphics.scenery.utils import *
from graphics.scenery.net import *
from graphics.scenery.compute import *
from graphics.scenery.numerics import Random, OpenSimplexNoise

def locate(name):
    objects = object.getIndex().toArray()

    for obj in objects:
        objectName = obj.toString().split('\n')[0]
        if name in objectName:
            return obj

    return None

# define standard variables
scene = locate("Scene")
renderer = locate("Renderer")
stats = locate("Statistics")
hub = locate("Hub")
settings = locate("Settings")
base = hub.getApplication()

# and say hello :-)
print("\n\n")
print("this is scenery.")
print("Standard library imported.\n")
print("Try scene.addChild(a = new Box(new GLVector(4.0, 4.0, 4.0)))")
print("------------------------------------------------------------")
print("")
print("")
