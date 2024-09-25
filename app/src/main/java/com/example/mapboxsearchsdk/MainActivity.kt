package com.example.mapboxsearchsdk

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebView.findAddress
import android.widget.EditText
import android.widget.TextView

//import android.widget.SearchView
import androidx.appcompat.widget.SearchView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.mapboxsearchsdk.databinding.ActivityMainBinding
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.search.*
import com.mapbox.search.autocomplete.PlaceAutocomplete
import com.mapbox.search.autofill.*
import com.mapbox.search.common.AsyncOperationTask
import com.mapbox.search.common.CompletionCallback
import com.mapbox.search.offline.OfflineResponseInfo
import com.mapbox.search.offline.OfflineSearchEngine
import com.mapbox.search.offline.OfflineSearchEngineSettings
import com.mapbox.search.offline.OfflineSearchResult
import com.mapbox.search.record.*
import com.mapbox.search.result.SearchAddress
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchResultType
import com.mapbox.search.result.SearchSuggestion
import com.mapbox.search.ui.adapter.autofill.AddressAutofillUiAdapter
import com.mapbox.search.ui.adapter.engines.SearchEngineUiAdapter
import com.mapbox.search.ui.view.*
import com.mapbox.search.ui.view.place.SearchPlace
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.*


class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    private var ignoreNextMapIdleEvent: Boolean = false
    private var ignoreNextQueryTextUpdate: Boolean = false

    private lateinit var searchView: SearchView
    private lateinit var searchResultsView: SearchResultsView
    private lateinit var searchEngineUiAdapter: SearchEngineUiAdapter

    private lateinit var queryEditInputText: EditText
    private lateinit var fullAddress: TextView
    private lateinit var addressAutofillUiAdapter: AddressAutofillUiAdapter

    private lateinit var mapView: MapView
    private lateinit var mapPin: View
    private lateinit var mapboxMap: MapboxMap


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        binding = ActivityMainBinding.inflate(layoutInflater)
        searchResultsView = binding.searchResultsView2
        queryEditInputText = binding.searcET
        fullAddress = binding.fulladdress


