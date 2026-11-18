# StockSim executed test inventory

November 18, 2026. Final Maven results: 251 PASS, 0 failures, 0 errors, 0 skipped; 38 suites.

## Maven suites

| Suite | Tests | Status |
|---|---:|---|
| JpaHoldingsAdapterTest | 10 | PASS |
| JpaWalletAdapterTest | 10 | PASS |
| JpaWalletTopUpAdapterTest | 4 | PASS |
| AssistantControllerAndAgentIntegrationTest | 4 | PASS |
| AssistantClientAndMemoryUnitTest | 8 | PASS |
| AssistantToolsIntegrationTest | 5 | PASS |
| SecurityPolicyRegressionTest | 3 | PASS |
| AuthControllerTest | 6 | PASS |
| EmailAuthIntegrationTest | 2 | PASS |
| EmailVerificationRequiredIntegrationTest | 1 | PASS |
| HealthControllerTest | 1 | PASS |
| OrderControllerTest | 13 | PASS |
| OrderHttpAutoExecutionIntegrationTest | 4 | PASS |
| PaymentTopUpIntegrationTest | 8 | PASS |
| PortfolioControllerTest | 5 | PASS |
| ProfileAndPublicIdIntegrationTest | 6 | PASS |
| ReportIntegrationTest | 3 | PASS |
| StockControllerTest | 6 | PASS |
| TradeControllerTest | 10 | PASS |
| WalletControllerTest | 10 | PASS |
| GlobalExceptionHandlerTest | 5 | PASS |
| HoldingTest | 21 | PASS |
| HoldingRepositoryTest | 4 | PASS |
| CsvWriterTest | 4 | PASS |
| OrderAutoExecutionTest | 5 | PASS |
| OrderExecutionServiceTest | 9 | PASS |
| OrderExecutionWithoutAdaptersTest | 2 | PASS |
| OrderServiceTest | 11 | PASS |
| PdfReportBuilderTest | 2 | PASS |
| PortfolioServiceIntegrationTest | 7 | PASS |
| PortfolioServiceUnitTest | 12 | PASS |
| PublicUserIdGeneratorTest | 3 | PASS |
| StockServiceTest | 16 | PASS |
| TradeSettlementIntegrationTest | 11 | PASS |
| UserServiceTest | 2 | PASS |
| WalletServiceTest | 5 | PASS |
| WalletTransactionServiceTest | 12 | PASS |
| StockTradingSimulatorApplicationTests | 1 | PASS |

## Individual Maven cases

### com.stocktrading.simulator.adapter.JpaHoldingsAdapterTest

- **PASS** optimisticLocking_DetectsConcurrentModification
- **PASS** deductAvailableShares_InsufficientShares_ThrowsException
- **PASS** priceBoundaryAlignment_AcceptsMaxDecimal12_2AndRejectsAbove
- **PASS** addShares_ValidBuy_CreatesHolding
- **PASS** addShares_WithoutActiveTransaction_ThrowsException
- **PASS** addShares_MultipleBuys_UpdatesWac
- **PASS** deductAvailableShares_ValidSell_DecreasesSharesAndCalculatesRealizedPnl
- **PASS** deductAvailableShares_WithoutActiveTransaction_ThrowsException
- **PASS** parameterValidation_RejectsInvalidInputs
- **PASS** deductAvailableShares_NoHolding_ThrowsException

### com.stocktrading.simulator.adapter.JpaWalletAdapterTest

- **PASS** operations_WhenUserHasNoWallet_ThrowsIllegalStateException
- **PASS** walletOwnershipIsolation_DebitOnOneUserDoesNotAffectAnother
- **PASS** debitSufficientFunds_InsufficientFunds_ThrowsInsufficientFundsException_AndRollsBack
- **PASS** debitAndCredit_CalledWithoutActiveTransaction_ThrowsIllegalTransactionStateException
- **PASS** monetaryValidation_RejectsInvalidAmountsAndOverflows
- **PASS** creditFunds_ResultingBalanceExceedsMaxDecimal15_ThrowsIllegalStateException
- **PASS** optimisticLocking_DetectsConcurrentWalletModifications
- **PASS** duplicateOrderReferenceId_ThrowsDataIntegrityViolation_AndPreventsDoubleOperation
- **PASS** creditFunds_ValidSellOrder_IncreasesBalanceAndRecordsTradeSellLedger
- **PASS** debitSufficientFunds_ValidBuyOrder_DecreasesBalanceAndRecordsTradeBuyLedger

