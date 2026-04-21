package tw.bluehomewu.devicemonitor.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.data.remote.PairingRepository
import tw.bluehomewu.devicemonitor.di.AppModule

private const val CODE_TTL = 30

class PairingViewModel(private val pairingRepository: PairingRepository) : ViewModel() {

    private val _code = MutableStateFlow("----")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _timeLeft = MutableStateFlow(CODE_TTL)
    val timeLeft: StateFlow<Int> = _timeLeft.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var countdownJob: Job? = null
    private var currentOwnerUid: String = ""

    fun start(ownerUid: String) {
        currentOwnerUid = ownerUid
        generate()
    }

    fun regenerate() {
        countdownJob?.cancel()
        generate()
    }

    private fun generate() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                _code.value = pairingRepository.generateCode(currentOwnerUid)
                startCountdown()
            }.onFailure {
                _error.value = "無法產生配對碼，請檢查網路連線"
            }
            _isLoading.value = false
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            _timeLeft.value = CODE_TTL
            while (isActive && _timeLeft.value > 0) {
                delay(1_000L)
                _timeLeft.value -= 1
            }
            if (isActive) generate()  // Auto-regenerate on expiry
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { PairingViewModel(AppModule.pairingRepository) }
        }
    }
}