//        searchResultsView = binding.searchResultsView2.apply {
//            initialize(
//                SearchResultsView.Configuration(CommonSearchViewConfiguration(DistanceUnitType.IMPERIAL))
//            )
////            isVisible = false
//        }

        searchResultsView.initialize(
            SearchResultsView.Configuration(
                CommonSearchViewConfiguration(DistanceUnitType.IMPERIAL)
            )
        )

        queryEditInputText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val address = queryEditInputText.text.toString().trim()
                if (address.isNotEmpty()) {
                    geocodeAddress(address)
//                    showSearchHistory()
                    showSearchResult()
                    // Возвращаем true, чтобы указать, что событие обработано
                    return@setOnEditorActionListener true
                } else {
                    binding.searcET.error = "Введите адрес"
                    // Возвращаем false, чтобы указать, что событие не было обработано
                    return@setOnEditorActionListener false
                }
            }
            // Если действие не IME_ACTION_DONE, возвращаем false
            false
        }

        setContentView(binding.root)
    }

    private fun showSearchHistory() {
        val historyDataProvider = ServiceProvider.INSTANCE.historyDataProvider()

        // Show `loading` item that indicates the progress of `search history` loading operation.
        searchResultsView.setAdapterItems(listOf(SearchResultAdapterItem.Loading))

        // Load `search history`
        val loadingTask = historyDataProvider.getAll(object : CompletionCallback<List<HistoryRecord>> {
            override fun onComplete(result: List<HistoryRecord>) {
                val viewItems = mutableListOf<SearchResultAdapterItem>().apply {
                    // Add `Recent searches` header
                    add(SearchResultAdapterItem.RecentSearchesHeader)

                    // Add history record items
                    addAll(result.map { history ->
                        SearchResultAdapterItem.History(
                            history,
                            isFavorite = false
                        )
                    })
                }

                // Show prepared items
                searchResultsView.setAdapterItems(viewItems)
            }

            override fun onError(e: Exception) {
                // Show error in case of failure
                val errorItem = SearchResultAdapterItem.Error(UiError.createFromException(e))
                searchResultsView.setAdapterItems(listOf(errorItem))
            }
        })
    }
    private val favoritesDataProvider = ServiceProvider.INSTANCE.favoritesDataProvider()
    private lateinit var task: AsyncOperationTask
    private val retrieveFavoritesCallback: CompletionCallback<List<FavoriteRecord>> =
        object : CompletionCallback<List<FavoriteRecord>> {
            override fun onComplete(result: List<FavoriteRecord>) {
                Log.i("SearchApiExample", "Favorite records: $result")
            }

            override fun onError(e: Exception) {
                Log.i("SearchApiExample", "Unable to retrieve favorite records", e)
            }
        }
    private val addFavoriteCallback: CompletionCallback<Unit> = object : CompletionCallback<Unit> {
        override fun onComplete(result: Unit) {
            Log.i("SearchApiExample", "Favorite record added")
            task = favoritesDataProvider.getAll(retrieveFavoritesCallback)
        }

        override fun onError(e: Exception) {
            Log.i("SearchApiExample", "Unable to add a new favorite record", e)
        }
    }
    private val onDataChangedListener: LocalDataProvider.OnDataChangedListener<FavoriteRecord> =
        object : LocalDataProvider.OnDataChangedListener<FavoriteRecord> {
            override fun onDataChanged(newData: List<FavoriteRecord>) {
                Log.i("SearchApiExample", "Favorites data changed. New data: $newData")
            }
        }
    override fun onDestroy() {
        favoritesDataProvider.removeOnDataChangedListener(onDataChangedListener)
        task.cancel()
        super.onDestroy()
    }

    private fun showSearchResult() {
        favoritesDataProvider.addOnDataChangedListener(onDataChangedListener)

        val newFavorite = FavoriteRecord(
            id = UUID.randomUUID().toString(),
            name = "Paris Eiffel Tower",
            descriptionText = "Eiffel Tower, Paris, France",
            address = SearchAddress(place = "Paris", country = "France"),
            routablePoints = null,
            categories = null,
            makiIcon = null,
            coordinate = Point.fromLngLat(2.294434, 48.858349),
            type = SearchResultType.PLACE,
            metadata = null
        )

        task = favoritesDataProvider.upsert(newFavorite, addFavoriteCallback)


        val placeAutocomplete = PlaceAutocomplete.create(
            accessToken = getString(R.string.mapbox_access_token),
        )

        lifecycleScope.launch {
            val response = placeAutocomplete.suggestions(
                query = "Washington DC",
            )

            if (response.isValue) {
                val suggestions = requireNotNull(response.value)

                Log.i("SearchApiExample", "Place Autocomplete suggestions: $suggestions")

                if (suggestions.isNotEmpty()) {
                    // Supposing that a user has selected (clicked in UI) the first suggestion
                    val selectedSuggestion = suggestions.first()

                    Log.i("SearchApiExample", "Selecting first suggestion...")

                    val selectionResponse = placeAutocomplete.select(selectedSuggestion)
                    selectionResponse.onValue { result ->
                        Log.i("SearchApiExample", "Place Autocomplete result: $result")
                    }.onError { e ->
                        Log.i("SearchApiExample", "An error occurred during selection", e)
                    }
                }
            } else {
                Log.i("SearchApiExample", "Place Autocomplete error", response.error)
            }
        }
    }




    private fun geocodeAddress(address: String) {
//        val accessToken = "YOUR_MAPBOX_ACCESS_TOKEN" // Замените YOUR_MAPBOX_ACCESS_TOKEN на свой ключ доступа Mapbox

            val accessToken = getString(R.string.mapbox_access_token)

            val geocodeUrl =
                "https://api.mapbox.com/geocoding/v5/mapbox.places/$address.json?country=pl&proximity=ip&language=pl&access_token=$accessToken"

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(geocodeUrl)
                .build()

            val addressAutofill = AddressAutofill.create(accessToken)

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed to execute request: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    response.close()

                    try {
                        val jsonObject = responseBody?.let { JSONObject(it) }
                        val features = jsonObject?.getJSONArray("features")
                        val results = mutableListOf<String>()


                        if (features != null) {
                            for (i in 0 until features.length()) {
                                val feature = features.getJSONObject(i)
                                val placeName = feature.getString("place_name")
                                results.add(placeName)
                            }
                        }

//                    this@MainActivity.runOnUiThread {
//                        // Очищаем предыдущие результаты
//                        binding.searchResultsTextView.text = ""
//                        // Выводим новые результаты
//                        for (result in results) {
//                            binding.searchResultsTextView.append("$result\n")
//                        }
//                    }

//                        handleGeocodingResults(results)


                        this@MainActivity.runOnUiThread {

//           search adapter
//                                val searchEngine =
//                                    SearchEngine.createSearchEngineWithBuiltInDataProviders(
//                                        apiType = ApiType.GEOCODING,
//                                        settings = SearchEngineSettings(accessToken)
//                                    )
//
//                                val offlineSearchEngine = OfflineSearchEngine.create(
//                                    OfflineSearchEngineSettings(accessToken)
//                                )
//
//                                val searchEngineUiAdapter = SearchEngineUiAdapter(
//                                    view = searchResultsView,
//                                    searchEngine = searchEngine,
//                                    offlineSearchEngine = offlineSearchEngine,
//                                )
//
////                                searchEngineUiAdapter.searchMode = SearchMode.AUTO
////                                  searchEngineUiAdapter.addSearchListener(object :
////                                  SearchEngineUiAdapter.SearchListener {
//
//
//                                searchEngineUiAdapter.addSearchListener(object :
//                                    SearchEngineUiAdapter.SearchListener {
//
//                                    private fun showToast(message: String) {
//                                        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
//                                    }
//
//                                    override fun onSuggestionsShown(
//                                        suggestions: List<SearchSuggestion>,
//                                        responseInfo: ResponseInfo
//                                    ) {
//                                        // not implemented
//                                    }
////                                    override fun onCategoryResultsShown(
////                                        suggestion: SearchSuggestion,
////                                        results: List<SearchResult>,
////                                        responseInfo: ResponseInfo
////                                    ) {
////                                        // not implemented
////                                    }
//
//                                    override fun onSearchResultsShown(
//                                        suggestion: SearchSuggestion,
//                                        results: List<SearchResult>,
//                                        responseInfo: ResponseInfo
//                                    ) {
////                                closeSearchView()
////                                mapMarkersManager.showMarkers(results.map { it.coordinate })
////                                    // Очищаем предыдущие результаты
////                                    searchResultsView.clearResults()
////
////                                    // Добавляем новые результаты
////                                    for (result in results) {
////                                        // Создаем объект SearchResult на основе строки результата
////                                        val searchResult = SearchResult.Builder(result)
////                                            .build()
////
////                                        // Добавляем созданный объект SearchResult в SearchResultsView
////                                        searchResultsView.addResult(searchResult)
////                                    }
//                                    }
//
//                                    override fun onOfflineSearchResultsShown(
//                                        results: List<OfflineSearchResult>,
//                                        responseInfo: OfflineResponseInfo
//                                    ) {
//                                        // Nothing to do
//                                    }
//
//                                    override fun onSuggestionSelected(searchSuggestion: SearchSuggestion): Boolean {
//                                        return false
//                                    }
//
//                                    override fun onSearchResultSelected(
//                                        searchResult: SearchResult,
//                                        responseInfo: ResponseInfo
//                                    ) {
//                                        showToast("SearchResult clicked: ${searchResult.name}")
////                                closeSearchView()
////                                searchPlaceView.open(SearchPlace.createFromSearchResult(searchResult, responseInfo))
////                                mapMarkersManager.showMarker(searchResult.coordinate)
//                                    }
//
//                                    override fun onOfflineSearchResultSelected(
//                                        searchResult: OfflineSearchResult,
//                                        responseInfo: OfflineResponseInfo
//                                    ) {
//                                        showToast("OfflineSearchResult clicked: ${searchResult.name}")
////                                closeSearchView()
////                                searchPlaceView.open(SearchPlace.createFromOfflineSearchResult(searchResult))
////                                mapMarkersManager.showMarker(searchResult.coordinate)
//                                    }
//
//                                    override fun onError(e: Exception) {
//                                        showToast("Error happened: $e")
//
//                                        Toast.makeText(
//                                            applicationContext,
//                                            "Error happened: $e",
//                                            Toast.LENGTH_SHORT
//                                        ).show()
//                                    }
//
//                                    override fun onHistoryItemClick(historyRecord: HistoryRecord) {
//                                        showToast("HistoryRecord clicked: ${historyRecord.name}")
////                                closeSearchView()
////                                searchPlaceView.open(SearchPlace.createFromIndexableRecord(historyRecord, distanceMeters = null))
//
////                                locationEngine.userDistanceTo(this@MainActivity, historyRecord.coordinate) { distance ->
////                                    distance?.let {
////                                        searchPlaceView.updateDistance(distance)
////                                    }
////                                }
////
////                                mapMarkersManager.showMarker(historyRecord.coordinate)
//                                    }
//
//                                    override fun onPopulateQueryClick(
//                                        suggestion: SearchSuggestion,
//                                        responseInfo: ResponseInfo
//                                    ) {
//                                        queryEditInputText.setText(suggestion.name)
//
//                                        if (::searchView.isInitialized) {
//                                            searchView.setQuery(suggestion.name, true)
//                                        }
//                                    }
//
//                                    override fun onFeedbackItemClick(responseInfo: ResponseInfo) {
//                                        // Not implemented
//                                    }
//                                })
//           search adapter



                            addressAutofillUiAdapter = AddressAutofillUiAdapter(
                                view = searchResultsView,
                                addressAutofill = addressAutofill
                            )
                            addressAutofillUiAdapter.addSearchListener(object : AddressAutofillUiAdapter.SearchListener {

                                override fun onSuggestionSelected(suggestion: AddressAutofillSuggestion) {
                                    selectSuggestion(
                                        suggestion,
//                                        fromReverseGeocoding = true,
                                        fromReverseGeocoding = false,
                                    )
                                }

                                override fun onSuggestionsShown(suggestions: List<AddressAutofillSuggestion>) {
                                    // Nothing to do
                                }

                                override fun onError(e: Exception) {
                                    // Nothing to do
                                }
                            })

                            queryEditInputText.addTextChangedListener(object : TextWatcher {

                                override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
                                    if (ignoreNextQueryTextUpdate) {
                                        ignoreNextQueryTextUpdate = false
                                        return
                                    }

                                    val query = Query.create(text.toString())
                                    if (query != null) {
                                        lifecycleScope.launch {
                                            addressAutofillUiAdapter.search(query)
                                        }
                                    }
                                    searchResultsView.isVisible = query != null
                                }
////                                override fun onTextChanged(s: CharSequence, start: Int, before: Int, after: Int) {
//                                override fun onTextChanged(s: CharSequence, start: Int, before: Int, after: Int) {
////                                    val address = binding.searcET.text.toString().trim()
////                                    searchResultsView.search(s.toString())
////                                    searchResultsView.toString()
//                                }


                                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
// not implemented
                                }

                                override fun afterTextChanged(e: Editable) { /* not implemented */ }
                            })

                        }

