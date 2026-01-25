package com.iqbal.gurmukhikeyboard50

import android.util.Log

object CameraManagerHelper {
    private var cameraLockOwner: String? = null

    fun lockCamera(owner: String): Boolean {
        if (cameraLockOwner != null) {
            Log.e("CameraManagerHelper", "Camera is already locked by $cameraLockOwner")
            return false
        }
        cameraLockOwner = owner
        Log.d("CameraManagerHelper", "Camera locked by $owner")
        return true
    }

    fun releaseCamera(owner: String) {
        if (cameraLockOwner == owner) {
            cameraLockOwner = null
            Log.d("CameraManagerHelper", "Camera released by $owner")
        } else {
            Log.w("CameraManagerHelper", "Warning: $owner tried to release camera locked by $cameraLockOwner")
        }
    }

    fun isCameraLocked(): Boolean {
        return cameraLockOwner != null
    }
}