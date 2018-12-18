package com.aoc18.day15

import com.aoc18.utils.readFile

enum class Tile {
    Wall,
    Space
}

sealed class Entity(var x: Int, var y: Int, var hp: Int, val attackPower: Int) {
    class Goblin(x: Int, y: Int, hp: Int, attackPower: Int) : Entity(x, y, hp, attackPower) {
        override fun copy(): Entity = Elf(x, y, hp, attackPower)
        override fun isEnemy(entity: Entity): Boolean = entity !is Goblin

        override fun equals(other: Any?): Boolean =
            other === this || (other is Goblin && other.x == x && other.y == y)

        override fun hashCode(): Int = javaClass.hashCode()
        override fun toString(): String = "Goblin(x=$x, y=$y, hp=$hp)"
    }

    class Elf(x: Int, y: Int, hp: Int, attackPower: Int) : Entity(x, y, hp, attackPower) {
        override fun copy(): Entity = Elf(x, y, hp, attackPower)
        override fun isEnemy(entity: Entity): Boolean = entity !is Elf

        override fun equals(other: Any?): Boolean =
            other === this || (other is Elf && other.x == x && other.y == y)

        override fun hashCode(): Int = javaClass.hashCode()
        override fun toString(): String = "Elf(x=$x, y=$y, hp=$hp)"
    }

    abstract fun isEnemy(entity: Entity): Boolean
    fun turnOrder(board: Board): Int = board.width * y + x
    abstract fun copy(): Entity
}

class DistanceBoard(private val width: Int, private val height: Int) {
    private val distances: Array<Int?> = arrayOfNulls(width * height)

    operator fun set(x: Int, y: Int, distance: Int?) {
        distances[y * width + x] = distance
    }

    operator fun get(x: Int, y: Int): Int? = distances[y * width + x]

    fun rowToString(y: Int): String =
        (0 until width)
            .joinToString("") { x ->
                distances[y * width + x]?.let { d -> d % 10 }?.toString() ?: "#"
            }

    override fun toString(): String =
        (0 until height).joinToString("\n") { y -> rowToString(y) }
}

class Board(val width: Int, val height: Int) {
    val tiles: Array<Tile> =
        (generateSequence { Tile.Space }).take(width * height).toList().toTypedArray()

    operator fun set(x: Int, y: Int, tile: Tile) {
        tiles[y * width + x] = tile
    }

    operator fun get(x: Int, y: Int): Tile {
        val i = y * width + x
        return if (i >= 0 && i < tiles.size) tiles[i] else Tile.Wall
    }

    fun rowToString(y: Int): String =
        (0 until width)
            .joinToString("") { x ->
                when (tiles[y * width + x]) {
                    Tile.Wall -> "#"
                    Tile.Space -> "."
                }
            }

    override fun toString(): String =
        (0 until height).joinToString("\n") { y -> rowToString(y) }

    fun drawWith(entities: Array<Entity?>): String =
        (0 until height).joinToString("\n") { y -> rowToStringWith(y, entities) }

    fun rowToStringWith(y: Int, es: Array<Entity?>): String {
        val entitiesInRow = es.filterNotNull().filter { e -> e.y == y }

        return (0 until width)
            .joinToString("") { x ->
                entitiesInRow
                    .find { e -> e.x == x }
                    ?.let { e ->
                        when (e) {
                            is Entity.Elf -> "E"
                            is Entity.Goblin -> "G"
                        }
                    }
                    ?: when (tiles[y * width + x]) {
                        Tile.Wall -> "#"
                        Tile.Space -> "."
                    }
            }
    }
}

fun main() {
    val inputString = readFile("day15.txt")
    val (board, entities) = initBoard(inputString)

    val game = sequence {
        while (iterateEntities(board, entities)) {
            yield(entities)
        }
    }

    val rounds = game.count() - 1
    println(board.drawWith(entities))
    val totalHp = entities.filterNotNull().map { e -> e.hp }.sum()
    println("$rounds * $totalHp = ${rounds * totalHp}")
    println(entities.filterNotNull().filter { it is Entity.Elf }.count())
}

fun initBoard(inputString: String): Pair<Board, Array<Entity?>> {
    val lines = inputString.split('\n')
    val width: Int = lines.map { l -> l.length }.max()!!
    val height: Int = lines.count() - 1

    val board = Board(width, height)
    var x = 0
    var y = 0

    val entities = mutableListOf<Entity?>()

    inputString.forEach { c ->
        if (c == '\n') {
            x = 0
            y += 1
        } else {
            board[x, y] = when (c) {
                '#' -> Tile.Wall
                '.' -> Tile.Space

                'G' -> {
                    entities.add(Entity.Goblin(x, y, 200, 3))
                    Tile.Space
                }

                'E' -> {
                    entities.add(Entity.Elf(x, y, 200, 14))
                    Tile.Space
                }

                else -> throw Exception("Unrecognized character \"$c\"")
            }

            x += 1
        }
    }

    return Pair(board, entities.toTypedArray())
}

