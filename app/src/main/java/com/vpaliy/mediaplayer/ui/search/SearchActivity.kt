package com.vpaliy.mediaplayer.ui.search

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import com.vpaliy.mediaplayer.FlashApp
import com.vpaliy.mediaplayer.R
import com.vpaliy.mediaplayer.di.component.DaggerViewComponent
import com.vpaliy.mediaplayer.di.module.PresenterModule
import com.vpaliy.mediaplayer.domain.model.Track
import com.vpaliy.mediaplayer.ui.base.BaseActivity
import com.vpaliy.mediaplayer.ui.base.BaseAdapter
import com.vpaliy.mediaplayer.ui.home.TrackAdapter
import com.vpaliy.mediaplayer.ui.search.SearchContract.Presenter
import kotlinx.android.synthetic.main.activity_search.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.SearchView
import android.content.Intent
import android.transition.Transition
import android.transition.TransitionInflater
import android.transition.TransitionManager
import com.vpaliy.mediaplayer.ui.utils.OnReachBottomListener
import android.app.SharedElementCallback
import android.support.annotation.TransitionRes
import javax.inject.Inject

class SearchActivity:BaseActivity(), SearchContract.View{

    private lateinit var presenter:Presenter
    private lateinit var adapter:BaseAdapter<Track>
    private var checked=false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        setupTransition()
        setupResult()
        setupSearch()
    }

    private fun setupTransition(){
        setEnterSharedElementCallback(object : SharedElementCallback(){
            override fun onSharedElementStart(sharedElementNames: MutableList<String>?,
                                              sharedElements: MutableList<View>?,
                                              sharedElementSnapshots: MutableList<View>?) {
                checked=!checked
                back.setImageState(intArrayOf(android.R.attr.state_checked * (if(checked) 1 else -1)),true)
                super.onSharedElementStart(sharedElementNames, sharedElements, sharedElementSnapshots)
            }
        })
    }

    private fun setupResult(){
        adapter=TrackAdapter(this,{navigator.navigate(this,it)}, {navigator.actions(this,it)})
        result.adapter=adapter
        result.addOnScrollListener(object: OnReachBottomListener(result.layoutManager){
            override fun onLoadMore() {
                presenter.more()
            }
        })
    }

    private fun setupSearch(){
        val searchManager=getSystemService(Context.SEARCH_SERVICE) as SearchManager
        back.setOnClickListener{onBackPressed()}
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.queryHint=getString(R.string.search_hint)
        searchView.inputType=InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        searchView.imeOptions = searchView.imeOptions or EditorInfo.IME_ACTION_SEARCH or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        searchView.setOnQueryTextListener(object:SearchView.OnQueryTextListener{
            override fun onQueryTextChange(newText: String?): Boolean {
                if(newText.isNullOrEmpty()) refreshPage(false)
                return true
            }
            override fun onQueryTextSubmit(query: String?): Boolean {
                presenter.query(query)
                searchView.clearFocus()
                hideKeyboard()
                return true
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        if (intent.hasExtra(SearchManager.QUERY)) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            if (!query.isNullOrEmpty()) {
                searchView.setQuery(query, false)
                searchView.clearFocus()
                hideKeyboard()
                presenter.query(query)
            }
        }
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        view?.let{
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun refreshPage(visible:Boolean, finish:Boolean=false){
        val transition=getTransition(if(visible) R.transition.search_show
                else R.transition.search_show)
        if(finish){
            result.animate()
            finishAfterTransition()
            return
        }
        TransitionManager.beginDelayedTransition(root,transition)
        result.visibility=if(visible) View.VISIBLE else View.GONE
    }

    override fun inject(){
        DaggerViewComponent.builder()
                .presenterModule(PresenterModule())
                .applicationComponent(FlashApp.app().component())
                .build().inject(this)
    }

    private fun getTransition(@TransitionRes transitionId: Int): Transition {
        val inflater = TransitionInflater.from(this)
        return inflater.inflateTransition(transitionId)
    }

    override fun error() {
        message.visibility=View.VISIBLE
    }

    override fun empty() {
        message.visibility=View.VISIBLE
    }

    override fun setLoading(isLoading: Boolean) {
        progress.visibility=if(isLoading)
            View.VISIBLE else View.GONE
    }

    @Inject
    override fun attach(presenter: Presenter) {
        this.presenter=presenter
        presenter.attachView(this)
    }

    override fun show(list: List<Track>){
        adapter.set(list.toMutableList())
        refreshPage(true)
    }

    override fun append(list: List<Track>)=adapter.appendData(list.toMutableList())

    override fun onBackPressed(){
        if(result.visibility!=View.VISIBLE){
            supportFinishAfterTransition()
        }else {
            refreshPage(false, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.stop()
    }
}