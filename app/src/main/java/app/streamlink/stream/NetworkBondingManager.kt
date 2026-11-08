package app.streamlink.stream

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.streamlink.data.NetworkBondingSettings

/**
 * PT: Tipo de ligação atualmente escolhida pelo failover automático.
 * EN: Connection type currently selected by the automatic failover.
 */
enum class BondingNetworkType {
    NONE,
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER
}

/**
 * PT: Estado do failover para mostrar na interface e para decidir quando
 * PT: reiniciar o encoder depois de mudar de rede.
 *
 * EN: Failover state used by the UI and to decide when the encoder must be
 * EN: restarted after a network change.
 */
data class NetworkBondingState(
    val enabled: Boolean = false,
    val networkType: BondingNetworkType = BondingNetworkType.NONE,
    val wifiAvailable: Boolean = false,
    val cellularAvailable: Boolean = false,
    /**
     * PT: `true` quando a rede ativa é a de reserva (normalmente dados móveis).
     * EN: `true` when the active network is the backup one (usually mobile data).
     */
    val switchedToBackup: Boolean = false,
    /**
     * PT: Incrementa sempre que a rede ativa muda. O encoder usa este valor
     * PT: para não reiniciar em atualizações de estado sem mudança de rede.
     * EN: Increments whenever the active network changes. The encoder uses it
     * EN: so it does not restart on state updates that keep the same network.
     */
    val generation: Long = 0L
)

/**
 * PT: Gere o failover automático entre Wi-Fi e dados móveis usando o
 * PT: `ConnectivityManager`. A app liga o processo inteiro à rede escolhida
 * PT: (`bindProcessToNetwork`), pelo que as sockets abertas pelo RootEncoder
 * PT: e pelo WebRTC usam sempre a ligação ativa.
 *
 * PT: Isto não agrega os dois links em simultâneo (isso exigiria SRTLA e um
 * PT: relay compatível); garante é que a transmissão continua quando o link
 * PT: principal falha.
 *
 * EN: Manages automatic failover between Wi-Fi and mobile data through
 * EN: `ConnectivityManager`. The whole process is bound to the chosen network
 * EN: (`bindProcessToNetwork`), so sockets opened by RootEncoder and WebRTC
 * EN: always use the active link.
 *
 * EN: This does not aggregate both links at the same time (that would require
 * EN: SRTLA and a compatible relay); it keeps the stream alive when the primary
 * EN: link fails.
 */
class NetworkBondingManager(context: Context) {

    private val appContext = context.applicationContext
    private val connectivity =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val networks = LinkedHashMap<Network, NetworkEntry>()
    private var settings = NetworkBondingSettings()
    private var registered = false
    private var appliedNetwork: Network? = null
    private var appliedType = BondingNetworkType.NONE
    @Volatile private var generation = 0L
    private var lastState = NetworkBondingState()
    private var watchdogStarted = false