fun makeDistanceBoard(board: Board, entities: List<Entity>, x: Int, y: Int): DistanceBoard {
    val visited = DistanceBoard(board.width, board.height)
    var from = setOf(Pair(x, y))
    var distance = 0

    while (from.isNotEmpty()) {
        from.forEach { (x, y) -> visited[x, y] = distance }
        distance += 1

        from = from
            .flatMap { (x, y) -> adjacentTiles(x, y) }
            .toSet()
            .filter { (x, y) -> visited[x, y] == null }
            .filter { (x, y) -> board[x, y] == Tile.Space }
            .filter { (x, y) -> entities.all { e -> x != e.x || y != e.y } }
            .toSet()
    }

    return visited
}

fun adjacentTiles(x: Int, y: Int): List<Pair<Int, Int>> =
    listOf(Pair(x - 1, y), Pair(x + 1, y), Pair(x, y - 1), Pair(x, y + 1))

fun iterateEntities(board: Board, entities: Array<Entity?>): Boolean {
    data class InRange(val x: Int, val y: Int, val entity: Entity)

    data class Reachable(val x: Int, val y: Int, val entity: Entity, val distance: Int) {
        fun priorityOrder(board: Board): Int = -(board.width * y + x)
    }

    data class MoveTile(val x: Int, val y: Int, val distance: Int) {
        fun priorityOrder(board: Board): Int = -(board.width * y + x)
    }

    if (entities.filterNotNull().all { e -> e is Entity.Goblin } ||
        entities.filterNotNull().all { e -> e is Entity.Elf }
    ) {
        return false
    }

    entities.sortBy { entity -> entity?.turnOrder(board) }

    for (entityIndex in 0 until entities.size) {
        val entity = entities[entityIndex] ?: continue
        val enemies = entities.filterNotNull().filter { e -> e.isEnemy(entity) }

        val attackEnemies = adjacentTiles(entity.x, entity.y)
            .flatMap { (x, y) ->
                enemies.flatMap { e -> if (e.x == x && e.y == y) listOf(e) else emptyList() }
            }

        if (attackEnemies.isNotEmpty()) {
            attackEnemies.minBy { e -> e.hp }?.hp
                .let { hp ->
                    attackEnemies
                        .filter { e -> e.hp == hp }
                        .minBy { e -> e.turnOrder(board) }
                        ?.also { e -> e.hp -= entity.attackPower }
                        ?.let { e -> if (e.hp <= 0) entities[entities.indexOf(e)] = null }
                }

            continue
        }

        val distanceBoard =
            makeDistanceBoard(board, entities.filterNotNull().toList(), entity.x, entity.y)

        val reachable = enemies
            .flatMap { e ->
                adjacentTiles(e.x, e.y)
                    .filter { (x, y) -> board[x, y] == Tile.Space }
                    .map { (x, y) -> InRange(x, y, e) }
            }
            .flatMap { inRange ->
                val distance = distanceBoard[inRange.x, inRange.y]

                if (distance == null) {
                    emptyList()
                } else {
                    listOf(Reachable(inRange.x, inRange.y, inRange.entity, distance))
                }
            }

        val minDistance = reachable.minBy { r -> r.distance }?.distance
        val nearest = reachable.filter { r -> r.distance == minDistance }

        nearest
            .maxBy { r -> r.priorityOrder(board) }
            ?.let { target ->
                val targetDistanceBoard =
                    makeDistanceBoard(
                        board,
                        entities.filterNotNull().toList(),
                        target.entity.x,
                        target.entity.y
                    )

                val moveTiles = adjacentTiles(entity.x, entity.y)
                    .flatMap { (x, y) ->
                        val distance = targetDistanceBoard[x, y]
                        if (distance == null) emptyList() else listOf(MoveTile(x, y, distance))
                    }

                val closestMoveTileDistance =
                    moveTiles.minBy { (_, _, distance) -> distance }!!.distance

                val moveTile = moveTiles
                    .filter { (_, _, distance) -> distance == closestMoveTileDistance }
                    .maxBy { m -> m.priorityOrder(board) }!!

                entity.x = moveTile.x
                entity.y = moveTile.y
            }

        val attackEnemiesAgain = adjacentTiles(entity.x, entity.y)
            .flatMap { (x, y) ->
                enemies.flatMap { e -> if (e.x == x && e.y == y) listOf(e) else emptyList() }
            }

        if (attackEnemiesAgain.isNotEmpty()) {
            attackEnemiesAgain.minBy { e -> e.hp }?.hp
                .let { hp ->
                    attackEnemiesAgain
                        .filter { e -> e.hp == hp }
                        .minBy { e -> e.turnOrder(board) }
                        ?.also { e -> e.hp -= entity.attackPower }
                        ?.let { e -> if (e.hp <= 0) entities[entities.indexOf(e)] = null }
                }
        }
    }

    return true
}
