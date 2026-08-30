package br.com.pilovieira.launcher.snake

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.pilovieira.launcher.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

private const val COLS = 16
private const val ROWS = 24

private const val PREFS_NAME = "snake_prefs"
private const val PREF_CONTROL_MODE = "control_mode"
private const val PREF_SPEED_MODE = "speed_mode"
private const val PREF_SPEED_PROGRESSION = "speed_progression"
private const val PREF_HIGH_SCORE = "high_score"

private enum class ControlMode {
    SWIPE, BUTTONS
}

private enum class SpeedLevel(val initialTickMs: Long, val minTickMs: Long) {
    LOW(260L, 140L),
    MEDIUM(180L, 80L),
    HIGH(120L, 50L)
}

private enum class SpeedProgression {
    INCREASING, CONSTANT
}

private fun loadControlMode(context: Context): ControlMode {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val name = prefs.getString(PREF_CONTROL_MODE, ControlMode.SWIPE.name)
    return runCatching { ControlMode.valueOf(name ?: ControlMode.SWIPE.name) }.getOrDefault(ControlMode.SWIPE)
}

private fun saveControlMode(context: Context, mode: ControlMode) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_CONTROL_MODE, mode.name)
        .apply()
}

private fun loadSpeedLevel(context: Context): SpeedLevel {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val name = prefs.getString(PREF_SPEED_MODE, SpeedLevel.MEDIUM.name)
    return runCatching { SpeedLevel.valueOf(name ?: SpeedLevel.MEDIUM.name) }.getOrDefault(SpeedLevel.MEDIUM)
}

private fun saveSpeedLevel(context: Context, level: SpeedLevel) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_SPEED_MODE, level.name)
        .apply()
}

private fun loadSpeedProgression(context: Context): SpeedProgression {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val name = prefs.getString(PREF_SPEED_PROGRESSION, SpeedProgression.INCREASING.name)
    return runCatching { SpeedProgression.valueOf(name ?: SpeedProgression.INCREASING.name) }
        .getOrDefault(SpeedProgression.INCREASING)
}

private fun saveSpeedProgression(context: Context, progression: SpeedProgression) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_SPEED_PROGRESSION, progression.name)
        .apply()
}

private fun loadHighScore(context: Context): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(PREF_HIGH_SCORE, 0)
}

