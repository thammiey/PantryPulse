package com.pantrypulse.app

import android.content.Context
import com.pantrypulse.app.data.community.CommunityApi
import com.pantrypulse.app.data.community.CommunityRepository
import com.pantrypulse.app.data.community.DeviceLocationProvider
import com.pantrypulse.app.data.community.HttpCommunityApi
import com.pantrypulse.app.data.community.LocationProvider
import com.pantrypulse.app.data.db.AppDatabase
import com.pantrypulse.app.data.remote.FoodRescueClient
import com.pantrypulse.app.data.remote.MealDbClient
import com.pantrypulse.app.data.remote.OpenFoodFactsClient
import com.pantrypulse.app.data.repo.AuthRepository
import com.pantrypulse.app.data.repo.HouseholdRepository
import com.pantrypulse.app.data.repo.PantryRepository
import com.pantrypulse.app.data.repo.SessionStore
import com.pantrypulse.app.data.repo.ShoppingRepository

/** Simple manual dependency container shared by the whole app. */
class AppContainer(
    context: Context,
    val db: AppDatabase = AppDatabase.build(context),
    communityApi: CommunityApi = HttpCommunityApi(),
    val location: LocationProvider = DeviceLocationProvider(),
) {
    val session = SessionStore(context)
    val households = HouseholdRepository(db, session)
    val auth = AuthRepository(db, session, households)
    val pantry = PantryRepository(db)
    val shopping = ShoppingRepository(db)
    val openFoodFacts = OpenFoodFactsClient()
    val productUse = com.pantrypulse.app.data.remote.ProductUseClient()
    val mealDb = MealDbClient()
    val foodRescue = FoodRescueClient()
    val community = CommunityRepository(context, communityApi, session, db)
}
