package br.com.pilovieira.launcher.snake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.pilovieira.launcher.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

private const val COLS = 16
private const val ROWS = 24
private const val INITIAL_TICK_MS = 180L
private const val MIN_TICK_MS = 80L

private data class Cell(val x: Int, val y: Int)

private enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0)
}

private class SnakeGame {
    var snake by mutableStateOf(listOf(Cell(COLS / 2, ROWS / 2), Cell(COLS / 2 - 1, ROWS / 2), Cell(COLS / 2 - 2, ROWS / 2)))
        private set
    var food by mutableStateOf(Cell(COLS / 4, ROWS / 4))
        private set
    var score by mutableStateOf(0)
        private set
    var gameOver by mutableStateOf(false)
        private set

    private var direction = Direction.RIGHT
    private var pendingDirection = Direction.RIGHT

    fun setDirection(newDirection: Direction) {
        val opposite = direction.dx == -newDirection.dx && direction.dy == -newDirection.dy
        if (!opposite) pendingDirection = newDirection
    }

    fun tickDelayMs(): Long = maxOf(MIN_TICK_MS, INITIAL_TICK_MS - (score / 3) * 10L)

    fun step() {
        if (gameOver) return
        direction = pendingDirection

        val head = snake.first()
        val newHead = Cell(
            x = (head.x + direction.dx + COLS) % COLS,
            y = (head.y + direction.dy + ROWS) % ROWS
        )

        if (snake.contains(newHead)) {
            gameOver = true
            return
        }

        val ateFood = newHead == food
        val newSnake = listOf(newHead) + if (ateFood) snake else snake.dropLast(1)
        snake = newSnake

        if (ateFood) {
            score += 1
            spawnFood()
        }
    }

    private fun spawnFood() {
        var candidate: Cell
        do {
            candidate = Cell(Random.nextInt(COLS), Random.nextInt(ROWS))
        } while (snake.contains(candidate))
        food = candidate
    }

    fun reset() {
        snake = listOf(Cell(COLS / 2, ROWS / 2), Cell(COLS / 2 - 1, ROWS / 2), Cell(COLS / 2 - 2, ROWS / 2))
        direction = Direction.RIGHT
        pendingDirection = Direction.RIGHT
        score = 0
        gameOver = false
        spawnFood()
    }
}

class SnakeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SnakeScreen()
        }
    }
}

@Composable
private fun SnakeScreen() {
    val game = remember { SnakeGame() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(game.gameOver) {
        while (!game.gameOver) {
            delay(game.tickDelayMs())
            game.step()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        Text(
            text = stringResource(R.string.snake_score, game.score),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        SnakeBoard(
            snake = game.snake,
            food = game.food,
            onSwipe = { direction -> game.setDirection(direction) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }

    if (game.gameOver) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.snake_game_over_title)) },
            text = { Text(stringResource(R.string.snake_game_over_message, game.score)) },
            confirmButton = {
                TextButton(onClick = { game.reset() }) {
                    Text(stringResource(R.string.snake_play_again))
                }
            },
            dismissButton = {
                TextButton(onClick = { (lifecycleOwner as? ComponentActivity)?.finish() }) {
                    Text(stringResource(R.string.snake_exit))
                }
            }
        )
    }
}

@Composable
private fun SnakeBoard(
    snake: List<Cell>,
    food: Cell,
    onSwipe: (Direction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(COLS.toFloat() / ROWS.toFloat())
            .pointerInput(Unit) {
                var dragStart = Offset.Zero
                detectDragGestures(
                    onDragStart = { dragStart = it },
                    onDragEnd = {}
                ) { change, _ ->
                    val dx = change.position.x - dragStart.x
                    val dy = change.position.y - dragStart.y
                    if (abs(dx) > abs(dy)) {
                        onSwipe(if (dx > 0) Direction.RIGHT else Direction.LEFT)
                    } else {
                        onSwipe(if (dy > 0) Direction.DOWN else Direction.UP)
                    }
                    dragStart = change.position
                }
            }
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawBoard(snake, food)
        }
    }
}

private fun DrawScope.drawBoard(snake: List<Cell>, food: Cell) {
    val cellWidth = size.width / COLS
    val cellHeight = size.height / ROWS
    val gap = 2f

    fun drawCell(cell: Cell) {
        drawRect(
            color = Color.White,
            topLeft = Offset(cell.x * cellWidth + gap / 2, cell.y * cellHeight + gap / 2),
            size = androidx.compose.ui.geometry.Size(cellWidth - gap, cellHeight - gap)
        )
    }

    drawCell(food)
    snake.forEach { drawCell(it) }
}
