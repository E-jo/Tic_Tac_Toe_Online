package tictactoeonline

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.min
import kotlin.random.Random

// TODO: make a TODO list of improvements to implement now that the tests are passed
/*
1.
 */

const val secretKey = "ut920BwH09AOEDx5"
const val pageSize = 10

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module(testing: Boolean = false) {

    // Install ContentNegotiation plugin for JSON serialization and deserialization
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true // Optional configuration
            encodeDefaults = true
            prettyPrint = true
            isLenient = true
        })
    }

    install(Authentication) {
        jwt("myAuth") {
            verifier(
                JWT
                    .require(Algorithm.HMAC256(secretKey))
                    .build()
            )
            validate { credential ->
                if (credential.payload.claims.isNotEmpty()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge {_, realm ->
                call.respondText("{\"status\":\"Authorization failed\"}",
                    status = HttpStatusCode.Unauthorized)
            }
        }
    }

    Database.connect(
        "jdbc:h2:file:./build/db",
        "org.h2.Driver"
    )

    transaction {
        SchemaUtils.create(UserTable, GameTable)
    }

    var playerOne: String
    var playerTwo: String
    var gameOver: Boolean
    var winner: String?
    var status: String
    var rows: Int
    var cols: Int
    var fieldSize: String
    var board: Board
    var currentPlayer: String
    var movesLeft: Int

    routing {

        // open routes for signup/signin

        post("/signup") {
            val requestBody = call.receive<String>()
            println(requestBody)
            var email: String? = null
            var password: String? = null
            try {
                val jsonRequest = Json.parseToJsonElement(requestBody).jsonObject
                println(Json.encodeToString(jsonRequest))
                email = jsonRequest["email"]?.jsonPrimitive?.content
                password = jsonRequest["password"]?.jsonPrimitive?.content
            } catch(e: Exception) {
                println(e.message)
            }

            if (email.isNullOrBlank() || password.isNullOrBlank()) {
                call.respondText("{\"status\":\"Registration failed\"}", status = HttpStatusCode.Forbidden)
            } else {
                var findUser: ResultRow? = null
                transaction {
                    findUser = UserTable.select { UserTable.email eq email!! }
                        .firstOrNull()
                }
                if (findUser != null) {
                    println("Found existing user with email $email")
                    call.respondText("{\"status\":\"Registration failed\"}", status = HttpStatusCode.Forbidden)
                } else {
                    println(email)
                    println(password)
                    transaction {
                        UserTable.insert { user ->
                            user[UserTable.email] = email!!
                            user[UserTable.password] = password!!
                        }
                    }
                    call.respondText("{\"status\":\"Signed Up\"}", status = HttpStatusCode.OK)
                }
            }
        }

        post("/signin") {
            val requestBody = call.receive<String>()
            println(requestBody)
            var email: String? = null
            var password: String? = null

            try {
                val jsonRequest = Json.parseToJsonElement(requestBody).jsonObject
                email = jsonRequest["email"]?.jsonPrimitive?.content
                password = jsonRequest["password"]?.jsonPrimitive?.content
            } catch(e: Exception) {
                println(e.message)
            }

            if (email == null || password == null) {
                call.respondText("{\"status\":\"Authorization failed\"}", status = HttpStatusCode.Forbidden)
            } else {
                var findUser: ResultRow? = null
                transaction {
                    findUser = UserTable.select { UserTable.email eq email!! }
                        .andWhere { UserTable.password eq password!! }
                        .firstOrNull()
                }
                if (findUser == null) {
                    call.respondText("{\"status\":\"Authorization failed\"}", status = HttpStatusCode.Forbidden)
                } else {
                    val token = JWT.create()
                        .withHeader(mapOf("alg" to "HS256", "typ" to "JWT"))
                        .withClaim("email", email)
                        .sign(Algorithm.HMAC256(secretKey))

                    val response = "{\"status\":\"Signed In\",\"token\":\"$token\"}"
                    call.respondText(response, status = HttpStatusCode.OK)
                }
            }
        }

        // authenticated routes for game interactions

        authenticate("myAuth") {
            get("/validate") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim("email").asString()

                call.respondText("Hello, $username")
            }

            get("/games") {
                println("/games")
                var page = 0
                call.parameters["page"]?.let {
                    page = it.toInt()
                }
                val gameList = mutableListOf<Game>()
                val totalElements = transaction {
                    GameTable.selectAll().count().toInt()
                }

                println("$totalElements total elements")
                val remainder = totalElements % pageSize
                val intDiv = totalElements / pageSize
                println("intDiv: $intDiv")
                println("remainder: $remainder")

                val totalPages = if (remainder != 0) intDiv + 1 else intDiv
                val numberOfElements =
                    if (page == (totalPages - 1) && remainder != 0) {
                        remainder
                    } else {
                        min(pageSize, totalElements)
                    }

                println("page: $page")
                val pageOffset = min((page * pageSize), totalElements - 1)

                transaction {
                    val allGames = GameTable.selectAll().limit(10, pageOffset.toLong())
                    for (row in allGames) {
                        val game = Game(
                            row[GameTable.gameId],
                            row[GameTable.player1],
                            row[GameTable.player2],
                            row[GameTable.size],
                            row[GameTable.private]
                        )
                        gameList.add(game)
                    }
                }

                val contentJsonArray = buildJsonArray {
                    gameList.forEach { game ->
                        add(buildJsonObject {
                            put("game_id", game.gameId)
                            put("player1", game.player1)
                            put("player2", game.player2)
                            put("size", game.size)
                            put("private", game.private)
                        })
                    }
                }

                val currentPage = History(totalPages, totalElements, page,
                    pageSize, numberOfElements, contentJsonArray)

                call.respond(HttpStatusCode.OK, currentPage)
            }

            get("/games/my") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal!!.payload.getClaim("email").asString()

                var page = 0
                call.parameters["page"]?.let {
                    page = it.toInt()
                }
                val gameStatusList = mutableListOf<GameStatus>()

                val totalElements = transaction {
                    GameTable.select {
                        GameTable.player1 eq email
                    }.orWhere {
                        GameTable.player2 eq email
                    }.count().toInt()
                }
                println("Found $totalElements games involving $email in GameTable")

                val remainder = totalElements % pageSize
                val intDiv = totalElements / pageSize
                val totalPages = if (remainder == 0) intDiv else intDiv + 1
                val numberOfElements =
                    if (page == (totalPages - 1) && remainder != 0) {
                        remainder
                    } else {
                        min(pageSize, totalElements)
                    }

                val pageOffset = min((page * pageSize), totalElements - 1)
                transaction {
                    val allGamesWithUser = GameTable.select {
                            GameTable.player1 eq email
                        }.orWhere {
                            GameTable.player2 eq email
                        }.limit(10, pageOffset.toLong())

                    for (row in allGamesWithUser) {
                        val gameStatus = GameStatus(
                            row[GameTable.gameId],
                            row[GameTable.status],
                            Json.decodeFromString<Board>(row[GameTable.field]),
                            row[GameTable.player1],
                            row[GameTable.player2],
                            row[GameTable.size],
                            row[GameTable.private],
                            row[GameTable.token]
                        )
                        gameStatusList.add(gameStatus)
                    }
                }

                val currentPage = UserHistory(totalPages, totalElements, page,
                    pageSize, numberOfElements, gameStatusList)

                call.respond(HttpStatusCode.OK, currentPage)
            }

            post("/game") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal!!.payload.getClaim("email").asString()

                val requestBody = call.receive<String>()
                println(requestBody)
                val jsonRequest = Json.parseToJsonElement(requestBody).jsonObject

                playerOne = jsonRequest["player1"]?.jsonPrimitive?.content ?: ""
                playerTwo = jsonRequest["player2"]?.jsonPrimitive?.content ?: ""
                fieldSize = jsonRequest["size"]?.jsonPrimitive?.content ?: "3x3"
                val private = jsonRequest["private"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

                if (playerOne != email && playerTwo != email) {
                    call.respondText("{\"status\":\"Creating a game failed\"}", status = HttpStatusCode.Forbidden)
                    return@post
                }

                var token = ""
                if (private) {
                    val charPool : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
                    token = (1..32)
                        .map { Random.nextInt(0, charPool.size) }
                        .map(charPool::get)
                        .joinToString("")
                }

                // reset and then validate the fieldSize
                rows = 3
                cols = 3

                if (fieldSize.isNotBlank() && fieldSize.matches(Regex("\\d+x\\d+"))) {
                    val (rowStr, colStr) = fieldSize.split("x")
                    val row = rowStr.toInt()
                    val col = colStr.toInt()

                    if (!(row < 3 && col < 3)) {
                        rows = row
                        cols = col
                    }
                }

                board = Board(Array(rows) { Array(cols) { ' ' } })
                movesLeft = rows * cols
                currentPlayer = playerOne
                gameOver = false

                // this is the status we will save to the db. the special one-time status of
                // 'New game started' shouldn't be saved
                status = "game not started"
                val size = "${rows}x$cols"

                val maxGameId = transaction {
                    GameTable
                        .slice(GameTable.gameId.max())
                        .selectAll()
                        .map { it[GameTable.gameId.max()] }
                        .singleOrNull() ?: 0
                }
                val currentGameId = maxGameId + 1

                val newGame = Game(currentGameId, playerOne, playerTwo, size, private)
                val newGameStatus = GameStatus(currentGameId, status, board, playerOne, playerTwo, size,
                    private, token, null, playerOne, false, movesLeft)


                println("Adding game to GameTable")
                transaction {
                    GameTable.insert { game ->
                        game[GameTable.gameId] = currentGameId
                        game[GameTable.status] = status
                        game[GameTable.field] = Json.encodeToString(board)
                        game[GameTable.player1] = playerOne
                        game[GameTable.player2] = playerTwo
                        game[GameTable.size] = size
                        game[GameTable.private] = private
                        game[GameTable.token] = token
                        game[GameTable.winner] = null
                        game[GameTable.currentPlayer] = playerOne
                        game[GameTable.gameOver] = false
                        game[GameTable.movesLeft] = movesLeft
                    }
                }
                println("Successfully added game to GameTable")
                println(newGame)

                val response = buildJsonObject {
                    put("game_id", currentGameId)
                    put("status", "New game started")
                    put("player1", playerOne)
                    put("player2", playerTwo)
                    put("size", size)
                    put("private", private)
                    put("token", token)
                }

                call.respond(HttpStatusCode.OK, response)
            }

            post("/game/{game_id}/join") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal!!.payload.getClaim("email").asString()
                val gameId = call.parameters["game_id"]!!.toInt()

                println("$email attempting to join game $gameId")

                var findGame: ResultRow? = null
                transaction {
                    findGame = GameTable.select { GameTable.gameId eq gameId }
                        .firstOrNull()
                }
                if (findGame == null) {
                    call.respondText("{\"status\":\"Joining the game failed\"}", status = HttpStatusCode.Forbidden)
                    return@post
                }

                var gameStatus: GameStatus? = null
                println("Retrieving game from GameTable")
                transaction {
                    val gameIdNew = findGame!![GameTable.gameId]
                    val statusNew = findGame!![GameTable.status]
                    val fieldNew = Json.decodeFromString<Board>(findGame!![GameTable.field])
                    val player1New = findGame!![GameTable.player1]
                    val player2New = findGame!![GameTable.player2]
                    val sizeNew = findGame!![GameTable.size]
                    val privateNew = findGame!![GameTable.private]
                    val tokenNew = findGame!![GameTable.token]
                    val winnerNew = findGame!![GameTable.winner]
                    val currentPlayerNew = findGame!![GameTable.currentPlayer]
                    val gameOverNew = findGame!![GameTable.gameOver]
                    val movesLeftNew = findGame!![GameTable.movesLeft]

                    gameStatus = GameStatus(gameIdNew, statusNew, fieldNew, player1New, player2New,
                        sizeNew, privateNew, tokenNew, winnerNew, currentPlayerNew,
                        gameOverNew, movesLeftNew)
                }

                if (gameStatus != null) {
                    println("Successfully retrieved game from GameTable")
                    println(Json.encodeToString(gameStatus))
                    for (row in gameStatus!!.field.boardState) {
                        for (e in row) {
                            print("\"$e\"")
                        }
                        println()
                    }
                    if (gameStatus!!.private) {
                        try {
                            val suppliedToken = call.parameters["token"]

                            if (suppliedToken != gameStatus!!.token) {
                                call.respondText("{\"status\":\"Joining the game failed\"}", status = HttpStatusCode.Forbidden)
                                println("$email supplied incorrect token for game $gameId")
                                return@post
                            }
                        } catch (e: NullPointerException) {
                            println("NPE")
                            call.respondText("{\"status\":\"Joining the game failed\"}", status = HttpStatusCode.Forbidden)
                            return@post
                        }
                    }

                    println("Looking for a place for $email")
                    if (gameStatus!!.player1.isEmpty()) {
                        gameStatus!!.player1 = email
                        println("$email joining as player1")

                        transaction {
                            GameTable.update({ GameTable.gameId eq gameStatus!!.gameId }) {
                                it[GameTable.player1] = email
                            }
                        }
                        println("Added $email to player1")

                        call.respondText("{\"status\":\"Joining the game succeeded\"}", status = HttpStatusCode.OK)
                    } else if (gameStatus!!.player2.isEmpty()) {
                        gameStatus!!.player2 = email
                        println("$email joining as player2")

                        transaction {
                            GameTable.update({ GameTable.gameId eq gameStatus!!.gameId }) {
                                it[GameTable.player2] = email
                            }
                        }
                        println("Added $email to player2")


                        call.respondText("{\"status\":\"Joining the game succeeded\"}", status = HttpStatusCode.OK)
                    } else {
                        println("$email couldn't join")
                        call.respondText("{\"status\":\"Joining the game failed\"}", status = HttpStatusCode.Forbidden)
                    }

                    // make sure we have correct currentPlayer, in case player1 was second to join
                    if (gameStatus!!.player1.isNotEmpty() && gameStatus!!.player2.isNotEmpty()) {
                        gameStatus!!.currentPlayer = gameStatus!!.player1
                        transaction {
                            GameTable.update({ GameTable.gameId eq gameStatus!!.gameId }) {
                                it[GameTable.currentPlayer] = gameStatus!!.player1
                                it[GameTable.status] = "1st player's move"
                            }
                        }
                        println("Updated currentPlayer")
                    }

                } else {
                    println("gameStatus is null")
                }
            }

            post("/game/{game_id}/join/{token}") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal!!.payload.getClaim("email").asString()
                val gameId = call.parameters["game_id"]!!.toInt()

                println("$email attempting to join game $gameId")

                var findGame: ResultRow? = null
                transaction {
                    findGame = GameTable.select { GameTable.gameId eq gameId }
                        .firstOrNull()
                }
                if (findGame == null) {
                    call.respondText("{\"status\":\"Joining the game failed\"}", status = HttpStatusCode.Forbidden)
                    return@post
                }

                var gameStatus: GameStatus? = null
                println("Retrieving game from GameTable")
                transaction {
                    val gameIdNew = findGame!![GameTable.gameId]
                    val statusNew = findGame!![GameTable.status]
                    val fieldNew = Json.decodeFromString<Board>(findGame!![GameTable.field])
                    val player1New = findGame!![GameTable.player1]
                    val player2New = findGame!![GameTable.player2]
                    val sizeNew = findGame!![GameTable.size]
                    val privateNew = findGame!![GameTable.private]
                    val tokenNew = findGame!![GameTable.token]
                    val winnerNew = findGame!![GameTable.winner]
                    val currentPlayerNew = findGame!![GameTable.currentPlayer]
                    val gameOverNew = findGame!![GameTable.gameOver]
                    val movesLeftNew = findGame!![GameTable.movesLeft]

                    gameStatus = GameStatus(gameIdNew, statusNew, fieldNew, player1New, player2New,
                        sizeNew, privateNew, tokenNew, winnerNew, currentPlayerNew,
                        gameOverNew, movesLeftNew)
                }

                if (gameStatus != null) {
                    println("Successfully retrieved game from GameTable")
                    println(Json.encodeToString(gameStatus))
                    for (row in gameStatus!!.field.boardState) {
                        for (e in row) {
                            print("\"$e\"")
                        }
                        println()
                    }
                    if (gameStatus!!.private) {
                        try {
                            val suppliedToken = call.parameters["token"]
                            if (suppliedToken == "fr67sl4g5fltwwsgjl4ftyj9t20062ia") {
                                println("token: fr67sl4g5fltwwsgjl4ftyj9t20062ia")
                            }
                            println(suppliedToken)
                            if (suppliedToken != gameStatus!!.token) {
                                call.respondText("{\"status\":\"Joining the game failed\"}", status = HttpStatusCode.Forbidden)
                                println("$email supplied incorrect token for game $gameId")
                                return@post
                            }
                        } catch (e: NullPointerException) {
                            println("NPE")
                            call.respondText("{\"status\":\"Joining the game failed\"}", status = HttpStatusCode.Forbidden)
                            return@post
                        }
                    }

                    println("Looking for a place for $email")
                    if (gameStatus!!.player1.isEmpty()) {
                        gameStatus!!.player1 = email
                        println("$email joining as player1")

                        transaction {
                            GameTable.update({ GameTable.gameId eq gameStatus!!.gameId }) {
                                it[GameTable.player1] = email
                            }
                        }
                        println("Added $email to player1")

                        call.respondText("{\"status\":\"Joining the game succeeded\"}", status = HttpStatusCode.OK)
                    } else if (gameStatus!!.player2.isEmpty()) {
                        gameStatus!!.player2 = email
                        println("$email joining as player2")

                        transaction {
                            GameTable.update({ GameTable.gameId eq gameStatus!!.gameId }) {
                                it[GameTable.player2] = email
                            }
                        }
                        println("Added $email to player2")


                        call.respondText("{\"status\":\"Joining the game succeeded\"}", status = HttpStatusCode.OK)
                    } else {
                        println("$email couldn't join")
                        call.respondText("{\"status\":\"Joining the game failed\"}", status = HttpStatusCode.Forbidden)
                    }

                    // make sure we have correct currentPlayer, in case player1 was second to join
                    if (gameStatus!!.player1.isNotEmpty() && gameStatus!!.player2.isNotEmpty()) {
                        gameStatus!!.currentPlayer = gameStatus!!.player1
                        transaction {
                            GameTable.update({ GameTable.gameId eq gameStatus!!.gameId }) {
                                it[GameTable.currentPlayer] = gameStatus!!.player1
                                it[GameTable.status] = "1st player's move"
                            }
                        }
                        println("Updated currentPlayer")
                    }

                } else {
                    println("gameStatus is null")
                }
            }

            get("/game/{game_id}/status") {
                val gameId = call.parameters["game_id"]!!.toInt()
                println("/game/$gameId/status")

                var findGame: ResultRow? = null
                transaction {
                    findGame = GameTable.select { GameTable.gameId eq gameId }
                        .firstOrNull()
                }
                if (findGame == null) {
                    call.respondText("{\"status\":\"No game found with id $gameId\"}",
                        status = HttpStatusCode.NotFound)
                    return@get
                }

                var gameStatus: GameStatus? = null
                println("Retrieving game from GameTable")
                transaction {
                    val statusNew = findGame!![GameTable.status]
                    val fieldNew = Json.decodeFromString<Board>(findGame!![GameTable.field])
                    val player1New = findGame!![GameTable.player1]
                    val player2New = findGame!![GameTable.player2]
                    val sizeNew = findGame!![GameTable.size]
                    val privateNew = findGame!![GameTable.private]
                    val tokenNew = findGame!![GameTable.token]
                    val winnerNew = findGame!![GameTable.winner]
                    val currentPlayerNew = findGame!![GameTable.currentPlayer]
                    val gameOverNew = findGame!![GameTable.gameOver]
                    val movesLeftNew = findGame!![GameTable.movesLeft]

                    gameStatus = GameStatus(gameId, statusNew, fieldNew, player1New, player2New,
                        sizeNew, privateNew, tokenNew, winnerNew, currentPlayerNew,
                        gameOverNew, movesLeftNew)
                }

                if (gameStatus == null) {
                    println("403 Forbidden: gameStatus is null")
                    call.respondText("{\"status\":\"Failed to get game status\"}", status = HttpStatusCode.Forbidden)
                    return@get
                }

                println("Successfully retrieved game from GameTable")
                for (row in gameStatus!!.field.boardState) {
                    for (e in row) {
                        print("\"$e\"")
                    }
                    println()
                }

                playerOne = gameStatus!!.player1
                playerTwo = gameStatus!!.player2

                val principal = call.principal<JWTPrincipal>()
                val email = principal!!.payload.getClaim("email").asString()

                if (email != playerOne && email != playerTwo) {
                    println("403 Forbidden: $email is not in game. player1: $playerOne, player2:$playerTwo")
                    call.respondText("{\"status\":\"Failed to get game status\"}", status = HttpStatusCode.Forbidden)
                    return@get
                }

                status = gameStatus!!.status
                winner = gameStatus!!.winner
                board = gameStatus!!.field
                rows = board.boardState.size
                cols = board.boardState[0].size
                gameOver = gameStatus!!.gameOver
                currentPlayer = gameStatus!!.currentPlayer


                // first check if game is waiting for a player so that we don't change the status prematurely
                if (playerOne.isEmpty() || playerTwo.isEmpty()) {
                    println(Json.encodeToString(gameStatus))
                    call.respond(HttpStatusCode.OK, Json.encodeToString(gameStatus))
                    return@get
                }

                // don't check for winner again if the game is over
                if (winner == null) {
                    gameStatus!!.winner = when (checkWinner(board.boardState, rows, cols)) {
                        'X' -> playerOne
                        'O' -> playerTwo
                        else -> null
                    }
                }

                if (gameStatus!!.winner == null) {
                    gameStatus!!.status = if (currentPlayer == playerOne) "1st player's move" else "2nd player's move"
                } else {
                    when (gameStatus!!.winner) {
                        playerOne -> {
                            gameStatus!!.status = "1st player won"
                            gameStatus!!.gameOver = true
                        }

                        playerTwo -> {
                            gameStatus!!.status = "2nd player won"
                            gameStatus!!.gameOver = true
                        }

                        "draw" -> {
                            gameStatus!!.status = "draw"
                            gameStatus!!.gameOver = true
                        }
                        else -> {}
                    }
                }

                transaction {
                    GameTable.update({ GameTable.gameId eq gameId }) {
                        it[GameTable.status] = gameStatus!!.status
                        it[GameTable.winner] = gameStatus!!.winner
                        it[GameTable.gameOver] = gameStatus!!.gameOver
                    }
                }
                println("Updated status of game $gameId")
                println(Json.encodeToString(gameStatus))
                call.respond(HttpStatusCode.OK, Json.encodeToString(gameStatus))
            }

            post("/game/{game_id}/move") {
                val gameId = call.parameters["game_id"]!!.toInt()

                var findGame: ResultRow? = null
                transaction {
                    findGame = GameTable.select { GameTable.gameId eq gameId }
                        .firstOrNull()
                }
                if (findGame == null) {
                    call.respondText("{\"status\":\"Joining the game failed\"}", status = HttpStatusCode.Forbidden)
                    return@post
                }

                var gameStatus: GameStatus? = null
                println("Retrieving game from GameTable")
                transaction {
                    val gameIdNew = findGame!![GameTable.gameId]
                    val statusNew = findGame!![GameTable.status]
                    val fieldNew = Json.decodeFromString<Board>(findGame!![GameTable.field])
                    val player1New = findGame!![GameTable.player1]
                    val player2New = findGame!![GameTable.player2]
                    val sizeNew = findGame!![GameTable.size]
                    val privateNew = findGame!![GameTable.private]
                    val tokenNew = findGame!![GameTable.token]
                    val winnerNew = findGame!![GameTable.winner]
                    val currentPlayerNew = findGame!![GameTable.currentPlayer]
                    val gameOverNew = findGame!![GameTable.gameOver]
                    val movesLeftNew = findGame!![GameTable.movesLeft]

                    gameStatus = GameStatus(gameIdNew, statusNew, fieldNew, player1New, player2New,
                        sizeNew, privateNew, tokenNew, winnerNew, currentPlayerNew,
                        gameOverNew, movesLeftNew)
                }

                if (gameStatus == null) {
                    call.respondText("{\"status\":\"Failed to get game status\"}", status = HttpStatusCode.Forbidden)
                    return@post
                }

                playerOne = gameStatus!!.player1
                playerTwo = gameStatus!!.player2
                currentPlayer = gameStatus!!.currentPlayer

                val principal = call.principal<JWTPrincipal>()
                val email = principal!!.payload.getClaim("email").asString()

                if (email != currentPlayer) {
                    call.respondText("{\"status\":\"You have no rights to make this move\"}", status = HttpStatusCode.Forbidden)
                    return@post
                }

                status = gameStatus!!.status
                gameOver = gameStatus!!.gameOver

                if (gameOver) {
                    println("game over")
                    call.respondText("{\"status\":\"You have no rights to make this move\"}", status = HttpStatusCode.Forbidden)
                    return@post
                }

                winner = gameStatus!!.winner
                board = gameStatus!!.field
                rows = board.boardState.size
                cols = board.boardState[0].size

                val requestBody = call.receive<String>()
                println(requestBody)
                var resultString = ""
                try {
                    val jsonRequest = Json.parseToJsonElement(requestBody).jsonObject
                    val move = jsonRequest["move"]?.jsonPrimitive?.content ?: ""

                    var row: Int? = null
                    var col: Int? = null
                    try {
                        val (rowStr, colStr) = move.let {
                            val (r, c) = it.removeSurrounding("(", ")").split(",")
                            Pair(r, c)
                        }

                        row = rowStr.toIntOrNull()
                        col = colStr.toIntOrNull()
                    } catch (e: Exception) {
                        println(e.message)
                    }

                    if (row == null || col == null) {
                        resultString = "Incorrect or impossible move"
                    } else {
                        row--
                        col--
                        if (row in 0 until rows && col in 0 until cols
                            && board.boardState[row][col] == ' '
                        ) {
                            gameStatus!!.field.boardState[row][col] = if (currentPlayer == playerOne) 'X' else 'O'
                            resultString = "Move done"
                            gameStatus!!.movesLeft--
                            println("Moves left: ${gameStatus!!.movesLeft}")
                            gameStatus!!.winner = when (checkWinner(board.boardState, rows, cols)) {
                                'X' -> playerOne
                                'O' -> playerTwo
                                else -> null
                            }

                            // only switch turns if there isn't a winner
                            if (gameStatus!!.winner == null) {
                                // first check for draw
                                if (gameStatus!!.movesLeft == 0) {
                                    gameStatus!!.winner = "draw"
                                    gameStatus!!.gameOver = true
                                } else {
                                    println("Switching players")
                                    gameStatus!!.currentPlayer = if (currentPlayer == playerOne) playerTwo else playerOne
                                }
                            } else {
                                println("game over, ${gameStatus!!.winner} wins")
                                gameStatus!!.gameOver = true
                            }
                        } else {
                            resultString = "Incorrect or impossible move"
                        }
                    }
                } catch (e: Exception) {
                    call.respondText("Invalid JSON format", status = HttpStatusCode.BadRequest)
                }
                if (resultString == "Incorrect or impossible move") {
                    call.respondText("{\"status\":\"$resultString\"}", status = HttpStatusCode.BadRequest)
                } else {
                    call.respondText("{\"status\":\"$resultString\"}", status = HttpStatusCode.OK)
                }

                transaction {
                    GameTable.update({ GameTable.gameId eq gameId }) {
                        it[GameTable.status] = gameStatus!!.status
                        it[GameTable.movesLeft] = gameStatus!!.movesLeft
                        it[GameTable.winner] = gameStatus!!.winner
                        it[GameTable.gameOver] = gameStatus!!.gameOver
                        it[GameTable.currentPlayer] = gameStatus!!.currentPlayer
                        it[GameTable.gameOver] = gameStatus!!.gameOver
                        it[GameTable.field] = Json.encodeToString(gameStatus!!.field)
                    }
                }
                println("Updated status of game $gameId")
            }
        }
    }

    TransactionManager.currentOrNull()?.connection?.close()
}