### com.stocktrading.simulator.adapter.JpaWalletTopUpAdapterTest

- **PASS** creditTopUp_WithoutTransaction_ThrowsIllegalTransactionStateException
- **PASS** creditTopUp_DuplicateReference_RollsBackWalletCredit
- **PASS** creditTopUp_ExceedsMaxWalletDecimal_ThrowsIllegalStateAndRollsBack
- **PASS** creditTopUp_ValidTransaction_CreditsWalletAndCreatesTopUpLedgerEntry

### com.stocktrading.simulator.assistant.controller.AssistantControllerAndAgentIntegrationTest

- **PASS** conversationIsolationBetweenUsers
- **PASS** providerErrorClassificationAndToolLoopCap
- **PASS** promptInjectionCannotMutateState
- **PASS** securityAndDisabledBehavior

### com.stocktrading.simulator.assistant.service.AssistantClientAndMemoryUnitTest

- **PASS** oversizedSingleToolCallBlockAndSingleMessageStillRespectBounds
- **PASS** tradingAgentBoundsExcessiveToolCallsAndTotalToolResultSize
- **PASS** llmClientErrorClassification
- **PASS** concurrentRequestsOnSameConversationIdDoNotInterleaveAndPreserveCrossUserIsolation
- **PASS** conversationTrimmingNeverLeavesPartialOrOrphanedToolCallSequences
- **PASS** syntheticInternalExceptionSentinelNeverLeaksFromToolsOrRegistry
- **PASS** propertiesValidationAndMasking
- **PASS** conversationBoundsAndOwnership

### com.stocktrading.simulator.assistant.tool.AssistantToolsIntegrationTest

- **PASS** rejectsInvalidArgumentsAndMalformedJson
- **PASS** allSixToolsWorkAgainstCanonicalServicesWithStrictUserIsolation
- **PASS** orderHistoryPaginationHandlesIntegerOverflowAndLargePagesSafely
- **PASS** toolRegistryContainsOnlySixReadOnlyTools
- **PASS** previewTradeProposalIsStrictlyReadOnlyAndNeverMutatesState

### com.stocktrading.simulator.config.SecurityPolicyRegressionTest

- **PASS** unknownAndFutureRoutesDefaultToAuthenticated
- **PASS** publicRoutesRemainAccessible
- **PASS** protectedApiEndpointsRequireAuthentication

### com.stocktrading.simulator.controller.AuthControllerTest

- **PASS** login_InvalidCredentials_Fails
- **PASS** register_DuplicateUsername_ReturnsBadRequest
- **PASS** dashboard_Authenticated_ReturnsLiveBalanceAndTracksSubsequentChanges
- **PASS** login_ValidCredentials_Success
- **PASS** register_ValidRequest_Success
- **PASS** dashboard_Unauthenticated_ReturnsUnauthorized

### com.stocktrading.simulator.controller.EmailAuthIntegrationTest

- **PASS** registrationSendsVerificationEmailAndTokenIsSingleUse
- **PASS** passwordResetTokenExpirySingleUseAndCrossTypeProtection

### com.stocktrading.simulator.controller.EmailVerificationRequiredIntegrationTest

- **PASS** enforcesVerificationOnlyForUnverifiedNewUsers

### com.stocktrading.simulator.controller.HealthControllerTest

- **PASS** healthEndpointReturnsUp

### com.stocktrading.simulator.controller.OrderControllerTest

- **PASS** getOrderById_NotOwnedByUser_Returns404WithoutLeaking
- **PASS** placeSellOrder_ValidRequest_Returns201Created
- **PASS** placeOrder_DuplicateClientOrderId_Returns409Conflict
- **PASS** placeBuyOrder_ValidRequest_Returns201Created
- **PASS** placeBuyOrder_MissingCsrf_Returns403
- **PASS** placeOrder_InvalidClientOrderIdFormat_Returns400BadRequest
- **PASS** placeOrder_UnknownSymbol_Returns404NotFound
- **PASS** placeSellOrder_InsufficientHoldingException_Returns422UnprocessableEntity
- **PASS** placeOrder_InvalidQuantity_Returns400BadRequest
- **PASS** getUserOrders_ReturnsOnlyUserOrders
- **PASS** unauthenticated_OrderEndpoints_Return401
- **PASS** placeOrder_WithoutCsrf_Returns403Forbidden
- **PASS** placeOrder_UnrelatedDataIntegrityViolation_Returns500WithoutSqlDetails