//                        searchPlaceView = findViewById(R.id.search_place_view)
//                        searchPlaceView.initialize(CommonSearchViewConfiguration(DistanceUnitType.IMPERIAL))
//
//                        searchPlaceView.addOnCloseClickListener {
//                            mapMarkersManager.clearMarkers()
//                            searchPlaceView.hide()
//                        }
//
//                        searchPlaceView.addOnNavigateClickListener { searchPlace ->
//                            startActivity(geoIntent(searchPlace.coordinate))
//                        }
//
//                        searchPlaceView.addOnShareClickListener { searchPlace ->
//                            startActivity(shareIntent(searchPlace))
//                        }
//
//                        searchPlaceView.addOnFeedbackClickListener { _, _ ->
//                            // Not implemented
//                        }
//
//                        searchPlaceView.addOnBottomSheetStateChangedListener { _, _ ->
//                            updateOnBackPressedCallbackEnabled()
//                        }


//                        val searchResult = SearchResult.Builder("Your search result")
//                            .build()
//                        searchResultsView.focusSearch(searchResult)

                            // работающий код для поисковой строки в загаловке
//                        this@MainActivity.runOnUiThread {
//                            searchResultsView.apply {
//                                initialize(
//                                    SearchResultsView.Configuration(
//                                        CommonSearchViewConfiguration(DistanceUnitType.IMPERIAL)
//                                    )
//                                )
//                                isVisible = false
//                            }
//
//                            val searchEngine = SearchEngine.createSearchEngineWithBuiltInDataProviders(
//                                apiType = ApiType.GEOCODING,
//                                settings = SearchEngineSettings(getString(R.string.mapbox_access_token))
//                            )
//
//                            val offlineSearchEngine = OfflineSearchEngine.create(
//                                OfflineSearchEngineSettings(getString(R.string.mapbox_access_token))
//                            )
//
//                            searchEngineUiAdapter = SearchEngineUiAdapter(
//                                view = searchResultsView,
//                                searchEngine = searchEngine,
//                                offlineSearchEngine = offlineSearchEngine,
//                            )
//
//                            searchEngineUiAdapter.searchMode = SearchMode.AUTO
//
//                            searchEngineUiAdapter.addSearchListener(object : SearchEngineUiAdapter.SearchListener {
//
//                                override fun onSuggestionsShown(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
//                                    // Nothing to do
//                                }
//
//                                override fun onSearchResultsShown(
//                                    suggestion: SearchSuggestion,
//                                    results: List<SearchResult>,
//                                    responseInfo: ResponseInfo
//                                ) {
//                                    closeSearchView()
////                                    mapMarkersManager.showMarkers(results.map { it.coordinate })
//                                }
//
//                                override fun onOfflineSearchResultsShown(results: List<OfflineSearchResult>, responseInfo: OfflineResponseInfo) {
//                                    // Nothing to do
//                                }
//
//                                override fun onSuggestionSelected(searchSuggestion: SearchSuggestion): Boolean {
//                                    return false
//                                }
//
//                                override fun onSearchResultSelected(searchResult: SearchResult, responseInfo: ResponseInfo) {
//                                    closeSearchView()
////                                    searchPlaceView.open(SearchPlace.createFromSearchResult(searchResult, responseInfo))
////                                    mapMarkersManager.showMarker(searchResult.coordinate)
//                                }
//
//                                override fun onOfflineSearchResultSelected(searchResult: OfflineSearchResult, responseInfo: OfflineResponseInfo) {
//                                    closeSearchView()
////                                    searchPlaceView.open(SearchPlace.createFromOfflineSearchResult(searchResult))
////                                    mapMarkersManager.showMarker(searchResult.coordinate)
//                                }
//
//                                override fun onError(e: Exception) {
//                                    Toast.makeText(applicationContext, "Error happened: $e", Toast.LENGTH_SHORT).show()
//                                }
//
//                                override fun onHistoryItemClick(historyRecord: HistoryRecord) {
//                                    closeSearchView()
////                                    searchPlaceView.open(SearchPlace.createFromIndexableRecord(historyRecord, distanceMeters = null))
////
////                                    locationEngine.userDistanceTo(this@MainActivity, historyRecord.coordinate) { distance ->
////                                        distance?.let {
////                                            searchPlaceView.updateDistance(distance)
////                                        }
////                                    }
////
////                                    mapMarkersManager.showMarker(historyRecord.coordinate)
//                                }
//
//                                override fun onPopulateQueryClick(suggestion: SearchSuggestion, responseInfo: ResponseInfo) {
//                                    if (::searchView.isInitialized) {
//                                        searchView.setQuery(suggestion.name, true)
//                                    }
//                                }
//
//                                override fun onFeedbackItemClick(responseInfo: ResponseInfo) {
//                                    // Not implemented
//                                }
//                            })
//
////                            searchPlaceView = findViewById(R.id.search_place_view)
////                            searchPlaceView.initialize(CommonSearchViewConfiguration(DistanceUnitType.IMPERIAL))
////
////                            searchPlaceView.addOnCloseClickListener {
////                                mapMarkersManager.clearMarkers()
////                                searchPlaceView.hide()
////                            }
////
////                            searchPlaceView.addOnNavigateClickListener { searchPlace ->
////                                startActivity(geoIntent(searchPlace.coordinate))
////                            }
////
////                            searchPlaceView.addOnShareClickListener { searchPlace ->
////                                startActivity(shareIntent(searchPlace))
////                            }
////
////                            searchPlaceView.addOnFeedbackClickListener { _, _ ->
////                                // Not implemented
////                            }
////
////                            searchPlaceView.addOnBottomSheetStateChangedListener { _, _ ->
////                                updateOnBackPressedCallbackEnabled()
////                            }
//
////                            if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
////                                ActivityCompat.requestPermissions(
////                                    this,
////                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
////                                    PERMISSIONS_REQUEST_LOCATION
////                                )
////                            }
//                        }
//                    } catch (e: JSONException) {
//                        Log.e(TAG, "Error parsing JSON: ${e.message}")
//                    }
//                }
//            })

