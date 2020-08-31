package ctfz.trustarea.checker

import java.util.*
import kotlinx.coroutines.*
import android.content.*
import android.util.Log
import ctfz.trustarea.ims.*
import ctfz.trustarea.result.*
import ctfz.trustarea.coroutines.*


class ClientAdapter(private val ims: IMS, private val teamId: Int) {
    private val teamPackage = "ctfz.trustarea.client.team${teamId}"

    private val AUTH_REG = "AUTH_REGISTRATION"
    private val REG_TIMEOUT = 1000L
    private val AUTH_SESSION = "AUTH_SESSION"
    private val SESSION_TIMEOUT = 1000L

    private val TASK_CREATE = "TASK_CREATE"
    private val CREATE_TASK_TIMEOUT = 1000L

    private val SOLUTION_SEND = "SOLUTION_SEND"
    private val SEND_SOLUTION_TIMEOUT = 2000L

    private val BACKUP_CREATE           = "CREATE_BACKUP"
    private val CREATE_BACKUP_TIMEOUT   = 2000L
    private val BACKUP_GET              = "GET_BACKUP"
    private val GET_BACKUP_TIMEOUT      = 1000L

    companion object {
        private val PACKAGE_PREFIX = "ctfz.trustarea.client"
        private val SERVICE_PREFIX = "${PACKAGE_PREFIX}.service.view"

        private fun serviceName(name: String): String = "${SERVICE_PREFIX}.${name}Service"

        private val ECHO_SERVICE = serviceName("Echo")
        private val ECHO_TIMEOUT = 250L

        private val AUTH_SERVICE = serviceName("Auth")
        private val TASK_SERVICE = serviceName("Task")
        private val SOLUTION_SERVICE = serviceName("Solution")
        private val BACKUP_SERVICE = serviceName("Backup")
    }

    suspend fun echo(message: String?): ResultS<Unit> {
        val MESSAGE_PARAM = "message"
        return withTimeoutOrError<Intent>(ECHO_TIMEOUT, "timeout: echo") {
            ims.sendTo(teamPackage, ECHO_SERVICE, "ECHO",
                Intent().apply {
                    putExtra(MESSAGE_PARAM, message ?: UUID.randomUUID().toString())
                }
            ).asOk()
        }.mapErr {
            "team #${teamId} is not reachable"
        }.map {
            it.getStringExtra(MESSAGE_PARAM)
        }.filter("wrong echo message") {
            it == message
        }.map {}  // return Unit
    }

    suspend fun register(username: String, firstName: String, lastName: String): ResultS<String> =
       withTimeoutOrError<Intent>(REG_TIMEOUT, "timeout: registration") {
           ims.sendTo(teamPackage, AUTH_SERVICE, AUTH_REG,
               Intent().apply {
                   putExtra("USER_NAME", username)
                   putExtra("FIRST_NAME", firstName)
                   putExtra("LAST_NAME", lastName)
               }
           ).asOk()
       } .flatMap {
           it.getStringExtra("TOKEN").asResult("token: no token on registration")
       }

    suspend fun newSession(token: String): ResultS<String> =
        withTimeoutOrError<Intent>(SESSION_TIMEOUT, "timeout: new session") {
            ims.sendTo(teamPackage, AUTH_SERVICE, AUTH_SESSION,
                Intent().apply {
                    putExtra("TOKEN", token)
                    putExtra("TOKEN_TYPE", "refresh")
                }
            ).asOk()
        } .flatMap {
            it.getStringExtra("TOKEN").asResult("token: no session token")
        }

    suspend fun createTask(session: String, description: String, challenge: String, reward: String): ResultS<Int> =
        withTimeoutOrError<Intent>(CREATE_TASK_TIMEOUT, "timeout: create task") {
            ims.sendTo(teamPackage, TASK_SERVICE, TASK_CREATE,
                Intent().apply {
                    putExtra("TOKEN", session)
                    putExtra("TOKEN_TYPE", "session")

                    putExtra("DESCRIPTION", description)
                    putExtra("CHALLENGE", challenge)
                    putExtra("REWARD", reward)
                }
            ).asOk()
        } .flatMap {
            it.getIntExtra("ID", -1337).asResult("task: no task id")
                .filter("task: no task id") { it != -1337 }
        }

    suspend fun sendSolution(taskId: Int, session: String, solution: String): ResultS<String> =
        withTimeoutOrError<Intent>(SEND_SOLUTION_TIMEOUT, "timeout: send solution") {
            ims.sendTo(teamPackage, SOLUTION_SERVICE, SOLUTION_SEND,
                Intent().apply {
                    putExtra("TOKEN", session)
                    putExtra("TOKEN_TYPE", "session")

                    putExtra("TASK_ID", taskId)
                    putExtra("SOLUTION", solution)
                }
            ).asOk()
        } .flatMap {
            it.getStringExtra("REWARD").asResult("solution: no flag")
        }

    suspend fun createBackup(session: String): ResultS<Unit> =
        withTimeoutOrError<Intent>(CREATE_BACKUP_TIMEOUT, "timeout: create backup") {
            ims.sendTo(teamPackage, BACKUP_SERVICE, BACKUP_CREATE,
                Intent().apply {
                    putExtra("TOKEN", session)
                    putExtra("TOKEN_TYPE", "session")
                }
            ).asOk()
        }.filter("backup: no status") {
            it.getBooleanExtra("STATUS", false)
        }.map {}

    suspend fun getBackup(session: String): ResultS<Unit> =
        withTimeoutOrError<Intent>(GET_BACKUP_TIMEOUT, "timeout: get backup") {
            ims.sendTo(teamPackage, BACKUP_SERVICE, BACKUP_GET,
                Intent().apply {
                    putExtra("TOKEN", session)
                    putExtra("TOKEN_TYPE", "session")
                }
            ).asOk()
        }.filter("backup: empty response") {
            it.getByteArrayExtra("DATA") != null ||  it.getBooleanExtra("STATUS", false)
        }.map {}
}
