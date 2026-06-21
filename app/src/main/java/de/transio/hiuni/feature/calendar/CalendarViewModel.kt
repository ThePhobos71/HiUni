package de.transio.hiuni.feature.calendar

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    @Suppress("unused") private val repository: CalendarRepository
) : ViewModel()
