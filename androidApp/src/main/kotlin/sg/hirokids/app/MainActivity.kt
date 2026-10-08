package sg.hirokids.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import sg.hirokids.shared.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val location = (application as HiroApplication).location
        location.attach(this) // the location permission dialog shows on this Activity
        setContent { App(location) }
    }
}