fun checkWinner(board: Array<Array<Char>>, rows: Int, cols: Int): Char? {
    // horizontal
    for (row in 0 until rows) {
        for (col in 0 until cols - 2) {
            val symbol = board[row][col]
            if (symbol != ' ' && symbol == board[row][col + 1] && symbol == board[row][col + 2]) {
                return symbol
            }
        }
    }

    // vertical
    for (col in 0 until cols) {
        for (row in 0 until rows - 2) {
            val symbol = board[row][col]
            if (symbol != ' ' && symbol == board[row + 1][col] && symbol == board[row + 2][col]) {
                return symbol
            }
        }
    }

    // diagonal (top-left to bottom-right)
    for (row in 0 until rows - 2) {
        for (col in 0 until cols - 2) {
            val symbol = board[row][col]
            if (symbol != ' ' && symbol == board[row + 1][col + 1] && symbol == board[row + 2][col + 2]) {
                return symbol
            }
        }
    }

    // diagonal (bottom-left to top-right)
    for (row in 2 until rows) {
        for (col in 0 until cols - 2) {
            val symbol = board[row][col]
            if (symbol != ' ' && symbol == board[row - 1][col + 1] && symbol == board[row - 2][col + 2]) {
                return symbol
            }
        }
    }

    return null
}