//                        этот код за видимостью функции
//                        private fun closeSearchView() {
//                            searchView.setQuery("", false)
//                        }
//                        override fun onCreateOptionsMenu(menu: Menu): Boolean {
//                            menuInflater.inflate(R.menu.main_activity_options_menu, menu)
//
//                            val searchActionView = menu.findItem(R.id.action_search)
//                            searchActionView.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
//                                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
////                searchPlaceView.hide()
//                                    searchResultsView.isVisible = true
//                                    return true
//                                }
//
//                                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
//                                    searchResultsView.isVisible = false
//                                    return true
//                                }
//                            })
//
////        searchView = searchActionView.actionView as SearchView
////        searchView.queryHint = getString(R.string.query_hint)
////        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
////            override fun onQueryTextSubmit(query: String): Boolean {
////                return false
////            }
////
////            override fun onQueryTextChange(newText: String): Boolean {
////                searchEngineUiAdapter.search(newText)
////                return false
////            }
////        })
//                            return true
//                        }
//
//                        override fun onOptionsItemSelected(item: MenuItem): Boolean {
//                            return when (item.itemId) {
////            R.id.open_address_autofill_ui_example -> {
////                startActivity(Intent(this, AddressAutofillUiActivity::class.java))
////                true
////            }
////            R.id.open_address_autofill_example -> {
////                startActivity(Intent(this, AddressAutofillKotlinExampleActivity::class.java))
////                true
////            }
////            ...
//                                else -> super.onOptionsItemSelected(item)
//                            }
//                        }
//                        этот код за видимостью функции

                            // работающий код для поисковой строки в загаловке


