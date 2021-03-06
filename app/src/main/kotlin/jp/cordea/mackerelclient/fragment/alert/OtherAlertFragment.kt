package jp.cordea.mackerelclient.fragment.alert

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import butterknife.bindView
import jp.cordea.mackerelclient.R
import jp.cordea.mackerelclient.activity.AlertDetailActivity
import jp.cordea.mackerelclient.adapter.OtherAlertAdapter
import jp.cordea.mackerelclient.api.MackerelApiClient
import jp.cordea.mackerelclient.api.response.Alert
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers

/**
 * Created by Yoshihiro Tanaka on 16/01/13.
 */
class OtherAlertFragment : Fragment() {

    companion object {
        public val RequestCode = 0
        fun newInstance(): OtherAlertFragment {
            val fragment = OtherAlertFragment()
            return fragment
        }
    }

    val listView: ListView by bindView(R.id.list)

    val swipeRefresh: SwipeRefreshLayout by bindView(R.id.swipe_refresh)

    val progress: View by bindView(R.id.progress)

    val error: View by bindView(R.id.error)

    private var alerts: List<Alert>? = null

    private var subscription: Subscription? = null

    private var resultSubscription: Subscription? = null

    private var itemSubscription: Subscription? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater?.inflate(R.layout.fragment_inside_alert, container, false)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        itemSubscription?.let {
            it.unsubscribe()
        }
        itemSubscription = (parentFragment as AlertFragment)
                .onAlertItemChanged
                .asObservable()
                .subscribe({
                    alerts = it?.alerts?.filter { !it.status.equals("CRITICAL") }
                    refresh()
                }, {
                    alerts = null
                    refresh()
                })

        val retry: Button = error.findViewById(R.id.retry) as Button
        retry.setOnClickListener {
            progress.visibility = View.VISIBLE
            error.visibility = View.GONE
            refresh()
        }

        swipeRefresh.setOnRefreshListener {
            refresh()
        }

        listView.setOnItemClickListener { adapterView, view, i, l ->
            val intent = Intent(context, AlertDetailActivity::class.java)
            intent.putExtra(AlertDetailActivity.AlertKey, listView.adapter.getItem(i) as Alert)
            parentFragment.startActivityForResult(intent, OtherAlertFragment.RequestCode)
        }

        resultSubscription?.let {
            it.unsubscribe()
        }
        (parentFragment as? AlertFragment)?.let {
            resultSubscription =
                    it.onOtherAlertFragmentResult
                            .asObservable()
                            .filter { it }
                            .subscribe({
                                refresh()
                            }, {})
        }
    }

    private fun refresh() {
        swipeRefresh.isRefreshing = true
        subscription?.let {
            it.unsubscribe()
        }
        subscription = requestApi()
    }
    private fun requestApi(): Subscription {
        var observable: Observable<Alert>
        if (alerts == null) {
            observable = MackerelApiClient
                    .getAlerts(context)
                    .flatMap {
                        Observable.from(it.alerts)
                                .filter { !it.status.equals("CRITICAL") }
                    }
        } else {
            observable = Observable.from(alerts)
            alerts = null
        }
        return observable
                .toList()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    listView.adapter = OtherAlertAdapter(context, it)
                    swipeRefresh.isRefreshing = false
                    swipeRefresh.visibility = View.VISIBLE
                    progress.visibility = View.GONE
                }, {
                    it.printStackTrace()
                    swipeRefresh.isRefreshing = false
                    error.visibility = View.VISIBLE
                    progress.visibility = View.GONE
                })
    }

    override fun onDestroyView() {
        subscription?.let {
            it.unsubscribe()
        }
        resultSubscription?.let {
            it.unsubscribe()
        }
        itemSubscription?.let {
            it.unsubscribe()
        }
        super.onDestroyView()
    }
}