### com.stocktrading.simulator.controller.OrderHttpAutoExecutionIntegrationTest

- **PASS** httpBuyOrder_InsufficientFunds_LeavesOrderPendingAndRollsBackSettlement
- **PASS** httpSellOrder_InsufficientShares_LeavesOrderPendingAndRollsBackSettlement
- **PASS** httpBuyOrder_AutoExecutesCompletely
- **PASS** httpSellOrder_AutoExecutesReverseFlowWithRealizedPnl

### com.stocktrading.simulator.controller.PaymentTopUpIntegrationTest

- **PASS** retryDeclinedTopUpWithSameKeyReturnsFailedAndCreditsNothing
- **PASS** declinedTopUpCreditsNothing
- **PASS** missingWalletTopUpPortFailsClosed
- **PASS** repeatedIdempotencyKeyCreditsOnceAndRejectsMismatchedAmount
- **PASS** successfulTopUpCreditsPersistentWalletAndLedger
- **PASS** concurrentTopUpSameKeyCreditsOnce
- **PASS** walletMutationFailureRollsBackPaymentStatusToPending
- **PASS** authCsrfAndUserScopingEnforced

### com.stocktrading.simulator.controller.PortfolioControllerTest

- **PASS** getPortfolio_ClosedPositions_FilteredCorrectly
- **PASS** getPortfolio_Authenticated_ReturnsOk
- **PASS** getPortfolio_ActivePositions_DefaultExcludeClosed
- **PASS** getPortfolio_UserIsolation_DoesNotLeakOtherUsersHoldings
- **PASS** getPortfolio_Unauthenticated_ReturnsUnauthorized

### com.stocktrading.simulator.controller.ProfileAndPublicIdIntegrationTest

- **PASS** registerCreatesUserPublicIdWalletAndInitialLedger
- **PASS** updateProfileReturnsRealWalletBalanceAndProtectsImmutableFields
- **PASS** changePasswordRequiresCurrentPassword
- **PASS** backfillFillsOnlyMissingIdsAndDatabaseEnforcesUniqueness
- **PASS** backfillRetriesOnCollisionAndIdentifiesUsersWithoutWallet
- **PASS** profileEndpointsRequireAuthAndCsrf

### com.stocktrading.simulator.controller.ReportIntegrationTest

- **PASS** tradingHistoryAndPortfolioReportsContainRealUserRecordsOnly
- **PASS** reportEndpointsRequireAuthenticationAndValidFormat
- **PASS** profileAndTransactionsReportsSecurityAndIsolation

### com.stocktrading.simulator.controller.StockControllerTest

- **PASS** getStockBySymbol_NotFound_Returns404
- **PASS** security_StocksPublic_UserMeStillProtected
- **PASS** getStockHistory_ReturnsChronologicalPrices
- **PASS** getStockBySymbol_Found_ReturnsStock
- **PASS** searchStocks_MatchesQuery
- **PASS** getAllStocks_ReturnsListWithSimulatedLabel

### com.stocktrading.simulator.controller.TradeControllerTest

- **PASS** getUserTrades_UserIsolationPreserved
- **PASS** getUserTrades_EmptyHistory_ReturnsEmptyPage
- **PASS** getUserTrades_NegativePageAndInvalidSize_HandledSafely
- **PASS** getTradeById_OwnedByUser_ReturnsTrade
- **PASS** getUserTrades_ValidCustomSort_OrdersCorrectly
- **PASS** getUserTrades_PageSizeCappedAt100
- **PASS** unauthenticated_TradeEndpoints_Return401
- **PASS** getUserTrades_WithTrades_ReturnsPaginatedList
- **PASS** getUserTrades_UnsafeSortField_FallsBackToExecutedAtDescSafely
- **PASS** getTradeById_NotOwnedByUser_Returns404

