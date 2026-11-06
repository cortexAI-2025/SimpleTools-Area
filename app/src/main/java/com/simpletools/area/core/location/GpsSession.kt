package com.simpletools.area.core.location

/** Session d'enregistrement unique partagée entre le service de premier plan et l'interface. */
object GpsSession {
    val recorder = GpsRecorder()
}
