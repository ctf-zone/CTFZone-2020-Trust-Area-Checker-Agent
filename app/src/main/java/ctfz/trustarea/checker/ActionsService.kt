package ctfz.trustarea.checker

import android.app.IntentService
import android.content.Intent

import kotlinx.coroutines.*

import khttp.post
import org.json.JSONObject

import ctfz.trustarea.ims.*
import ctfz.trustarea.result.*
import java.security.MessageDigest
import java.util.*


class ActionsService : IntentService("ActionsService") {
    private lateinit var teams: List<ClientAdapter>

    companion object {
        private const val CHECKER_SERVER_BASE_URL = "http://100.100.51.53:31337/f6bd34bb2796a190bebbce5f38d71484"
        private const val CHECKER_SERVER_ANSWER_ENDPOINT = "${CHECKER_SERVER_BASE_URL}/answer"
        private const val CHECKER_SERVER_LOG_ENDPOINT = "${CHECKER_SERVER_BASE_URL}/log"

        private const val SECRET_EXTRA = "__secret__"
        private const val SECRET_TOKEN = "there-is-no-country-for-an-old-man"

        private const val REQID_EXTRA = "__req_id__"

        private const val ECHO_ACTION = "ECHO"

        private const val PUSH_FLAG_ACTION = "PUSH_FLAG"

        private const val PULL_FLAG_ACTION = "PULL_FLAG"

        private const val REG_USER_ACTION = "REG_USER"

        private const val CHECK_BACKUP_ACTION = "CHECK_BACKUP"

        private fun String.asInt(): Result<Int, String> {
            try {
                return Ok(this.toInt())
            } catch (ex: NumberFormatException) {
                return Err("'${this}' is not an integer")
            }
        }

        private fun Intent.extractTeamId(): ResultS<Int> =
            this.getIntOr("team_id", "bad req: no team_id")
                .filter("bad req: invalid team id") { it >= 1 && it <= 10 }

        private fun Intent.getOr(extraName: String, error: String): ResultS<String> =
            this.getStringExtra(extraName).asResult(error)

        private fun Intent.getIntOr(extraName: String, error: String): ResultS<Int> =
            this.getOr(extraName, error)
                .flatMap { it.asInt() }
                .mapErr { "bad req: $it" }

    }

    override fun onCreate() {
        super.onCreate()

        val ims = IMS(applicationContext)
        teams = List(11) { idx ->
            ClientAdapter(ims, idx)
        }
    }

    // val okTeams = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    override fun onHandleIntent(intent: Intent?) {
        intent?: return  // skip null intents

        val secret = intent.getStringExtra(SECRET_EXTRA)
        if (secret == null || secret != SECRET_TOKEN) {
            return  // TODO: punish dirty hackers
        }

        val reqId = intent.getStringExtra(REQID_EXTRA)?: return

        val response = when (intent.action) {
            ECHO_ACTION -> {
                EchoMsg.fromIntent(intent)
                    .flatMap { echo(it) }
            }
            PUSH_FLAG_ACTION -> {
                PushFlagMsg.fromIntent(intent)
                    /* .filter("TMP IGNORED") {
                        okTeams.contains(it.teamId)
                    } */
                    .flatMap { pushFlag(it) }
                    .map { it.toMap() }
            }
            PULL_FLAG_ACTION -> {
                PullFlagMsg.fromIntent(intent)
                    /*.filter("TMP IGNORED") {
                        okTeams.contains(it.teamId)
                    }*/
                    .flatMap { pullFlag(it) }
                    .map { it.toMap() }
            }
            REG_USER_ACTION -> {
                RegUserMsg.fromIntent(intent)
                    .flatMap { regUser(it) }
                    .map { it.toMap() }
            }

            CHECK_BACKUP_ACTION -> {
                BackupMsg.fromIntent(intent)
                    .flatMap { checkBackup(it) }
                    .map { mapOf<String, String>() }
            }
            else -> {
                Err("bad req: unknown action")
            }
        }.let {
            formatResponse(reqId, it)
        }

