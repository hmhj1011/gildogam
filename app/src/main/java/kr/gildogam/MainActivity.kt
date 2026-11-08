package kr.gildogam

import android.Manifest
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kr.gildogam.core.CarEvent
import kr.gildogam.dex.Controller
import kr.gildogam.ui.AskPanel
import kr.gildogam.ui.DemoConsole
import kr.gildogam.ui.DexScreen
import kr.gildogam.ui.DriveScreen
import kr.gildogam.ui.GildogamTheme
import kr.gildogam.ui.NoticeBar
import kr.gildogam.ui.Road

/** Holds the controller for the whole process so a journey survives screen changes. */
class GildogamApp : Application() {
    val controller: Controller by lazy { Controller(this) }
}

class MainActivity : ComponentActivity() {

    private val controller by lazy { (application as GildogamApp).controller }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        controller.onPermissionsChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller.start()
        permissions.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                "android.car.permission.CAR_SPEED",
                "android.car.permission.CAR_EXTERIOR_ENVIRONMENT",
            ),
        )

        setContent {
            GildogamTheme {
                val ui by controller.ui.collectAsState()
                var showDex by remember { mutableStateOf(false) }
                var consoleOpen by remember { mutableStateOf(false) }
                // The journey book is a parked-only screen.
                val dexVisible = showDex && ui.car.parked

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Road.Asphalt),
                ) {
                    if (dexVisible) {
                        DexScreen(
                            dex = ui.car.dex,
                            onBack = { showDex = false },
                            onNote = controller::askNote,
                        )
                    } else {
                        DriveScreen(
                            ui = ui,
                            onRiders = controller::askRiders,
                            onOpenDex = { showDex = true },
                            onEndJourney = { controller.dispatch(CarEvent.EndJourney) },
                            onToggleConsole = { consoleOpen = !consoleOpen },
                            consoleOpen = consoleOpen,
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    ) {
                        ui.notice?.let { NoticeBar(it, controller::dismissNotice) }
                        ui.asking?.let { ask ->
                            AskPanel(ask, ui.heard, onTyped = controller::answer, onCancel = controller::cancelAsk)
                        }
                        if (consoleOpen && !dexVisible && ui.asking == null) {
                            DemoConsole(
                                ui = ui,
                                onScenario = controller::playScenario,
                                onEvent = controller::dispatch,
                                onPlay = controller::play,
                                onStop = controller::stopDemo,
                                onDemoMode = controller::setDemoMode,
                                onClock = controller::setClock,
                                onReset = controller::resetDex,
                            )
                        }
                    }
                }
            }
        }
    }
}