### com.stocktrading.simulator.controller.WalletControllerTest

- **PASS** getTransactions_InvertedDateRange_ReturnsBadRequest
- **PASS** getTransactions_Authenticated_ReturnsPaginatedHistory
- **PASS** getTransactions_ValidDateRange_ReturnsOk
- **PASS** getTransactions_EqualDates_ReturnsBadRequest
- **PASS** getWallet_Unauthenticated_ReturnsUnauthorized
- **PASS** getTransactions_FilterByTypeAndCategory_ReturnsMatchingSubset
- **PASS** getTransactions_UserIsolation_DoesNotReturnOtherUserTransactions
- **PASS** getTransactions_Unauthenticated_ReturnsUnauthorized
- **PASS** getTransactions_Pagination_RespectsPageAndSize
- **PASS** getWallet_Authenticated_ReturnsCorrectBalanceAndPayload

### com.stocktrading.simulator.exception.GlobalExceptionHandlerTest

- **PASS** handleOptimisticLocking_Returns409Conflict
- **PASS** handleIdempotencyConflict_Returns409Conflict
- **PASS** handleLockTimeout_Returns503ServiceUnavailable
- **PASS** handleInsufficientBalance_Returns400BadRequest
- **PASS** handleDeadlock_Returns503ServiceUnavailable

### com.stocktrading.simulator.model.HoldingTest

- **PASS** reopeningPosition_ResetsAverageCostPreservingPnl
- **PASS** buyPrice_Boundaries
- **PASS** sell_WithProfit
- **PASS** multipleBuys_WeightedAverageCost
- **PASS** applyBuy_AppliesCalculatedState
- **PASS** zeroAndNegativeQuantities_Rejected
- **PASS** sell_InsufficientQuantity_ThrowsException
- **PASS** failedCalculation_LeavesEntityUnchanged
- **PASS** initialHolding_ZeroValues
- **PASS** previewSell_ComputesCorrectState_WithoutMutatingOriginalHolding
- **PASS** sell_WithLoss
- **PASS** sellPrice_Boundaries
- **PASS** previewSell_InsufficientQuantity_ThrowsException
- **PASS** firstBuy_InitializesPosition
- **PASS** previewBuy_ComputesCorrectState_WithoutMutatingOriginalHolding
- **PASS** previewBuy_IntegerOverflow_ThrowsArithmeticException
- **PASS** realizedPnl_Boundaries
- **PASS** applySell_AppliesCalculatedState
- **PASS** closePosition_RetainsCumulativeRealizedPnl
- **PASS** quantityOverflow_ThrowsArithmeticException
- **PASS** constructorValidations

### com.stocktrading.simulator.repository.HoldingRepositoryTest

- **PASS** findByUserId_RetrievesAllHoldingsForUser
- **PASS** userIsolation_NoCrossUserLeakage
- **PASS** findByUserIdAndStockId_Success
- **PASS** findByUserIdAndQuantityGreaterThan_FiltersZeroQuantityPositions

### com.stocktrading.simulator.service.CsvWriterTest

- **PASS** plainValuesAreUntouched
- **PASS** writesBomHeaderAndCrlfRows
- **PASS** quotesCommasAndNewlines
- **PASS** neutralisesSpreadsheetFormulas

### com.stocktrading.simulator.service.OrderAutoExecutionTest

- **PASS** createSellOrder_WhenInsufficientShares_ThrowsExceptionAndRollsBackSettlement
- **PASS** createBuyOrder_WhenAutoExecuteEnabledAndAdaptersPresent_TransitionsToExecuted
- **PASS** createOrder_IdempotencyUnderAutoExecution_PreventsDuplicateSettlement
- **PASS** createBuyOrder_WhenInsufficientFunds_ThrowsExceptionAndRollsBackSettlement
- **PASS** createSellOrder_WhenAutoExecuteEnabledAndAdaptersPresent_TransitionsToExecuted

### com.stocktrading.simulator.service.OrderExecutionServiceTest