private fun saveHighScore(context: Context, score: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(PREF_HIGH_SCORE, score)
        .apply()
}

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

    var speedLevel = SpeedLevel.MEDIUM
    var speedProgression = SpeedProgression.INCREASING

    fun setDirection(newDirection: Direction) {
        val opposite = direction.dx == -newDirection.dx && direction.dy == -newDirection.dy
        if (!opposite) pendingDirection = newDirection
    }

    fun tickDelayMs(): Long = when (speedProgression) {
        SpeedProgression.CONSTANT -> speedLevel.initialTickMs
        SpeedProgression.INCREASING -> maxOf(speedLevel.minTickMs, speedLevel.initialTickMs - (score / 3) * 10L)
    }

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
    val context = LocalContext.current

    var controlMode by remember { mutableStateOf(loadControlMode(context)) }
    var speedLevel by remember { mutableStateOf(loadSpeedLevel(context)) }
    var speedProgression by remember { mutableStateOf(loadSpeedProgression(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var showGameOverDialog by remember { mutableStateOf(false) }
    var snakeColor by remember { mutableStateOf(Color.White) }
    var paused by remember { mutableStateOf(false) }
    var highScore by remember { mutableStateOf(loadHighScore(context)) }

    game.speedLevel = speedLevel
    game.speedProgression = speedProgression

    LaunchedEffect(game.gameOver) {
        while (!game.gameOver) {
            delay(game.tickDelayMs())
            if (!paused) game.step()
        }
    }

    LaunchedEffect(game.gameOver) {
        if (game.gameOver) {
            repeat(6) { i ->
                snakeColor = if (i % 2 == 0) Color.Red else Color.White
                delay(150)
            }
            snakeColor = Color.White
            if (game.score > highScore) {
                highScore = game.score
                saveHighScore(context, highScore)
            }
            showGameOverDialog = true
        } else {
            showGameOverDialog = false
            snakeColor = Color.White
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.snake_score, game.score),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.snake_high_score, highScore),
                    color = Color.Gray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            IconButton(onClick = { showSettings = true }) {
                Text(
                    text = "⚙",
                    color = Color.White,
                    fontSize = 22.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val boardRatio = COLS.toFloat() / ROWS.toFloat()
                val maxWidth = maxWidth
                val maxHeight = maxHeight
                val widthFromHeight = maxHeight * boardRatio
                val boardWidth = if (widthFromHeight <= maxWidth) widthFromHeight else maxWidth
                SnakeBoard(
                    snake = game.snake,
                    food = game.food,
                    snakeColor = snakeColor,
                    swipeEnabled = controlMode == ControlMode.SWIPE,
                    onSwipe = { direction -> game.setDirection(direction) },
                    modifier = Modifier.width(boardWidth)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111))
                .padding(vertical = 16.dp, horizontal = 16.dp)
        ) {
            if (controlMode == ControlMode.BUTTONS) {
                DirectionalPad(
                    onDirection = { direction -> game.setDirection(direction) },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            PauseButton(
                paused = paused,
                onToggle = { paused = !paused },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }

    if (showGameOverDialog) {
        AlertDialog(
            onDismissRequest = {},
            modifier = Modifier.border(1.dp, Color.White, androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            containerColor = Color.Black,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text(stringResource(R.string.snake_game_over_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.snake_game_over_message, game.score))
                    Text(stringResource(R.string.snake_high_score, highScore))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    game.reset()
                    paused = false
                }) {
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

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            modifier = Modifier.border(1.dp, Color.White, androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            containerColor = Color.Black,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text(stringResource(R.string.snake_settings_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.snake_settings_controls_section),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    ControlModeOption(
                        label = stringResource(R.string.snake_control_swipe),
                        selected = controlMode == ControlMode.SWIPE,
                        onSelect = {
                            controlMode = ControlMode.SWIPE
                            saveControlMode(context, ControlMode.SWIPE)
                        }
                    )
                    ControlModeOption(
                        label = stringResource(R.string.snake_control_buttons),
                        selected = controlMode == ControlMode.BUTTONS,
                        onSelect = {
                            controlMode = ControlMode.BUTTONS
                            saveControlMode(context, ControlMode.BUTTONS)
                        }
                    )
                    Text(
                        text = stringResource(R.string.snake_settings_speed_section),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                    ControlModeOption(
                        label = stringResource(R.string.snake_speed_low),
                        selected = speedLevel == SpeedLevel.LOW,
                        onSelect = {
                            speedLevel = SpeedLevel.LOW
                            saveSpeedLevel(context, SpeedLevel.LOW)
                        }
                    )
                    ControlModeOption(
                        label = stringResource(R.string.snake_speed_medium),
                        selected = speedLevel == SpeedLevel.MEDIUM,
                        onSelect = {
                            speedLevel = SpeedLevel.MEDIUM
                            saveSpeedLevel(context, SpeedLevel.MEDIUM)
                        }
                    )
                    ControlModeOption(
                        label = stringResource(R.string.snake_speed_high),
                        selected = speedLevel == SpeedLevel.HIGH,
                        onSelect = {
                            speedLevel = SpeedLevel.HIGH
                            saveSpeedLevel(context, SpeedLevel.HIGH)
                        }
                    )
                    Text(
                        text = stringResource(R.string.snake_settings_speed_progression_section),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                    ControlModeOption(
                        label = stringResource(R.string.snake_speed_progression_increasing),
                        selected = speedProgression == SpeedProgression.INCREASING,
                        onSelect = {
                            speedProgression = SpeedProgression.INCREASING
                            saveSpeedProgression(context, SpeedProgression.INCREASING)
                        }
                    )
                    ControlModeOption(
                        label = stringResource(R.string.snake_speed_progression_constant),
                        selected = speedProgression == SpeedProgression.CONSTANT,
                        onSelect = {
                            speedProgression = SpeedProgression.CONSTANT
                            saveSpeedProgression(context, SpeedProgression.CONSTANT)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text(stringResource(R.string.snake_settings_close))
                }
            }
        )
    }
}

@Composable
private fun ControlModeOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                selectedColor = Color.White,
                unselectedColor = Color.Gray
            )
        )
        Text(text = label, color = Color.White, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun DirectionalPad(
    onDirection: (Direction) -> Unit,
    modifier: Modifier = Modifier
) {
    val cell = 56.dp
    val gap = 4.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Spacer(modifier = Modifier.size(cell))
            DirectionalButton("▲", Direction.UP, onDirection)
            Spacer(modifier = Modifier.size(cell))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            DirectionalButton("◀", Direction.LEFT, onDirection)
            Spacer(modifier = Modifier.size(cell))
            DirectionalButton("▶", Direction.RIGHT, onDirection)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Spacer(modifier = Modifier.size(cell))
            DirectionalButton("▼", Direction.DOWN, onDirection)
            Spacer(modifier = Modifier.size(cell))
        }
    }
}

@Composable
private fun DirectionalButton(
    label: String,
    direction: Direction,
    onDirection: (Direction) -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(Color(0xFF333333), CircleShape)
            .clickable { onDirection(direction) },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = Color.White, fontSize = 20.sp)
    }
}

@Composable
private fun PauseButton(
    paused: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .background(Color(0xFF333333), CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
            if (paused) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, color = Color.White)
            } else {
                val barWidth = size.width * 0.3f
                drawRect(color = Color.White, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(barWidth, size.height))
                drawRect(
                    color = Color.White,
                    topLeft = Offset(size.width - barWidth, 0f),
                    size = androidx.compose.ui.geometry.Size(barWidth, size.height)
                )
            }
        }
    }
}

@Composable
private fun SnakeBoard(
    snake: List<Cell>,
    food: Cell,
    snakeColor: Color,
    swipeEnabled: Boolean,
    onSwipe: (Direction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(COLS.toFloat() / ROWS.toFloat())
            .pointerInput(swipeEnabled) {
                if (!swipeEnabled) return@pointerInput
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
            drawBoard(snake, food, snakeColor)
        }
    }
}

private fun DrawScope.drawBoard(snake: List<Cell>, food: Cell, snakeColor: Color) {
    val cellWidth = size.width / COLS
    val cellHeight = size.height / ROWS
    val gap = 2f

    fun drawCell(cell: Cell, color: Color) {
        drawRect(
            color = color,
            topLeft = Offset(cell.x * cellWidth + gap / 2, cell.y * cellHeight + gap / 2),
            size = androidx.compose.ui.geometry.Size(cellWidth - gap, cellHeight - gap)
        )
    }

    drawCell(food, Color.White)
    snake.forEach { drawCell(it, snakeColor) }
}
