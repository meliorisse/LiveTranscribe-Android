package com.charles.livecaptionn.di

import android.content.Context
import com.charles.livecaptionn.billing.PremiumRepository
import com.charles.livecaptionn.billing.SelfBuildPremiumRepository
import com.charles.livecaptionn.data.PremiumLocalStore
import kotlinx.coroutines.CoroutineScope

/** github-flavor counterpart to the playstore flavor's PlayBillingRepository factory. */
fun createPremiumRepository(
    context: Context,
    store: PremiumLocalStore,
    scope: CoroutineScope
): PremiumRepository = SelfBuildPremiumRepository()
