package com.example.mapboxsearchsdk

import android.os.Bundle
import android.util.Log
import com.example.mapboxsearchsdk.databinding.FragmentSearchBinding
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mapbox.search.ApiType
import com.mapbox.search.ResponseInfo
import com.mapbox.search.SearchEngine
import com.mapbox.search.SearchEngineSettings
import com.mapbox.search.offline.OfflineResponseInfo
import com.mapbox.search.offline.OfflineSearchEngine
import com.mapbox.search.offline.OfflineSearchEngineSettings
import com.mapbox.search.offline.OfflineSearchResult
import com.mapbox.search.record.HistoryRecord
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import com.mapbox.search.ui.adapter.engines.SearchEngineUiAdapter
import com.mapbox.search.ui.view.CommonSearchViewConfiguration
import com.mapbox.search.ui.view.DistanceUnitType
import com.mapbox.search.ui.view.SearchResultsView
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException


class SearchFragment : Fragment(){
    private lateinit var binding: FragmentSearchBinding
    private lateinit var searchResultsView: SearchResultsView


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSearchBinding.inflate(inflater, container, false)

////        авто заполнение
//
////        addressAutofill = AddressAutofill.create(getString(R.string.mapbox_access_token))
////        val placeAutocomplete = PlaceAutocomplete.create(getString(R.string.mapbox_access_token))
//
//        val placeAutocomplete = PlaceAutocomplete.create(
//            accessToken = getString(R.string.mapbox_access_token),
//        )
//
//        viewLifecycleOwner.lifecycleScope.launch {
//            val response = placeAutocomplete.suggestions(
//                query = "Washington DC",
//            )
//
//            if (response.isValue) {
//                val suggestions = requireNotNull(response.value)
//
//                Log.i("SearchApiExample", "Place Autocomplete suggestions: $suggestions")
//
//                if (suggestions.isNotEmpty()) {
//// Supposing that a user has selected (clicked in UI) the first suggestion
//                    val selectedSuggestion = suggestions.first()
//
//                    Log.i("SearchApiExample", "Selecting first suggestion...")
//
//                    val selectionResponse = placeAutocomplete.select(selectedSuggestion)
//                    selectionResponse.onValue { result ->
//                        Log.i("SearchApiExample", "Place Autocomplete result: $result")
//                    }.onError { e ->
//                        Log.i("SearchApiExample", "An error occurred during selection", e)
//                    }
//                }
//            } else {
//                Log.i("SearchApiExample", "Place Autocomplete error", response.error)
//            }
//        }
////        авто заполнение

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        searchResultsView = binding.searchResultsView2
//
//        binding.searcET.setOnEditorActionListener { _, actionId, _ ->
//            if (actionId == EditorInfo.IME_ACTION_DONE) {
//                val address = binding.searcET.text.toString().trim()
//                if (address.isNotEmpty()) {
//                    geocodeAddress(address)
//                    // Возвращаем true, чтобы указать, что событие обработано
//                    return@setOnEditorActionListener true
//                } else {
//                    binding.searcET.error = "Введите адрес"
//                    // Возвращаем false, чтобы указать, что событие не было обработано
//                    return@setOnEditorActionListener false
//                }
//            }
//            // Если действие не IME_ACTION_DONE, возвращаем false
//            false
//        }
//
////        placeAutocompleteUiAdapter = PlaceAutocompleteUiAdapter(
////            view = searchResultsView,
////            placeAutocomplete = placeAutocomplete
////        )
////
////        placeAutocompleteUiAdapter.addSearchListener(object : PlaceAutocompleteUiAdapter.SearchListener {
////
////            override fun onSuggestionsShown(suggestions: List<PlaceAutocompleteSuggestion>) {
////                // Nothing to do
////            }
////
////            override fun onSuggestionSelected(suggestion: PlaceAutocompleteSuggestion) {
////                openPlaceCard(suggestion)
////            }
////
////            override fun onPopulateQueryClick(suggestion: PlaceAutocompleteSuggestion) {
////                queryEditText.setText(suggestion.name)
////            }
////
////            override fun onError(e: Exception) {
////                // Nothing to do
////            }
////        })
    }


    private fun geocodeAddress(address: String) {
////        val accessToken = "YOUR_MAPBOX_ACCESS_TOKEN" // Замените YOUR_MAPBOX_ACCESS_TOKEN на свой ключ доступа Mapbox
//        val accessToken = getString(R.string.mapbox_access_token)
//
//        val geocodeUrl = "https://api.mapbox.com/geocoding/v5/mapbox.places/$address.json?country=pl&proximity=ip&language=pl&access_token=$accessToken"
//
//        val client = OkHttpClient()
//        val request = Request.Builder()
//            .url(geocodeUrl)
//            .build()
//
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                Log.e("Request", "Failed to execute request: ${e.message}")
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                val responseBody = response.body?.string()
//                response.close()
//
//                try {
//                    val jsonObject = responseBody?.let { JSONObject(it) }
//                    val features = jsonObject?.getJSONArray("features")
//                    val results = mutableListOf<String>()
//
//
//                    if (features != null) {
//                        for (i in 0 until features.length()) {
//                            val feature = features.getJSONObject(i)
//                            val placeName = feature.getString("place_name")
//                            results.add(placeName)
//                        }
//                    }
//
//                    requireActivity().runOnUiThread {
//                        // Очищаем предыдущие результаты
//                        binding.searchResultsTextView.text = ""
//                        // Выводим новые результаты
//                        for (result in results) {
//                            binding.searchResultsTextView.append("$result\n")
//                        }
//                    }
//
////                    for (i in 0 until features!!.length()) {
////                        val feature = features.getJSONObject(i)
////                        val placeName = feature.getString("place_name")
////                        results.add(placeName)
////                    }
////
////                    activity?.runOnUiThread {
////                        searchResultsView.clearResults()
////                        searchResultsView.addResults(results)
//////                        showSearchResults(results)
////                    }
////                    if (features != null) {
////                        if (features.length() > 0) {
////                            val location = features.getJSONObject(0)?.getJSONArray("center")
////                            val longitude = location?.getDouble(0)
////                            val latitude = location?.getDouble(1)
////                            val placeName = features.getJSONObject(0)?.getString("place_name")
////
////                            activity?.runOnUiThread {
////                                // Очищаем старые результаты перед добавлением новых
////                                binding.searchResultsView.removeAllViews()
////                                // Обновляем интерфейс с полученными данными
////                                val resultText = ("Широта: $latitude\nДолгота: $longitude\nМестоположение: $placeName")
////                                binding.searchResultsView.addResult(resultText)
////                            }
////                        } else {
////                            Log.e("Response", "No features found in the response.")
////                        }
////                    }
//                } catch (e: JSONException) {
//                    Log.e("Response", "Error parsing JSON: ${e.message}")
//                }
//            }
//        })
////        val accessToken = getString(R.string.mapbox_access_token)
//
//        binding.apply {
////            val searchResultsView = searchResultsView
//
//            searchResultsView.initialize(
//                SearchResultsView.Configuration(
//                    commonConfiguration = CommonSearchViewConfiguration(DistanceUnitType.IMPERIAL)
//                )
//            )
//
//            val searchEngine = SearchEngine.createSearchEngineWithBuiltInDataProviders(
//                apiType = ApiType.GEOCODING,
//                settings = SearchEngineSettings(accessToken)
//            )
//
//            val offlineSearchEngine = OfflineSearchEngine.create(
//                OfflineSearchEngineSettings(accessToken)
//            )
//
//            val searchEngineUiAdapter = SearchEngineUiAdapter(
//                view = searchResultsView,
//                searchEngine = searchEngine,
//                offlineSearchEngine = offlineSearchEngine,
//            )
//
//            searchEngineUiAdapter.addSearchListener(object : SearchEngineUiAdapter.SearchListener {
//
//                private fun showToast(message: String) {
//                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
//                }
//
//                override fun onSearchResultSelected(searchResult: SearchResult, responseInfo: ResponseInfo) {
//                    showToast("SearchResult clicked: ${searchResult.name}")
//                }
//
//                override fun onOfflineSearchResultSelected(searchResult: OfflineSearchResult, responseInfo: OfflineResponseInfo) {
//                    showToast("OfflineSearchResult clicked: ${searchResult.name}")
//                }
//
//                override fun onError(e: Exception) {
//                    showToast("Error happened: $e")
//                }
//
//                override fun onSearchResultsShown(
//                    suggestion: SearchSuggestion,
//                    results: List<SearchResult>,
//                    responseInfo: ResponseInfo
//                ) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun onSuggestionSelected(searchSuggestion: SearchSuggestion): Boolean {
//                    TODO("Not yet implemented")
//                }
//
//                override fun onOfflineSearchResultsShown(
//                    results: List<OfflineSearchResult>,
//                    responseInfo: OfflineResponseInfo
//                ) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun onSuggestionsShown(
//                    suggestions: List<SearchSuggestion>,
//                    responseInfo: ResponseInfo
//                ) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun onFeedbackItemClick(responseInfo: ResponseInfo) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun onHistoryItemClick(historyRecord: HistoryRecord) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun onPopulateQueryClick(suggestion: SearchSuggestion, responseInfo: ResponseInfo) {
//                    // This is where you would handle populating the query text.
//                }
//            })
//        }
    }
}