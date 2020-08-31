package ctfz.trustarea.ims


import android.content.*
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.*
import java.util.*


/*
* Intent Messaging System
*  by ramon93i7
* */

class IMS(private val context: Context) {
    companion object {
        private const val RETURN_WITH_ACTION_NAME = "__return_with_action__"
    }

    fun send(msg: Intent, callback: (Intent) -> Unit) {
        val randomReturnAction = UUID.randomUUID().toString()
        msg.apply {
            putExtra(RETURN_WITH_ACTION_NAME, randomReturnAction)
        }
        registerHandlerForAction(randomReturnAction, callback)
        context.startService(msg)
    }

    fun sendTo(pkg: String, serviceClass: String, actionName: String, msg: Intent, callback: (Intent) -> Unit) {
        send(msg.apply {
            component = ComponentName(pkg, serviceClass)
            action = "${pkg}.action.${actionName}"
        }, callback)
    }

    suspend fun send(msg: Intent): Intent =
        suspendCancellableCoroutine {cont ->
            send(msg) { cont.resume(it) }
        }

    suspend fun sendTo(pkg: String, serviceClass: String, actionName: String, msg: Intent): Intent =
        suspendCancellableCoroutine { cont ->
            sendTo(pkg, serviceClass, actionName, msg) {
                cont.resume(it)
            }
        }


    fun replyTo(msg: Intent, answer: Intent): Boolean {
        val returnAction = extractReturnAction(msg) ?: return false
        context.sendBroadcast(answer.apply {
            action = returnAction
        })
        return true
    }

    fun isMsg(msg: Intent): Boolean = extractReturnAction(msg) != null

    private fun extractReturnAction(msg: Intent): String? = msg.getStringExtra(RETURN_WITH_ACTION_NAME)

    private fun registerHandlerForAction(action: String, handler: (Intent) -> Unit) {
        val filter = IntentFilter().apply {
            addAction(action)
        }
        val receiver = OneTimeBroadcastReceiver(handler)
        context.registerReceiver(receiver, filter)
    }

    private class OneTimeBroadcastReceiver(private val callback: (Intent) -> Unit): BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context?.unregisterReceiver(this)
            intent?: return
            callback.invoke(intent)
        }
    }
}



