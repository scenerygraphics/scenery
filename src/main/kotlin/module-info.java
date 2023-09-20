module graphics.scenery {
    exports graphics.scenery;
    exports graphics.scenery.atomicsimulations;
    exports graphics.scenery.attribute;
    exports graphics.scenery.backends;
    exports graphics.scenery.compute;
    exports graphics.scenery.controls;
    exports graphics.scenery.effectors;
    exports graphics.scenery.fonts;
    exports graphics.scenery.geometry;
    exports graphics.scenery.net;
    exports graphics.scenery.numerics;
    exports graphics.scenery.primitives;
    exports graphics.scenery.proteins;
    exports graphics.scenery.repl;
    exports graphics.scenery.serialization;
    exports graphics.scenery.textures;
    exports graphics.scenery.ui;
    exports graphics.scenery.utils;
    exports graphics.scenery.volumes;

    requires java.base;
    requires java.desktop;
    requires java.management;
    requires java.logging;
    requires jdk.unsupported;

    requires kotlin.stdlib;
    requires kotlin.reflect;
    requires kotlinx.coroutines.core;

    requires org.joml;
    requires org.lwjgl;
    requires org.lwjgl.vulkan;
    requires org.lwjgl.tinyexr;
    requires org.lwjgl.remotery;
    requires org.lwjgl.openvr;
    requires org.lwjgl.glfw;
    requires org.lwjgl.spvc;
    requires org.lwjgl.shaderc;
    requires org.lwjgl.jemalloc;
    requires info.picocli;
    requires org.bytedeco.ffmpeg;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires com.fasterxml.jackson.kotlin;
    requires jinput;
    requires trove4j;
    requires org.scijava.ui.behaviour;
    requires org.slf4j;

//    opens java.nio;
//    opens java.nio;
//    opens sun.nio.ch;
//    opens sun.
}