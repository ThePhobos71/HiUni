package de.transio.hiuni.feature.mensa.data

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.feature.grades.data.GradeDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bündelt die Mensa-Preisgruppe: gespeicherte Wahl aus den Einstellungen, automatische
 * Erkennung aus Uni-Login (CAS-Profil) und LSF-Notenspiegel sowie die daraus effektive
 * Gruppe, die Mensa-Tab, Gerichtsdetail und Widget für die Preisanzeige nutzen.
 */
@Singleton
class MensaPriceGroupResolver @Inject constructor(
    private val settings: SettingsDataStore,
    casSession: CasSession,
    gradeDao: GradeDao
) {

    val preference: Flow<PriceGroupPreference> = settings.mensaPriceGroup
        .map(PriceGroupPreference::fromKey)
        .distinctUntilChanged()

    val detection: Flow<PriceGroupDetection> = combine(
        casSession.profile,
        gradeDao.observeHasAny()
    ) { profile, hasGrades -> PriceGroupRules.detect(profile, hasGrades) }
        .distinctUntilChanged()

    val effectiveGroup: Flow<PriceGroup> = combine(preference, detection) { pref, det ->
        PriceGroupRules.effective(pref, det.group)
    }.distinctUntilChanged()

    suspend fun setPreference(preference: PriceGroupPreference) {
        settings.setMensaPriceGroup(preference.storageKey)
    }
}