        sendResponse(response).merge()  // can use this value to debug

    }

    private fun sendResponse(msg: Map<String, String>): Result<String, String> {
        val n = 3
        while (n > 0) {
            try {
                val resp = post(CHECKER_SERVER_ANSWER_ENDPOINT, data = JSONObject(msg))
                if (resp.statusCode == 200)
                    return Ok(resp.jsonObject.toString())
                else
                    return Err(resp.text)
            } catch (ex: Exception) {

            }
        }
        return Err("network error")
    }

    private fun formatResponse(reqId: String, data: ResultS<Map<String, String>>): Map<String, String> =
        data.map { formatSuccess(it) }.mapErr { formatFailure(it) }.merge() + mapOf(REQID_EXTRA to reqId)

    private fun formatSuccess(data: Map<String, String>): Map<String, String> = mapOf("status" to "ok") + data

    private fun formatFailure(error: String): Map<String, String> = mapOf("status" to "error", "error" to error)


    private fun echo(msg: EchoMsg): ResultS<Map<String, String>> = runBlocking {
        val team = teams[msg.teamId]
        team.echo(msg.message) .map { mapOf("message" to msg.message) }
    }

    private fun pushFlag(msg: PushFlagMsg): ResultS<PushFlagAns> = runBlocking {
        val team = teams[msg.teamId]
        team.register(msg.username, msg.firstName, msg.lastName).flatMapA { token ->
            team.newSession(token).flatMapA { session ->
                val solution = UUID.randomUUID().toString()

                val md = MessageDigest.getInstance("SHA1")
                md.update(solution.toByteArray(Charsets.UTF_8))
                val challenge = md.digest().joinToString("") { "%02x".format(it) }

                team.createTask(session, msg.description, challenge, msg.flag).flatMapA { taskId ->
                    PushFlagAns(token, session, taskId, solution).asOk<PushFlagAns, String>()
                }
            }
        }
    }

    private fun pullFlag(msg: PullFlagMsg): ResultS<PullFlagAns> = runBlocking {
        val team = teams[msg.teamId]
        team.sendSolution(msg.taskId, msg.session, msg.solution).map { flag -> PullFlagAns(flag) }
    }

    private fun regUser(msg: RegUserMsg): ResultS<RegUserAns> = runBlocking {
        val team = teams[msg.teamId]
        team.register(msg.username, msg.firstName, msg.lastName).flatMapA { token ->
            team.newSession(token).flatMapA { session ->
                RegUserAns(token, session).asOk<RegUserAns, String>()
            }
        }
    }

    private fun checkBackup(msg: BackupMsg): ResultS<Unit> = runBlocking {
        val team = teams[msg.teamId]
        team.createBackup(msg.session).flatMapA {
            team.getBackup(msg.session)
        }
        /*
        val username = UUID.randomUUID().toString().substring(0,10)
        val firstname = UUID.randomUUID().toString().substring(0,4)
        val lastname = UUID.randomUUID().toString().substring(0,6)

        team.register(username, firstname, lastname).flatMapA { token ->
            team.newSession(token).flatMapA {  session ->
                val solution = UUID.randomUUID().toString()
                val md = MessageDigest.getInstance("SHA1")
                md.update(solution.toByteArray(Charsets.UTF_8))
                val challenge = md.digest().joinToString("") { "%02x".format(it) }

                val description = UUID.randomUUID().toString().substring(0, 10)
                val reward = UUID.randomUUID().toString().substring(0, 5)
                team.createTask(session, description, challenge, reward).flatMapA {
                    team.createBackup(session).flatMapA {
                        team.getBackup(session)
                    }
                }
            }
        }
         */
    }

    private class EchoMsg(val teamId: Int, val message: String) {
        companion object {
            fun fromIntent(intent: Intent): ResultS<EchoMsg> =
                mapN(
                    intent.extractTeamId(),
                    intent.getOr("message", "bad req: no message")
                ) { teamId, message ->
                    EchoMsg(teamId, message)
                }
        }
    }


    private class PushFlagMsg(val teamId: Int, val flag: String, val username: String, val firstName: String, val lastName: String, val description: String) {
        companion object {
            fun fromIntent(intent: Intent): ResultS<PushFlagMsg> =
                mapN(
                    intent.extractTeamId(),
                    intent.getOr("flag", "bad req: no flag"),
                    intent.getOr("username", "bad req: no username"),
                    intent.getOr("first_name", "bad req: no first_name"),
                    intent.getOr("last_name", "bad req: no last_name"),
                    intent.getOr("description", "bad req: no task description")
                ) { teamId, flag, username, firstName, lastName, description ->
                    PushFlagMsg(teamId, flag, username, firstName, lastName, description)
                }
        }
    }

    private class PushFlagAns(val token: String, val session: String, val taskId: Int, val taskSolution: String) {
        fun toMap(): Map<String, String> = mapOf(
            "token" to token,
            "session" to session,
            "task" to taskId.toString(),
            "solution" to taskSolution
        )
    }

    private class PullFlagMsg(val teamId: Int, val session: String, val taskId: Int, val solution: String) {
        companion object {
            fun fromIntent(intent: Intent): ResultS<PullFlagMsg> =
                mapN(
                    intent.extractTeamId(),
                    intent.getOr("session", "bad_req: no session"),
                    intent.getIntOr("task", "bad_req: no task"),
                    intent.getOr("solution", "bad_req: no solution")
                ) { teamId, session, taskId, solution ->
                    PullFlagMsg(teamId, session, taskId, solution)
                }
        }
    }

    private class PullFlagAns(val flag: String) {
        fun toMap(): Map<String, String> = mapOf("flag" to flag)
    }

    private class RegUserMsg(val teamId: Int, val username: String, val firstName: String, val lastName: String) {
        companion object {
            fun fromIntent(intent: Intent): ResultS<RegUserMsg> =
                mapN(
                    intent.extractTeamId(),
                    intent.getOr("username", "bad req: no username"),
                    intent.getOr("first_name", "bad req: no first_name"),
                    intent.getOr("last_name", "bad req: no last_name")
                ) { teamId, username, firstName, lastName ->
                    RegUserMsg(teamId, username, firstName, lastName)
                }
        }
    }

    private class RegUserAns(val token: String, val session: String) {
        fun toMap(): Map<String, String> = mapOf(
            "token" to token,
            "session" to session
        )
    }

    private class BackupMsg(val teamId: Int, val session: String) {
        companion object {
            fun fromIntent(intent: Intent): ResultS<BackupMsg> =
            mapN(
                intent.extractTeamId(),
                intent.getOr("session", "bad_req: no session")
            ) { teamId, session ->
                BackupMsg(teamId, session)
            }
        }
    }
}
