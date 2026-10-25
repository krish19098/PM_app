@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.lifecycle

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

abstract class Lifecycle {
    enum class Event { ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY, ON_ANY }
}

interface LifecycleOwner {
    val lifecycle: Lifecycle
}

abstract class ViewModel {
    protected open fun onCleared() {}
}

open class AndroidViewModel(private val application: Application) : ViewModel() {
    @Suppress("UNCHECKED_CAST")
    fun <T : Application> getApplication(): T = application as T
}

val ViewModel.viewModelScope: CoroutineScope get() = MainScope()
