package com.deep3d.upgrade.depth

enum class GroundProfile { TOPRAK, BETON_ASFALT, KAYA_TAS }

object DepthModel {
    fun toMeters(depthRaw: Double, profile: GroundProfile): Double {
        val k = when(profile) {
            GroundProfile.TOPRAK -> 1.00
            GroundProfile.BETON_ASFALT -> 1.25
            GroundProfile.KAYA_TAS -> 1.40
        }
        return k * depthRaw
    }
}
