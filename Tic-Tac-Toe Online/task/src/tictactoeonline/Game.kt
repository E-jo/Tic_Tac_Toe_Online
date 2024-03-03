package tictactoeonline

/*

This was my console version for stage 1

var player1 = "Player1"
var player2 = "Player2"

fun main() {

    // Get player names
    println("Enter the first player's name (Player1 by default)")
    player1 = readlnOrNull()?.takeIf { it.isNotBlank() } ?: "Player1"
    println("First player's name: $player1")

    println("Enter the second player's name (Player2 by default)")
    player2 = readlnOrNull()?.takeIf { it.isNotBlank() } ?: "Player2"
    println("Second player's name: $player2")

    // Get game field size
    var rows = 3
    var cols = 3

    println("\nEnter the field size (3x3 by default)")
    val sizeInput = readlnOrNull()

    if (!sizeInput.isNullOrBlank() && sizeInput.matches(Regex("\\d+x\\d+"))) {
        val (rowStr, colStr) = sizeInput.split("x")
        val row = rowStr.toInt()
        val col = colStr.toInt()

        if (!(row < 3 && col < 3)) {
            rows = row
            cols = col
        }
    }

    println("Field size: ${rows}x$cols")

    // Initialize game board
    val board = Array(rows) { Array(cols) { ' ' } }

    // Print initial board
    printBoard(board, true)

    // Game loop
    var currentPlayer = player1
    var movesLeft = rows * cols
    var winner: String? = null

    while (movesLeft > 0 && winner == null) {
        // Get player move
        var validMove = false
        while (!validMove) {
            println("Enter $currentPlayer's move as (x,y)")
            var row: Int? = null
            var col: Int? = null
            try {
                val input = readlnOrNull()
                val (rowStr, colStr) = input?.let {
                    val (r, c) = it.removeSurrounding("(", ")").split(",")
                    Pair(r, c)
                } ?: Pair("", "")

                row = rowStr.toIntOrNull()
                col = colStr.toIntOrNull()
            } catch (e: Exception) {
                println("Wrong move entered")
                continue
            }

            if (row != null && col != null) {
                row--
                col--
            } else {
                println("Wrong move entered")
                continue
            }
            if (row in 0 until rows && col in 0 until cols
                && board[row][col] == ' ') {
                board[row][col] = if (currentPlayer == player1) 'X' else 'O'
                validMove = true
            } else {
                println("Wrong move entered")
            }
        }

        // Print updated board
        printBoard(board, false)

        // Check for winner
        winner = checkWinner(board, rows, cols)

        // Switch players
        currentPlayer = if (currentPlayer == player1) player2 else player1
        movesLeft--
    }

    // Print result
    if (winner != null) {
        println("$winner wins!")
    } else {
        println("Draw!")
    }
}

fun printBoard(board: Array<Array<Char>>, firstPrint: Boolean) {
    val horizontalLine = "|---".repeat(board[0].size).plus("|")
    print(horizontalLine)
    if (firstPrint) {
        println("-y")
    } else {
        println()
    }
    for (row in board) {
        println("| ${row.joinToString(" | ")} |")
        println(horizontalLine)
    }
    if (firstPrint) {
        println("|")
        println("x\n")
    }
}

fun formatBoard(board: Array<Array<Char>>): Array<Array<Char>> {
    val horizontalLine = "|---".repeat(board[0].size).plus("|").toCharArray().toTypedArray()
    val formattedBoard = mutableListOf<Array<Char>>()
    formattedBoard.add(horizontalLine)

    for (row in board) {
        val formattedRow = ("| " + row.joinToString(" | ") + " |").toCharArray().toTypedArray()
        formattedBoard.add(formattedRow)
        formattedBoard.add(horizontalLine)
    }

    return formattedBoard.toTypedArray()
}


fun checkWinner(board: Array<Array<Char>>, rows: Int, cols: Int): String? {
    // Check horizontal
    for (row in 0 until rows) {
        for (col in 0 until cols - 2) {
            val symbol = board[row][col]
            if (symbol != ' ' && symbol == board[row][col + 1] && symbol == board[row][col + 2]) {
                return if (symbol == 'X') player1 else player2
            }
        }
    }

    // Check vertical
    for (col in 0 until cols) {
        for (row in 0 until rows - 2) {
            val symbol = board[row][col]
            if (symbol != ' ' && symbol == board[row + 1][col] && symbol == board[row + 2][col]) {
                return if (symbol == 'X') player1 else player2
            }
        }
    }

    // Check diagonal (top-left to bottom-right)
    for (row in 0 until rows - 2) {
        for (col in 0 until cols - 2) {
            val symbol = board[row][col]
            if (symbol != ' ' && symbol == board[row + 1][col + 1] && symbol == board[row + 2][col + 2]) {
                return if (symbol == 'X') player1 else player2
            }
        }
    }

    // Check diagonal (bottom-left to top-right)
    for (row in 2 until rows) {
        for (col in 0 until cols - 2) {
            val symbol = board[row][col]
            if (symbol != ' ' && symbol == board[row - 1][col + 1] && symbol == board[row - 2][col + 2]) {
                return if (symbol == 'X') player1 else player2
            }
        }
    }

    return null
}



 */