- **PASS** executeOrder_WhenPriceExceedsMaxPrecision_ThrowsIllegalStateException_BeforeWalletOrHoldingsMutations
- **PASS** executeOrder_InsufficientShares_ThrowsException_LeavesOrderPending
- **PASS** executeOrder_CrossUserOwnership_ThrowsResourceNotFoundException
- **PASS** executeOrder_WhenTotalAmountExceedsMaxPrecision_ThrowsIllegalStateException_BeforeMutations
- **PASS** executeOrder_InsufficientFunds_ThrowsException_LeavesOrderPending
- **PASS** executeOrder_SellOrder_SuccessWithMockedAdapters
- **PASS** trade_UniqueConstraintOnOrderId_PreventsDuplicateTrades
- **PASS** executeOrder_BuyOrder_SuccessWithMockedAdapters
- **PASS** executeOrder_WhenAlreadyExecuted_ThrowsIllegalStateException

### com.stocktrading.simulator.service.OrderExecutionWithoutAdaptersTest

- **PASS** applicationContext_StartsWithoutAdapters_AndExecutionFailsClosed
- **PASS** orderCreation_WhenAdaptersMissing_CreatesValidPendingOrderWithoutPretendingExecuted

### com.stocktrading.simulator.service.OrderServiceTest

- **PASS** createOrder_InvalidQuantity_ThrowsIllegalArgumentException
- **PASS** getUserOrders_ReturnsOnlyUserOrdersInChronologicalReverse
- **PASS** createSellOrder_ValidRequest_CreatesPendingSellOrder
- **PASS** getUserOrderById_OwnershipIsolation_ThrowsNotFoundForOtherUser
- **PASS** createOrder_UnknownSymbol_ThrowsResourceNotFoundException
- **PASS** orderEntity_LifecycleTransitions_CannotTransitionFromTerminal
- **PASS** createBuyOrder_ValidRequest_CreatesPendingOrderWithServerPrice
- **PASS** createOrder_ConcurrentDuplicateConstraintViolation_ThrowsDuplicateOrderException
- **PASS** createOrder_DuplicateClientOrderId_ThrowsDuplicateOrderException
- **PASS** createOrder_UnrelatedUniqueConstraintViolation_RethrowsDataIntegrityViolationException
- **PASS** createOrder_UnrelatedDatabaseConstraintViolation_RethrowsDataIntegrityViolationException

### com.stocktrading.simulator.service.PdfReportBuilderTest

- **PASS** unsupportedCharactersAndLongValuesDoNotBreakTheDocument
- **PASS** paginatesRepeatsHeaderAndNumbersPages

### com.stocktrading.simulator.service.PortfolioServiceIntegrationTest

- **PASS** getPortfolio_AfterCompleteLiquidation_RetainsRealizedPnl
- **PASS** getPortfolio_AfterPartialSell_ReflectsRealizedAndUnrealized
- **PASS** getPortfolio_NewUser_AccurateInitialState
- **PASS** getPortfolio_AfterBuy_ReflectsAccurateValuation
- **PASS** getPortfolio_AfterMultipleBuys_TracksWeightedAverageCost
- **PASS** getPortfolio_IncludeClosedToggle_LeavesSummaryMetricsInvariant
- **PASS** getPortfolio_CrossUserIsolation

### com.stocktrading.simulator.service.PortfolioServiceUnitTest

- **PASS** getPortfolio_ProfitablePosition_ComputesPositiveGain
- **PASS** getPortfolio_OneActiveHolding_ComputesCorrectMetrics
- **PASS** getPortfolio_AlphabeticalOrdering
- **PASS** getPortfolio_MissingWallet_ThrowsException
- **PASS** getPortfolio_EmptyPortfolio_ReturnsZeroMetricsAndEmptyList
- **PASS** getPortfolio_BigDecimalPrecisionAndRounding
- **PASS** getPortfolio_ClosedHolding_Filtering
- **PASS** getPortfolio_MissingUser_ThrowsException
- **PASS** getPortfolio_MultipleHoldings_AggregatesAccurately
- **PASS** getPortfolio_SummaryMetrics_InvariantUnderIncludeClosedToggle
- **PASS** getPortfolio_LossMakingPosition_ComputesNegativeReturn
- **PASS** getPortfolio_ZeroInvestedValue_ReturnsZeroPercentage

