// standard library
importPackage(Packages.scenery);
importPackage(Packages.cleargl);

// for threading
importClass(Packages.java.lang.Thread);

// define standard variables
var scene = object.getIndex().toArray()[0];
var renderer = object.getIndex().toArray()[1];

// and say hello :-)
print("\n\n");
print("this is scenery.");
print("Standard library imported.\n");
print("Try scene.addChild(a = new Box(new GLVector(4.0, 4.0, 4.0)))");
print("------------------------------------------------------------");
print("");
print("");
