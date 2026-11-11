package com.daiyimo.maeve.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daiyimo.maeve.core.trace.TraceArchive
import com.daiyimo.maeve.record.OtlpReceipt
import com.daiyimo.maeve.record.OtlpSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class TraceSendState : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var receipt by mutableStateOf<OtlpReceipt?>(null)
        private set
    var failed by mutableStateOf(false)
        private set

    fun send(archive: TraceArchive, address: String, name: String, value: String) {
        if (busy) return
        busy = true
        receipt = null
        failed = false
        viewModelScope.launch {
            try { receipt = OtlpSender.send(archive, address, name, value) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
}