### com.stocktrading.simulator.service.PublicUserIdGeneratorTest

- **PASS** givesUpAfterBoundedAttempts
- **PASS** generatesSixDigitIds
- **PASS** retriesWhenCandidateIsTaken

### com.stocktrading.simulator.service.StockServiceTest

- **PASS** stockPriceSimulator_CalculateNextPrice_HandlesInvalidAndNegativeChangeFactors
- **PASS** stockPriceSimulator_CalculateNextPrice_MultiDecimalAndInvalidChangeFactor_RoundsToTwoDecimals
- **PASS** getStockHistory_BoundedLimitAndChronological
- **PASS** createStockIfAbsent_ExistingStock_IdempotentAndDoesNotOverwritePrice
- **PASS** getStockHistory_IdenticalTimestamps_DeterministicSecondarySortByIdAndChronologicalOrder
- **PASS** stockPriceSimulator_CalculateNextPrice_OverCeilingAndInvalidChangeFactor_ClampsToCeiling
- **PASS** updateStockPrice_RoundingAndBoundaryValidation
- **PASS** createStockIfAbsent_NewStock_Success
- **PASS** searchStocks_BySymbolOrCompanyName
- **PASS** updateStockPrice_NegativeOrZeroPrice_ThrowsException
- **PASS** createStockIfAbsent_RoundingAndBoundaryValidation
- **PASS** stockPriceSimulator_CalculateNextPrice_NeverExceedsCeiling
- **PASS** getStockBySymbol_UnknownSymbol_ThrowsResourceNotFoundException
- **PASS** updateStockPrice_UpdatesPreviousAndCurrentAndAppendsHistory
- **PASS** stockPriceSimulator_CalculateNextPrice_SubFloorAndInvalidChangeFactor_ClampsToFloor
- **PASS** stockPriceSimulator_CalculateNextPrice_NeverBelowFloor

### com.stocktrading.simulator.service.TradeSettlementIntegrationTest

- **PASS** concurrentBuyAndSell_RollsBackCleanlyWithoutPartialState
- **PASS** userOwnership_EnforcedOnExecution
- **PASS** concurrentBuyAndSell_ThreadSafety
- **PASS** insufficientShares_RollsBackEverything
- **PASS** portfolioValuation_UsesSimulatedPrices
- **PASS** successfulSell_SettlesAtomically
- **PASS** highCeilingPrice_AlignedAcrossEngineAndHoldings
- **PASS** duplicateSettlement_CannotMutateTwice
- **PASS** simultaneousFirstTimeBuy_EnforcesUniqueHoldingConstraintAndRollsBackFailedTransaction
- **PASS** successfulBuy_SettlesAtomically
- **PASS** insufficientWalletFunds_RollsBackEverything

### com.stocktrading.simulator.service.UserServiceTest

- **PASS** registerUser_Success_PersistsBothUserAndWalletInH2
- **PASS** registerUser_WalletCreationFailure_RollsBackUserRegistration

### com.stocktrading.simulator.service.WalletServiceTest

- **PASS** createWalletWithInitialBalance_CreatesWalletAndLedgerEntryWith100000INR
- **PASS** existingWalletBalance_IsNotResetOrModified
- **PASS** getWalletByUsername_ExistingUserWithWallet_ReturnsCorrectWallet
- **PASS** createWalletWithInitialBalance_WhenWalletAlreadyExists_ThrowsIllegalStateException
- **PASS** getWalletByUsername_UserWithoutWallet_ThrowsIllegalStateException

### com.stocktrading.simulator.service.WalletTransactionServiceTest

- **PASS** executeDebit_InsufficientBalance_ThrowsExceptionAndLeavesBalanceUnchanged
- **PASS** executeDebit_ValidRequest_DecreasesBalanceAndCreatesLedgerEntry
- **PASS** executeDebit_IdempotentConflict_DifferentAmount_ThrowsIdempotencyConflictException
- **PASS** executeDebit_MissingWallet_ThrowsIllegalStateException_DoesNotCreateWallet
- **PASS** executeDebit_IdempotentConflict_DifferentCategory_ThrowsIdempotencyConflictException
- **PASS** executeDebit_CalledInsideActiveTransaction_ThrowsIllegalStateException
- **PASS** executeCredit_ValidRequest_IncreasesBalanceAndCreatesLedgerEntry
- **PASS** concurrentDuplicateRequests_SameReferenceId_ExactlyOneLedgerEntryPersisted
- **PASS** executeCredit_IdempotentReplay_ReturnsExistingTransactionWithoutDoubleCredit
- **PASS** executeDebit_IdempotentReplay_ReturnsExistingTransactionWithoutDoubleDebit
- **PASS** getTransactionHistory_DateFiltering_RespectsHalfOpenInterval
- **PASS** concurrentDebits_CompeteForInsufficientBalance_CannotProduceNegativeBalance