@Serializable
data class History(
    val totalPages: Int,
    val totalElements: Int,
    val page: Int,
    val size: Int,
    val numberOfElements: Int,
    val content: JsonArray
)

@Serializable
data class UserHistory(
    val totalPages: Int,
    val totalElements: Int,
    val page: Int,
    val size: Int,
    val numberOfElements: Int,
    val content: MutableList<GameStatus>
)

@Serializable
data class Game(
    @SerialName("game_id")
    val gameId: Int,
    var player1: String,
    var player2: String,
    val size: String,
    val private: Boolean
)

@Serializable
data class Board(val boardState: Array<Array<Char>>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Board

        return boardState.contentDeepEquals(other.boardState)
    }

    override fun hashCode(): Int {
        return boardState.contentDeepHashCode()
    }
}

@Serializable
data class GameStatus(
    @SerialName("game_id")
    val gameId: Int,
    @SerialName("game_status")
    var status: String,
    @Serializable(with = BoardSerializer::class)
    val field: Board,
    var player1: String,
    var player2: String,
    val size: String,
    val private: Boolean,
    val token: String,
    @Transient
    var winner: String? = null,
    @Transient
    var currentPlayer: String = "",
    @Transient
    var gameOver: Boolean = false,
    @Transient
    var movesLeft: Int = Int.MAX_VALUE
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameStatus

        if (status != other.status) return false
        if (!field.boardState.contentDeepEquals(other.field.boardState)) return false
        if (player1 != other.player1) return false
        if (player2 != other.player2) return false
        if (size != other.size) return false

        return true
    }

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + field.boardState.contentDeepHashCode()
        result = 31 * result + player1.hashCode()
        result = 31 * result + player2.hashCode()
        result = 31 * result + size.hashCode()
        return result
    }
}

object BoardSerializer : KSerializer<Board> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Board")

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Board) {
        encoder.encodeSerializableValue(ArraySerializer(ArraySerializer(Char.serializer())), value.boardState)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): Board {
        val boardState = decoder.decodeSerializableValue(ArraySerializer(ArraySerializer(Char.serializer())))
        return Board(boardState)
    }
}

object UserTable : IntIdTable() {
    val email: Column<String> = text("email")
    val password: Column<String> = text("password")
}

object GameTable : Table() {
    val gameId: Column<Int> = integer("game_id")
    val status: Column<String> = text("status")
    val field: Column<String> = text("field")
    val player1: Column<String> = text("player1")
    val player2: Column<String> = text("player2")
    val size: Column<String> = text("size")
    val private: Column<Boolean> = bool("private")
    val token: Column<String> = text("token")
    val winner: Column<String?> = text("winner").nullable()
    val currentPlayer: Column<String> = text("current_player")
    val gameOver: Column<Boolean> = bool("game_over")
    val movesLeft: Column<Int> = integer("moves_left")
}
