package jp.cordea.mackerelclient.adapter

import android.app.Activity
import android.app.AlertDialog
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import butterknife.bindView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.LineData
import io.realm.Realm
import jp.cordea.mackerelclient.MemoryValueFormatter
import jp.cordea.mackerelclient.MetricsType
import jp.cordea.mackerelclient.R
import jp.cordea.mackerelclient.activity.MetricsEditActivity
import jp.cordea.mackerelclient.model.MetricsParameter
import jp.cordea.mackerelclient.model.UserMetric
import kotlin.concurrent.withLock

/**
 * Created by Yoshihiro Tanaka on 16/01/20.
 */
class MetricsAdapter (val activity: Activity, val items: MutableList<MetricsParameter>, val type: MetricsType, val id: String, var visibles: Int = 0, var canRefresh: Boolean = false) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val lock = java.util.concurrent.locks.ReentrantLock()

    private var drawComplete: Int = 0

    companion object {
        val CustomEventKey = "Removed the card during refresh"
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        (holder as? ViewHolder)?.let {
            if (items[position].label.isNullOrEmpty()) {
                it.title.visibility = View.GONE
            } else {
                it.title.text = items[position].label
            }
            val data = items[position].data
            if (data == null) {
                if (items[position].isError) {
                    it.progress.visibility = View.GONE
                    it.error.visibility = View.VISIBLE
                }
            } else {
                val chart = it.chart
                chart.data = data
                chart.setDescription("")
                chart.xAxis.position = XAxis.XAxisPosition.BOTTOM

                val needFormat = data.dataSets
                        .filter { "memory" == it.label.split(".")[0] }.size == data.dataSets.size
                if (needFormat) {
                    chart.data.dataSets[0].label = chart.data.dataSets[0].label + " (GB)"
                    if (chart.data.dataSets.size > 1) {
                        chart.data.dataSets[1].label = chart.data.dataSets[1].label + " (GB)"
                    }
                    chart.axisRight.valueFormatter = MemoryValueFormatter()
                    chart.axisLeft.valueFormatter = MemoryValueFormatter()
                }

                chart.axisRight.setLabelCount(3, false)
                chart.axisLeft.setLabelCount(3, false)
                chart.visibility = View.VISIBLE
                it.progress.visibility = View.GONE
                chart.invalidate()
                ++visibles
                canRefresh = visibles == itemCount
            }

            it.edit.setOnClickListener {
                val intent = MetricsEditActivity.newInstance(activity, type, id, items[position].id)
                activity.startActivityForResult(intent, MetricsEditActivity.RequestCode)
            }

            it.delete.setOnClickListener {
                AlertDialog
                        .Builder(activity)
                        .setMessage(R.string.metrics_card_delete_dialog_title)
                        .setPositiveButton(R.string.button_positive, { dialogInterface, i ->
                            lock.withLock {
                                val realm = Realm.getInstance(activity)
                                realm.executeTransaction {
                                    realm.where(UserMetric::class.java).equalTo("id", items[position].id).findFirst().removeFromRealm()
                                }
                                realm.close()
                                items.removeAt(position)
                                notifyItemRemoved(position)
                                notifyItemRangeRemoved(position, items.size)
                                --drawComplete
                            }
                        }).show()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder? {
        val view = LayoutInflater.from(activity).inflate(R.layout.list_item_metrics_chart, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    public fun refreshRecyclerViewItem(item: Pair<Int, LineData?>, drawCompleteMetrics: Int): Int {
        drawComplete = drawCompleteMetrics
        lock.withLock {
            val idx = items.map { it.id }.indexOf(item.first)
            if (idx != -1 && idx < items.size) {
                items[idx] = MetricsParameter(item.first, item.second, items[idx].label, item.second == null)
                notifyDataSetChanged()
                return ++drawComplete
            }
        }
        return drawComplete
    }

    private class ViewHolder(val view: View): RecyclerView.ViewHolder(view) {
        val progress: View by bindView(R.id.progress)
        val chart: LineChart by bindView(R.id.chart)
        val title: TextView by bindView(R.id.title)
        val delete: Button by bindView(R.id.delete)
        val edit: Button by bindView(R.id.edit)
        val error: View by bindView(R.id.error)
    }
}