### com.stocktrading.simulator.StockTradingSimulatorApplicationTests

- **PASS** contextLoads

## Browser fixture cases

| Check | Edge | Opera |
|---|---|---|
| Single profile fetch; genuine empty portfolio; no initial focus theft | PASS | PASS |
| Five refreshes finish without formatter recreation | PASS | PASS |
| Confirmed top-up refreshes portfolio promptly; programmatic double submit sends once | PASS | PASS |
| BUY cancellation sends nothing; confirmation sends one UUID and no client price | PASS | PASS |
| 422 preserves pending UUID over reload and reconciles without resubmission | PASS | PASS |
| Ambiguous network failure reuses UUID; changed order cannot discard it | PASS | PASS |
| Ambiguous top-up cannot change amount; same-amount retry preserves key | PASS | PASS |
| Fast stock selection cannot display an older chart response | PASS | PASS |
| AI output is text; rate-limit feedback recovers; chat never submits a trade | PASS | PASS |
| Failed logout keeps dashboard open and reports failure | PASS | PASS |
| New account cannot inherit another user pending top-up or order | PASS | PASS |
| Invalid trading quantities never issue a POST | PASS | PASS |
| SELL cancellation sends nothing; confirmed SELL sends once | PASS | PASS |
| Trade completion refreshes again after an older portfolio poll finishes | PASS | PASS |
| Duplicate refresh and timer tick cannot overlap a slow refresh | PASS | PASS |
| Hidden tab polling pauses; visible tab and BFCache restore polling | PASS | PASS |
| Disconnected APIs show errors and recover after reconnect | PASS | PASS |
| light 1440px: no page overflow | PASS | PASS |
| light 1280px: no page overflow | PASS | PASS |
| light 768px: no page overflow | PASS | PASS |
| light 390px: no page overflow | PASS | PASS |
| light 360px: no page overflow | PASS | PASS |
| light: axe WCAG A/AA has no violations | PASS | PASS |
| dark 1440px: no page overflow | PASS | PASS |
| dark 1280px: no page overflow | PASS | PASS |
| dark 768px: no page overflow | PASS | PASS |
| dark 390px: no page overflow | PASS | PASS |
| dark 360px: no page overflow | PASS | PASS |
| dark: axe WCAG A/AA has no violations | PASS | PASS |
| Mobile menu Escape restores focus; theme toggle persists over reload | PASS | PASS |
| No uncaught JavaScript exceptions | PASS | PASS |

## Live browser/MySQL cases

- **PASS** Real browser registration, login, session and initial wallet
- **PASS** Stock search, live chart, genuine empty portfolio and keyboard access
- **PASS** Browser top-up credits wallet and exactly one ledger/payment row
- **PASS** BUY cancellation and confirmed BUY INFY; holdings/WAC/equity reconcile with SQL
- **PASS** Confirmed SELL INFY updates realized/unrealized P&L and SQL balance
- **PASS** All eight report downloads have correct content, headers and browser delivery
- **PASS** Real HTTP CSRF, duplicates, invalid quantity, rollback, idempotent top-up and pagination
- **PASS** Authenticated cross-user isolation and unauthenticated report denial
- **PASS** Live MySQL concurrent same-key and distinct-key top-ups credit exactly once
- **PASS** Profile update and password change use authenticated session; relogin succeeds

**BLOCKED:** Live AI provider success: LLM_AUTHENTICATION_ERROR. Read-only failed-chat financial snapshot check passed.

**BLOCKED:** Firefox launch (spawn UNKNOWN); no Firefox application checks ran. WebKit/Safari not run.
