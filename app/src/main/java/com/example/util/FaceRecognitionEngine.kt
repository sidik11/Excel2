package com.example.util

import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

object FaceRecognitionEngine {
    private val landmarkTypes = intArrayOf(
        FaceLandmark.LEFT_EYE,
        FaceLandmark.RIGHT_EYE,
        FaceLandmark.NOSE_BASE,
        FaceLandmark.MOUTH_LEFT,
        FaceLandmark.MOUTH_RIGHT,
        FaceLandmark.MOUTH_BOTTOM,
        FaceLandmark.LEFT_CHEEK,
        FaceLandmark.RIGHT_CHEEK,
        FaceLandmark.LEFT_EAR,
        FaceLandmark.RIGHT_EAR
    )

    fun vector(face: Face): List<Float> {
        val box = face.boundingBox
        val width = box.width().coerceAtLeast(1)
        val height = box.height().coerceAtLeast(1)
        val result = ArrayList<Float>(25)

        landmarkTypes.forEach { type ->
            val p = face.getLandmark(type)?.position
            if (p == null) {
                result += -1f
                result += -1f
            } else {
                result += ((p.x - box.left) / width.toFloat()).coerceIn(-1f, 2f)
                result += ((p.y - box.top) / height.toFloat()).coerceIn(-1f, 2f)
            }
        }

        result += ((face.headEulerAngleX / 45f).coerceIn(-2f, 2f))
        result += ((face.headEulerAngleY / 45f).coerceIn(-2f, 2f))
        result += ((face.headEulerAngleZ / 45f).coerceIn(-2f, 2f))
        result += (face.smilingProbability ?: 0.5f)
        result += (face.leftEyeOpenProbability ?: 0.5f)
        result += (face.rightEyeOpenProbability ?: 0.5f)
        return result
    }
}