////                        requireActivity().runOnUiThread {   // for fragment
//                        this@MainActivity.runOnUiThread {
//                            val searchResultsView =
//                                findViewById<SearchResultsView>(R.id.searchResultsView2)
////                    val searchResultsView = binding.searchResultsView2
//                            searchResultsView.initialize(
//                                SearchResultsView.Configuration(
//                                    commonConfiguration = CommonSearchViewConfiguration(
//                                        DistanceUnitType.IMPERIAL
//                                    )
//                                )
//                            )
//                        }
//                        val searchEngine = SearchEngine.createSearchEngineWithBuiltInDataProviders(
//                            apiType = ApiType.GEOCODING,
//                            settings = SearchEngineSettings(accessToken)
//                        )
//
//                        val offlineSearchEngine = OfflineSearchEngine.create(
//                            OfflineSearchEngineSettings(accessToken)
//                        )
//
//                        val searchEngineUiAdapter = SearchEngineUiAdapter(
//                            view = searchResultsView,
//                            searchEngine = searchEngine,
//                            offlineSearchEngine = offlineSearchEngine,
//                        )
//
//                        searchEngineUiAdapter.addSearchListener(object :
//                            SearchEngineUiAdapter.SearchListener {
//
//                            private fun showToast(message: String) {
//                                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT)
//                                    .show()
//                            }
//
//                            override fun onSuggestionsShown(
//                                suggestions: List<SearchSuggestion>,
//                                responseInfo: ResponseInfo
//                            ) {
//                                // not implemented
//                            }
//
//                            override fun onSearchResultsShown(
//                                suggestion: SearchSuggestion,
//                                results: List<SearchResult>,
//                                responseInfo: ResponseInfo
//                            ) {
//                                // not implemented
//                            }
//
//                            override fun onOfflineSearchResultsShown(
//                                results: List<OfflineSearchResult>,
//                                responseInfo: OfflineResponseInfo
//                            ) {
//                                // not implemented
//                            }
//
//                            override fun onSuggestionSelected(searchSuggestion: SearchSuggestion): Boolean {
//                                return false
//                            }
//
//                            override fun onSearchResultSelected(
//                                searchResult: SearchResult,
//                                responseInfo: ResponseInfo
//                            ) {
//                                showToast("SearchResult clicked: ${searchResult.name}")
//                            }
//
//                            override fun onOfflineSearchResultSelected(
//                                searchResult: OfflineSearchResult,
//                                responseInfo: OfflineResponseInfo
//                            ) {
//                                showToast("OfflineSearchResult clicked: ${searchResult.name}")
//                            }
//
//                            override fun onError(e: Exception) {
//                                showToast("Error happened: $e")
//                            }
//
//                            override fun onHistoryItemClick(historyRecord: HistoryRecord) {
//                                showToast("HistoryRecord clicked: ${historyRecord.name}")
//                            }
//
//                            override fun onPopulateQueryClick(
//                                suggestion: SearchSuggestion,
//                                responseInfo: ResponseInfo
//                            ) {
//                                binding.searcET.setText(suggestion.name)
//                            }
//
//                            override fun onFeedbackItemClick(responseInfo: ResponseInfo) {
//                                // not implemented
//                            }
//                        })
//
//                        binding.searcET.addTextChangedListener(object : TextWatcher {
//
//                            override fun onTextChanged(
//                                s: CharSequence,
//                                start: Int,
//                                before: Int,
//                                after: Int
//                            ) {
////                            searchResultsView.search(s.toString())
//                            }
//
//                            override fun beforeTextChanged(
//                                s: CharSequence,
//                                start: Int,
//                                count: Int,
//                                after: Int
//                            ) {
//                                // not implemented
//                            }
//
//                            override fun afterTextChanged(e: Editable) { /* not implemented */
//                            }
//                        })
//
//                        if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
//                            ActivityCompat.requestPermissions(
//                                this@MainActivity,
//                                arrayOf(
//                                    Manifest.permission.ACCESS_FINE_LOCATION,
//                                    Manifest.permission.ACCESS_COARSE_LOCATION
//                                ),
//                                PERMISSIONS_REQUEST_LOCATION
//                            )
//                        }


