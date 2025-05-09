package net.echonolix.caelum.vktest

import net.echonolix.caelum.NFloat
import net.echonolix.caelum.NInt
import net.echonolix.caelum.NStruct

interface Vec2f : NStruct {
    val x: NFloat
    val y: NFloat
}

interface Vec3f : NStruct {
    val x: NFloat
    val y: NFloat
    val z: NFloat
}

interface Vec4f : NStruct {
    val x: NFloat
    val y: NFloat
    val z: NFloat
    val w: NFloat
}

interface Vec2i : NStruct {
    val x: NInt
    val y: NInt
}

interface Vec3i : NStruct {
    val x: NInt
    val y: NInt
    val z: NInt
}

interface Vec4i : NStruct {
    val x: NInt
    val y: NInt
    val z: NInt
    val w: NInt
}