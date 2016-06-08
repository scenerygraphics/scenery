// standard library
importPackage(Packages.scenery);
importPackage(Packages.cleargl);

// for threading
importClass(Packages.java.lang.Thread);

var objectLocator = function(match) {
    var objectArray = object.getIndex().toArray();

    for(i = 0; i < objectArray.length; i++) {
        if(objectArray[i].toString().indexOf(match) != -1) {
            return objectArray[i];
        }
    }

    return null;
}

// define standard variables
var scene = objectLocator("Scene");
var renderer = objectLocator("DeferredLightingRenderer");

// and say hello :-)
print("\n\n");
print("this is scenery.");
print("Standard library imported.\n");
print("Try scene.addChild(a = new Box(new GLVector(4.0, 4.0, 4.0)))");
print("------------------------------------------------------------");
print("");
print("");