//                    for (i in 0 until features!!.length()) {
//                        val feature = features.getJSONObject(i)
//                        val placeName = feature.getString("place_name")
//                        results.add(placeName)
//                    }
//
//                    activity?.runOnUiThread {
//                        searchResultsView.clearResults()
//                        searchResultsView.addResults(results)
////                        showSearchResults(results)
//                    }
//                    if (features != null) {
//                        if (features.length() > 0) {
//                            val location = features.getJSONObject(0)?.getJSONArray("center")
//                            val longitude = location?.getDouble(0)
//                            val latitude = location?.getDouble(1)
//                            val placeName = features.getJSONObject(0)?.getString("place_name")
//
//                            activity?.runOnUiThread {
//                                // Очищаем старые результаты перед добавлением новых
//                                binding.searchResultsView.removeAllViews()
//                                // Обновляем интерфейс с полученными данными
//                                val resultText = ("Широта: $latitude\nДолгота: $longitude\nМестоположение: $placeName")
//                                binding.searchResultsView.addResult(resultText)
//                            }
//                        } else {
//                            Log.e("Response", "No features found in the response.")
//                        }
//                    }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Error parsing JSON: ${e.message}")
                    }
                }


                private fun findAddress(point: Point) {
                    lifecycleScope.launch {
                        val response = addressAutofill.suggestions(point, AddressAutofillOptions())
                        response.onValue { suggestions ->
                            if (suggestions.isEmpty()) {
//                                showToast(R.string.address_autofill_error_pin_correction)
                            } else {
                                selectSuggestion(
                                    suggestions.first(),
                                    fromReverseGeocoding = true
                                )
                            }
                        }.onError {
//                            showToast(R.string.address_autofill_error_pin_correction)
                        }
                    }
                }
                private fun selectSuggestion(suggestion: AddressAutofillSuggestion, fromReverseGeocoding: Boolean) {
                    lifecycleScope.launch {
                        val response = addressAutofill.select(suggestion)
                        response.onValue { result ->
                            showAddressAutofillResult(result, fromReverseGeocoding)
                        }.onError {
//                            showToast(R.string.address_autofill_error_select)
                        }
                    }
                }
                private fun showAddressAutofillResult(result: AddressAutofillResult, fromReverseGeocoding: Boolean) {
//                    fullAddress = findViewById(R.id.full_address)
                    val address = result.address
//                    cityEditText.setText(address.place)
//                    stateEditText.setText(address.region)
//                    zipEditText.setText(address.postcode)

                    fullAddress.isVisible = true
                    fullAddress.text = result.suggestion.formattedAddress

//                    pinCorrectionNote.isVisible = true

                    if (!fromReverseGeocoding) {
//                        mapView.getMapboxMap().setCamera(
//                            CameraOptions.Builder()
//                                .center(result.suggestion.coordinate)
//                                .zoom(16.0)
//                                .build()
//                        )
                        ignoreNextMapIdleEvent = true
//                        mapPin.isVisible = true
                    }

                    ignoreNextQueryTextUpdate = true
                    queryEditInputText.setText(
                        listOfNotNull(
                            address.houseNumber,
                            address.street
                        ).joinToString()
                    )
                    queryEditInputText.clearFocus()

                    searchResultsView.isVisible = false
//                    searchResultsView.hideKeyboard()
                }
            })
    }

    private fun handleGeocodingResults(results: List<String>) {
        this@MainActivity.runOnUiThread {
            // Очистка предыдущих результатов
            binding.searchResultsTextView.text = ""
            // Вывод новых результатов
            for (result in results) {
                binding.searchResultsTextView.append("$result\n")
            }
        }
    }


//    private fun handleGeocodingResults(results: List<String>) {
//    this@MainActivity.runOnUiThread {
//            // Очистка предыдущих результатов
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            searchResultsView.isPreferKeepClear
//        }
//
//        // Добавление новых результатов
//            for (result in results) {
//                // Создание объекта SearchResult
//                val searchResult = SearchResult.Builder(result)
//                    .build()
//
//                // Добавление SearchResult в SearchResultsView
//                searchResultsView.focusSearch(searchResult)
//            }
//        }
//    }

    private val TAG = "MyTAG"
}