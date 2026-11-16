package br.com.fluxo.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import br.com.fluxo.core.Direction
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Transaction
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home-screen summary: month in/out and review count. Never shows merchant names. */
class FluxoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        FluxoWidget.update(context.applicationContext, goAsync())
}

object FluxoWidget {
    /** Call when ledger data changes. */
    fun refresh(context: Context) = update(context.applicationContext, null)

    internal fun update(context: Context, pending: BroadcastReceiver.PendingResult?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, FluxoWidgetProvider::class.java))
                if (ids.isEmpty()) return@launch
                val repository = (context as FluxoApplication).repository
                repository.refresh()
                manager.updateAppWidget(ids, build(context, repository.transactions.value))
            } catch (_: Exception) {
                // Widget keeps its last content; the app itself is unaffected.
            } finally {
                pending?.finish()
            }
        }
    }

    private fun build(context: Context, rows: List<Transaction>): RemoteViews {
        val zone = ZoneId.systemDefault()
        val month = YearMonth.now(zone)
        val monthRows = rows.filter { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) == month }
        // Same rules as the Início screen: confirmed, no transfer, no voucher; cash and credit both count as spent.
        val net = monthRows
            .filter { it.status == ReviewStatus.CONFIRMED && it.transferId == null && !it.method.isVoucher }
            .fold(BigInteger.ZERO) { sum, it ->
                val cents = BigInteger.valueOf(it.amountCents ?: 0L)
                when (it.direction) { Direction.INCOME -> sum + cents; Direction.EXPENSE -> sum - cents; else -> sum }
            }
        val review = monthRows.count { it.status == ReviewStatus.REVIEW }
        val value = money(BigDecimal(net, 2)).replace("-", "−")
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return RemoteViews(context.packageName, R.layout.widget_fluxo).apply {
            setTextViewText(R.id.widget_month, month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR")))
            setTextViewText(R.id.widget_value, value)
            setTextViewText(R.id.widget_review, if (review == 0) "Nada para revisar" else "$review para revisar")
            setOnClickPendingIntent(R.id.widget_root, open)
        }
    }
}