    /**
     * PT: Algumas ROMs deixam de entregar `onLost` enquanto o processo está
     * PT: ligado a uma rede que desaparece. Esta verificação periódica garante
     * PT: que o failover acontece mesmo nesse caso.
     * EN: Some ROMs stop delivering `onLost` while the process is bound to a
     * EN: network that disappears. This periodic check guarantees that failover
     * EN: still happens in that case.
     */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!registered) return
            rescanAndEvaluate()
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    /**
     * PT: Chamado na main thread sempre que a rede ativa muda (ou quando o
     * PT: estado de disponibilidade muda pela primeira vez).
     * EN: Called on the main thread whenever the active network changes (or when
     * EN: the availability state changes for the first time).
     */
    var onChanged: ((NetworkBondingState) -> Unit)? = null

    /**
     * PT: Geração atual da rede ativa (0 = ainda sem mudança de failover).
     * EN: Current generation of the active network (0 = no failover change yet).
     */
    val currentGeneration: Long
        get() = generation

    private data class NetworkEntry(
        val type: BondingNetworkType,
        val validated: Boolean
    )

    /**
     * PT: Reexamina todas as redes sempre que a conectividade muda. Em vez de
     * PT: confiar só no `onLost` de cada transporte (pouco fiável em algumas
     * PT: ROMs), reconstrói a lista a partir do sistema e volta a escolher.
     * EN: Re-checks every network whenever connectivity changes. Instead of
     * EN: relying only on each transport's `onLost` (unreliable on some ROMs),
     * EN: it rebuilds the list from the system and chooses again.
     */
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            rescanAndEvaluate()
        }

        override fun onLost(network: Network) {
            rescanAndEvaluate()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            rescanAndEvaluate()
        }
    }

    /**
     * PT: Callback da rede predefinida. Ajuda quando a ROM não entrega os
     * PT: callbacks por transporte ao desligar o Wi-Fi.
     * EN: Default-network callback. Helps when the ROM does not deliver the
     * EN: per-transport callbacks when Wi-Fi is turned off.
     */
    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            rescanAndEvaluate()
        }

        override fun onLost(network: Network) {
            rescanAndEvaluate()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            rescanAndEvaluate()
        }
    }

    /**
     * PT: Regista os callbacks e aplica já a rede preferida. Pode ser chamado
     * PT: mais de uma vez; só a primeira registra callbacks.
     * EN: Registers the callbacks and applies the preferred network immediately.
     * EN: Safe to call repeatedly; only the first call registers callbacks.
     */
    @Synchronized
    fun start(newSettings: NetworkBondingSettings) {
        settings = newSettings
        if (!registered) {
            val manager = connectivity
            if (manager == null) {
                Log.w(TAG, "ConnectivityManager indisponível")
                return
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            runCatching {
                manager.registerNetworkCallback(request, callback)
                manager.registerDefaultNetworkCallback(defaultCallback)
                registered = true
            }.onFailure { Log.w(TAG, "Falha ao monitorizar redes", it) }
            rescanAndEvaluate()
        }
        evaluate(notify = true, force = true)
        if (!watchdogStarted) {
            watchdogStarted = true
            mainHandler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
        }
    }

    /**
     * PT: Atualiza as preferências sem voltar a registar callbacks.
     * EN: Updates the preferences without registering callbacks again.
     */
    @Synchronized
    fun update(newSettings: NetworkBondingSettings) {
        if (newSettings == settings) return
        settings = newSettings
        evaluate(notify = true, force = true)
    }

    /**
     * PT: Liberta o binding e os callbacks (fim da transmissão/da app).
     * EN: Releases the binding and callbacks (end of stream/app).
     */
    @Synchronized
    fun stop() {
        mainHandler.removeCallbacks(watchdog)
        watchdogStarted = false
        val manager = connectivity
        if (registered && manager != null) {
            runCatching { manager.unregisterNetworkCallback(callback) }
            runCatching { manager.unregisterNetworkCallback(defaultCallback) }
        }
        registered = false
        networks.clear()
        runCatching { manager?.bindProcessToNetwork(null) }
        appliedNetwork = null
        appliedType = BondingNetworkType.NONE
        lastState = NetworkBondingState()
    }

    @Synchronized
    private fun rescanAndEvaluate() {
        networks.clear()
        connectivity?.let { syncCurrentNetworks(it) }
        evaluate(notify = true)
    }

    private fun syncCurrentNetworks(manager: ConnectivityManager) {
        runCatching {
            manager.allNetworks.forEach { network ->
                val capabilities = manager.getNetworkCapabilities(network)
                if (capabilities != null) {
                    storeNetwork(network, capabilities)
                }
            }
        }.onFailure { Log.w(TAG, "Falha ao listar redes", it) }
    }

    @Synchronized
    private fun storeNetwork(network: Network, capabilities: NetworkCapabilities) {
        val type = networkType(capabilities)
        if (type == BondingNetworkType.OTHER) {
            // PT: VPN/loopback não interessam ao failover Wi-Fi/móvel.
            // EN: VPN/loopback are not relevant for Wi-Fi/mobile failover.
            networks.remove(network)
            return
        }
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        networks[network] = NetworkEntry(type, validated)
    }

    private fun networkType(capabilities: NetworkCapabilities): BondingNetworkType =
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                BondingNetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                BondingNetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                BondingNetworkType.ETHERNET
            else -> BondingNetworkType.OTHER
        }

    private fun bestNetwork(type: BondingNetworkType): Network? =
        networks.entries.firstOrNull { it.value.type == type && it.value.validated }?.key

    /**
     * PT: Escolhe e aplica a rede ativa. Quando o failover está desligado, o
     * PT: processo volta à rede predefinida do sistema.
     * EN: Chooses and applies the active network. When failover is disabled, the
     * EN: process returns to the system default network.
     */
    @Synchronized
    private fun evaluate(notify: Boolean, force: Boolean = false) {
        val preferredType = if (settings.preferWifi) {
            BondingNetworkType.WIFI
        } else {
            BondingNetworkType.CELLULAR
        }
        val backupType = if (preferredType == BondingNetworkType.WIFI) {
            BondingNetworkType.CELLULAR
        } else {
            BondingNetworkType.WIFI
        }
        val wifiAvailable = networks.values.any { it.type == BondingNetworkType.WIFI }
        val cellularAvailable = networks.values.any { it.type == BondingNetworkType.CELLULAR }

        val preferred = bestNetwork(preferredType)
        val backup = bestNetwork(backupType)

        var chosen: Network? = null
        var chosenType = BondingNetworkType.NONE
        if (settings.enabled) {
            val keepBackup = !settings.autoSwitchBack &&
                appliedType == backupType &&
                backup != null
            when {
                keepBackup -> {
                    chosen = backup
                    chosenType = backupType
                }
                preferred != null -> {
                    chosen = preferred
                    chosenType = preferredType
                }
                backup != null -> {
                    chosen = backup
                    chosenType = backupType
                }
                appliedNetwork != null && networks.containsKey(appliedNetwork) -> {
                    // PT: Nenhuma rede validada ainda: mantém a ligação atual para
                    // PT: não cortar a transmissão durante a validação.
                    // EN: No validated network yet: keep the current link so the
                    // EN: stream is not cut while validation finishes.
                    chosen = appliedNetwork
                    chosenType = appliedType
                }
            }
        }

        val changed = chosen != appliedNetwork || chosenType != appliedType
        if (changed || force) {
            runCatching { connectivity?.bindProcessToNetwork(chosen) }
                .onFailure { Log.w(TAG, "Falha ao ligar o processo à rede", it) }
            appliedNetwork = chosen
            appliedType = chosenType
        }
        if (changed) generation++
        if (changed && settings.enabled) {
            Log.i(
                TAG,
                "Rede ativa: $chosenType (Wi-Fi=$wifiAvailable, móvel=$cellularAvailable)"
            )
        }

        val state = NetworkBondingState(
            enabled = settings.enabled,
            networkType = chosenType,
            wifiAvailable = wifiAvailable,
            cellularAvailable = cellularAvailable,
            switchedToBackup = settings.enabled && chosen != null && chosenType == backupType,
            generation = generation
        )
        lastState = state
        if (notify && (changed || force)) {
            mainHandler.post { onChanged?.invoke(lastState) }
        }
    }

    private companion object {
        const val TAG = "NetworkBonding"
        // PT: Intervalo da verificação de segurança (ROMs que retêm callbacks).
        // EN: Safety-check interval (ROMs that withhold callbacks).
        const val WATCHDOG_INTERVAL_MS = 2_500L
    }
}
