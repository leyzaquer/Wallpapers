package org.laneby.wallpaperpicker

import android.app.Application
import com.google.android.material.color.DynamicColors

class ChooserApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Observe dynamic colors changes
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}