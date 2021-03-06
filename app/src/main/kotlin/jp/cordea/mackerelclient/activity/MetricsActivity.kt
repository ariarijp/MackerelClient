package jp.cordea.mackerelclient.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import butterknife.bindView
import io.realm.Realm
import jp.cordea.mackerelclient.MetricsType
import jp.cordea.mackerelclient.MetricsViewModel
import jp.cordea.mackerelclient.R
import jp.cordea.mackerelclient.adapter.MetricsAdapter
import jp.cordea.mackerelclient.model.MetricsParameter
import jp.cordea.mackerelclient.model.UserMetric
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers

class MetricsActivity : AppCompatActivity() {

    val toolbar: Toolbar by bindView(R.id.toolbar)

    val noticeView: View by bindView(R.id.notice_container)

    val templateButton: Button by bindView(R.id.template)

    val recyclerView: RecyclerView by bindView(R.id.recycler_view)

    val swipeRefresh: SwipeRefreshLayout by bindView(R.id.swipe_refresh)

    private var viewModel: MetricsViewModel? = null

    private var subscription: Subscription? = null

    private var hostId: String? = null

    private var needRefresh = false

    private var drawCompleteMetrics = 0

    private var enableRefresh = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_metrics)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView.layoutManager = LinearLayoutManager(this)
        val hostId = intent.getStringExtra(HostIdKey)

        viewModel = MetricsViewModel(this)

        swipeRefresh.setOnRefreshListener {
            if (enableRefresh) {
                refresh(hostId)
            }
            swipeRefresh.isRefreshing = false
        }

        needRefresh = true
        this.hostId = hostId
    }

    override fun onResume() {
        super.onResume()
        if (needRefresh) {
            refresh(hostId!!)
            needRefresh = false
        }
    }

    private fun refresh(hostId: String) {
        enableRefresh = false
        val realm = Realm.getInstance(applicationContext)
        val metrics = realm.copyFromRealm(
                realm.where(UserMetric::class.java)
                        .equalTo("type", MetricsType.HOST.name)
                        .equalTo("parentId", hostId).findAll())
        val item = metrics.map { MetricsParameter(it.id, null, it.label!!) }
        realm.close()

        recyclerView.adapter = MetricsAdapter(this, item as MutableList, MetricsType.HOST, hostId)

        drawCompleteMetrics = 0
        subscription?.let {
            it.unsubscribe()
        }
        subscription = viewModel!!
                .onChartDataAlive
                .asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({it0 ->
                    val adapter = recyclerView.adapter as MetricsAdapter
                    drawCompleteMetrics = adapter.refreshRecyclerViewItem(it0, drawCompleteMetrics)
                    if (adapter.itemCount == drawCompleteMetrics) {
                        enableRefresh = true
                    }
                }, {})

        if (metrics.size == 0) {
            noticeView.visibility = View.VISIBLE
            templateButton.setOnClickListener {
                templateButton.isClickable = false
                viewModel = MetricsViewModel(applicationContext)
                viewModel!!.initializeUserMetrics(hostId)
                refresh(hostId)
            }
            swipeRefresh.visibility = View.GONE
        } else {
            swipeRefresh.visibility = View.VISIBLE
            noticeView.visibility = View.GONE
            viewModel!!.requestMetricsApi(metrics, hostId, MetricsType.HOST)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel!!.subscription?.let {
            it.unsubscribe()
        }
        subscription?.let {
            it.unsubscribe()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.metric, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == MetricsEditActivity.RequestCode) {
            if (resultCode == Activity.RESULT_OK) {
                needRefresh = true
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.action_add -> {
                val intent = MetricsEditActivity.newInstance(applicationContext, MetricsType.HOST, hostId!!)
                startActivityForResult(intent, MetricsEditActivity.RequestCode)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        public val HostIdKey = "HostIdKey"
    }
}
