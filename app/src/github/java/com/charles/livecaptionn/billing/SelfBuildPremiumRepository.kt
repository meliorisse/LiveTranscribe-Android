package com.charles.livecaptionn.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local, permanent access for this fork's self-built APKs. No billing backend is used. */
class SelfBuildPremiumRepository : PremiumRepository {
    private val included = PremiumState(entitlements = setOf(Entitlement.PRO))
    override val state = MutableStateFlow(included).asStateFlow()
    override val supportsEmailRestore = false

    override suspend fun refresh(sessionId: String?): Result<PremiumState> = Result.success(included)

    override suspend fun restore(email: String?): Result<PremiumState> = Result.success(included)

    override suspend fun purchase(
        activity: Activity,
        product: PremiumProduct,
        email: String?
    ): PurchaseFlowResult = PurchaseFlowResult.Failed("All features are already included.")

    override suspend fun openManageSubscription(activity: Activity): ManageAction =
        ManageAction.Failed("All features are already included.")
}
