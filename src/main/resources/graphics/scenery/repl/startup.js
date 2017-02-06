// standard library
importPackage(Packages.graphics.scenery);
importPackage(Packages.cleargl);

// for threading
importClass(Packages.java.lang.Thread);

var ObjectLocator = function(match) {
    var objectArray = object.getIndex().toArray();

    for(i = 0; i < objectArray.length; i++) {
        if(objectArray[i].toString().indexOf(match) != -1) {
            return objectArray[i];
        }
    }

    print("ObjectLocator: Could not find " + match);
    return null;
};

Array.prototype.first = function() {
    if(this === void 0 || this === null) {
        throw new TypeError();
    }

    if(this.length == 0) {
        return null;
    } else {
        return this[0];
    }
};

Array.prototype.last = function() {
    if(this === void 0 || this === null) {
        throw new TypeError();
    }

    if(this.length == 0) {
        return null;
    } else {
        return this[this.length-1];
    }
};

// define standard variables
var scene = ObjectLocator("Scene");
var renderer = ObjectLocator("Renderer");
var stats = ObjectLocator("Statistics");

// and say hello :-)
print("\n\n");
print("this is scenery.");
print("Standard library imported.\n");
print("Try scene.addChild(a = new Box(new GLVector(4.0, 4.0, 4.0)))");
print("------------------------------------------------------------");
print("");
print("");
