package io.horizontalsystems.bankwallet.modules.swapxxx.sendtransaction

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.horizontalsystems.bankwallet.core.App
import io.horizontalsystems.bankwallet.core.ethereum.EvmCoinServiceFactory
import io.horizontalsystems.bankwallet.modules.evmfee.EvmCommonGasDataService
import io.horizontalsystems.bankwallet.modules.evmfee.EvmFeeModule
import io.horizontalsystems.bankwallet.modules.evmfee.EvmFeeService
import io.horizontalsystems.bankwallet.modules.evmfee.GasPriceInfo
import io.horizontalsystems.bankwallet.modules.evmfee.IEvmGasPriceService
import io.horizontalsystems.bankwallet.modules.evmfee.eip1559.Eip1559GasPriceService
import io.horizontalsystems.bankwallet.modules.evmfee.legacy.LegacyGasPriceService
import io.horizontalsystems.bankwallet.modules.send.evm.settings.SendEvmFeeSettingsScreen
import io.horizontalsystems.bankwallet.modules.send.evm.settings.SendEvmNonceService
import io.horizontalsystems.bankwallet.modules.send.evm.settings.SendEvmNonceViewModel
import io.horizontalsystems.bankwallet.modules.send.evm.settings.SendEvmSettingsModule
import io.horizontalsystems.bankwallet.modules.send.evm.settings.SendEvmSettingsService
import io.horizontalsystems.bankwallet.modules.send.evm.settings.SendEvmSettingsViewModel
import io.horizontalsystems.ethereumkit.core.LegacyGasPriceProvider
import io.horizontalsystems.ethereumkit.core.eip1559.Eip1559GasPriceProvider
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.horizontalsystems.marketkit.models.BlockchainType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SendTransactionServiceEvm(blockchainType: BlockchainType) : ISendTransactionService() {
    private lateinit var transactionData: TransactionData
    private var gasLimit: Long? = null

    private val token by lazy { App.evmBlockchainManager.getBaseToken(blockchainType)!! }
    private val evmKitWrapper by lazy { App.evmBlockchainManager.getEvmKitManager(blockchainType).evmKitWrapper!! }
    private val gasPriceService: IEvmGasPriceService by lazy {
        val evmKit = evmKitWrapper.evmKit
        if (evmKit.chain.isEIP1559Supported) {
            val gasPriceProvider = Eip1559GasPriceProvider(evmKit)
            Eip1559GasPriceService(gasPriceProvider, evmKit)
        } else {
            val gasPriceProvider = LegacyGasPriceProvider(evmKit)
            LegacyGasPriceService(gasPriceProvider)
        }
    }
    private val feeService by lazy {
        val gasDataService = EvmCommonGasDataService.instance(
            evmKitWrapper.evmKit,
            evmKitWrapper.blockchainType
        )
        EvmFeeService(evmKitWrapper.evmKit, gasPriceService, gasDataService, transactionData, gasLimit)
    }
    private val coinServiceFactory by lazy {
        EvmCoinServiceFactory(
            token,
            App.marketKit,
            App.currencyManager,
            App.coinManager
        )
    }
//    private val cautionViewItemFactory by lazy { CautionViewItemFactory(coinServiceFactory.baseCoinService) }
    private val nonceService by lazy { SendEvmNonceService(evmKitWrapper.evmKit) }
    private val settingsService by lazy { SendEvmSettingsService(feeService, nonceService) }
//    private val sendService by lazy {
//        SendEvmTransactionService(
//            sendEvmData,
//            evmKitWrapper,
//            settingsService,
//            App.evmLabelManager
//        )
//    }

    private var gasPriceInfo: GasPriceInfo? = null

    override fun createState() = SendTransactionSettings.Evm(
        gasPriceInfo
    )

    override fun start(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            gasPriceService.stateFlow.collect {
                gasPriceInfo = it.dataOrNull

                emitState()
            }
        }
    }

    override fun setSendTransactionData(data: SendTransactionData) {
        check(data is SendTransactionData.Evm)

        transactionData = data.transactionData
        gasLimit = data.gasLimit
    }

    @Composable
    override fun GetContent(navController: NavController) {
        val nonceViewModel = viewModel<SendEvmNonceViewModel>(initializer = {
            SendEvmNonceViewModel(nonceService)
        })

        val baseCoinService = coinServiceFactory.baseCoinService
        val feeSettingsViewModel = viewModel<ViewModel>(
            factory = EvmFeeModule.Factory(
                feeService,
                gasPriceService,
                baseCoinService
            )
        )
        val sendSettingsViewModel = viewModel<SendEvmSettingsViewModel>(
            factory = SendEvmSettingsModule.Factory(settingsService, baseCoinService)
        )
        SendEvmFeeSettingsScreen(
            viewModel = sendSettingsViewModel,
            feeSettingsViewModel = feeSettingsViewModel,
            nonceViewModel = nonceViewModel,
            navController = navController
        )
    }
}
