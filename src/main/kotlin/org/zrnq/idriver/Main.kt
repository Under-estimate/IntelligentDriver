package org.zrnq.idriver

import java.awt.Color
import java.awt.Desktop
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.*
import kotlin.concurrent.thread

object Conf {
    const val renderUnitSize = 50
    const val renderGapSize = 5
    val renderBlockSize : Int
        get() = renderUnitSize + renderGapSize
    const val fieldWidth = 6
    const val fieldHeight = 6
}

data class Vehicle(val length : Int, val color : Color, val isPrimary: Boolean = false)

data class VehiclePos(val x : Int, val y : Int, val horizontal : Boolean)

class ParkingState(val vehicles : MutableMap<Vehicle, VehiclePos>) {
    val occupiedSlots = Array(Conf.fieldHeight) { BooleanArray(Conf.fieldWidth) { false } }
    init {
        update()
    }
    fun update() {
        occupiedSlots.forEach { it.fill(false) }
        vehicles.forEach { (v, p) ->
            if(p.horizontal) {
                repeat(v.length) {
                    occupiedSlots[p.y][p.x + it] = true
                }
            } else {
                repeat(v.length) {
                    occupiedSlots[p.y + it][p.x] = true
                }
            }
        }
    }
    fun listAdjacentStates() : List<ParkingState> {
        val result = mutableListOf<ParkingState>()
        vehicles.forEach { (v, p) ->
            if(p.horizontal) {
                if(p.x > 0 && !occupiedSlots[p.y][p.x - 1]) result.add(ParkingState(HashMap(vehicles).also { it[v] = VehiclePos(it[v]!!.x - 1, it[v]!!.y, it[v]!!.horizontal) }))
                if(p.x + v.length < Conf.fieldWidth && !occupiedSlots[p.y][p.x + v.length]) result.add(ParkingState(HashMap(vehicles).also { it[v] = VehiclePos(it[v]!!.x + 1, it[v]!!.y, it[v]!!.horizontal) }))
            } else {
                if(p.y > 0 && !occupiedSlots[p.y - 1][p.x]) result.add(ParkingState(HashMap(vehicles).also { it[v] = VehiclePos(it[v]!!.x, it[v]!!.y - 1, it[v]!!.horizontal) }))
                if(p.y + v.length < Conf.fieldHeight && !occupiedSlots[p.y + v.length][p.x]) result.add(ParkingState(HashMap(vehicles).also { it[v] = VehiclePos(it[v]!!.x, it[v]!!.y + 1, it[v]!!.horizontal) }))
            }
        }
        return result
    }
    fun render(g : Graphics2D) {
        g.color = Color.lightGray
        for(r in occupiedSlots.indices) {
            for(c in occupiedSlots[r].indices) {
                if(occupiedSlots[r][c]) continue
                g.drawRect(c * Conf.renderBlockSize, r * Conf.renderBlockSize, Conf.renderUnitSize, Conf.renderUnitSize)
            }
        }
        vehicles.forEach { (v, p) ->
            g.color = v.color
            if(p.horizontal) {
                g.fillRect(p.x * Conf.renderBlockSize, p.y * Conf.renderBlockSize, Conf.renderBlockSize * v.length - Conf.renderGapSize, Conf.renderUnitSize)
            } else {
                g.fillRect(p.x * Conf.renderBlockSize, p.y * Conf.renderBlockSize, Conf.renderUnitSize, Conf.renderBlockSize * v.length - Conf.renderGapSize, )
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if(other == null) return false
        if(other !is ParkingState) return false
        if(vehicles.size != other.vehicles.size) return false
        for(v in vehicles.keys) {
            if(vehicles[v] != other.vehicles[v]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        return vehicles.map { (v, p) -> v.hashCode() xor p.hashCode() }.reduce { i1, i2 -> i1 + i2 }
    }
}

lateinit var displayState : ParkingState
var currentPlacingVehicle : Vehicle? = null
var isVehicleHorizontal = true
var currentMousePos : Point = Point(0, 0)

fun main() {
    val allVehicles = mutableMapOf(
        KeyEvent.VK_X to Vehicle(2, Color(255, 237, 72), true),
        KeyEvent.VK_A to Vehicle(2, Color(216, 255, 185)),
        KeyEvent.VK_B to Vehicle(2, Color(255, 205, 100)),
        KeyEvent.VK_C to Vehicle(2, Color(251, 248, 255)),
        KeyEvent.VK_D to Vehicle(2, Color(255, 193, 241)),
        KeyEvent.VK_E to Vehicle(2, Color(212, 158, 255)),
        KeyEvent.VK_F to Vehicle(2, Color(241, 255, 95)),
        KeyEvent.VK_G to Vehicle(2, Color(128, 43, 80)),
        KeyEvent.VK_H to Vehicle(2, Color(166, 162, 128)),
        KeyEvent.VK_I to Vehicle(2, Color(117, 52, 128)),
        KeyEvent.VK_J to Vehicle(2, Color(181, 128, 75)),
        KeyEvent.VK_K to Vehicle(2, Color(129, 143, 58)),
        KeyEvent.VK_O to Vehicle(3, Color(57, 68, 77)),
        KeyEvent.VK_P to Vehicle(3, Color(181, 60, 48)),
        KeyEvent.VK_Q to Vehicle(3, Color(63, 110, 173)),
        KeyEvent.VK_R to Vehicle(3, Color(71, 128, 48)),
    )

    var initialState = ParkingState(mutableMapOf())

    displayState = initialState

    val frame = JFrame("Intelligent Driver")
    frame.jMenuBar = JMenuBar().also { bar ->
        bar.add(JMenu("Action").also { menu ->
            menu.add(JMenuItem(("Clear")).also {
                it.addActionListener {
                    initialState.vehicles.clear()
                    initialState.update()
                    displayState = initialState
                    frame.repaint()
                }
            })
            menu.add(JMenuItem("Run Analysis").also { item ->
                item.addActionListener {
                    if(!initialState.vehicles.containsKey(allVehicles[KeyEvent.VK_X])) {
                        JOptionPane.showMessageDialog(frame, "Vehicle 'X' is not placed", "Error", JOptionPane.ERROR_MESSAGE)
                        return@addActionListener
                    }
                    if(initialState.vehicles[allVehicles[KeyEvent.VK_X]]!!.horizontal) {
                        JOptionPane.showMessageDialog(frame, "Vehicle 'X' is not in a valid position", "Error", JOptionPane.ERROR_MESSAGE)
                        return@addActionListener
                    }
                    thread {
                        val result = astral(initialState,
                            { it.vehicles[allVehicles[KeyEvent.VK_X]]!!.y == 4 },
                            { it.listAdjacentStates() },
                            { _, _ -> 1 },
                            { 4 - it.vehicles[allVehicles[KeyEvent.VK_X]]!!.y },
                            { displayState = it; frame.repaint() })

                        if(result != null) {
                            JOptionPane.showMessageDialog(frame, "Search complete! ${result.size} steps in total.")
                            result.forEach {
                                displayState = it; frame.repaint(); Thread.sleep(200)
                            }
                        } else {
                            JOptionPane.showMessageDialog(frame, "Failed to find solution", "No solution", JOptionPane.ERROR_MESSAGE)
                        }
                    }
                }
            })
            menu.add(JMenuItem("Help").also {
                it.addActionListener {
                    JOptionPane.showMessageDialog(frame, """
                        Press key 'A'-'K', 'O'-'R' and 'X' to place vehicles.
                        Press backspace to delete vehicle under mouse cursor.
                        Press space to rotate vehicle.
                        Vehicle 'X' must be placed vertically.
                        After placing some vehicles on the field, 
                        use 'Run Analysis' to analyze for possible solution.
                    """.trimIndent())
                }
            })
            menu.add(JMenuItem("About").also {
                it.addActionListener {
                    val option = JOptionPane.showOptionDialog(frame,
                        "'Intelligent Driver' is a program for finding solutions to 'Smart Driver' puzzles.",
                        "About",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.INFORMATION_MESSAGE,
                        null,
                        arrayOf("Github", "Close"),
                        "Close")
                    if(option == 0) {
                        Desktop.getDesktop().browse(URI("https://github.com/Under-estimate/IntelligentDriver"))
                    }
                }
            })
        })
    }
    frame.contentPane = object : JPanel() {
        init {
            addMouseMotionListener(object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    currentMousePos = e.point
                    if(currentPlacingVehicle != null) frame.repaint()
                }
            })
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if(!isFocusOwner) requestFocusInWindow()
                    if(currentPlacingVehicle == null) return
                    val gridx = e.x / Conf.renderBlockSize
                    val gridy = e.y / Conf.renderBlockSize
                    if(isVehicleHorizontal) {
                        if(gridx + currentPlacingVehicle!!.length > Conf.fieldWidth) return
                        for(i in gridx until gridx + currentPlacingVehicle!!.length)
                            if(initialState.occupiedSlots[gridy][i]) return
                    } else {
                        if(gridy + currentPlacingVehicle!!.length > Conf.fieldHeight) return
                        for(i in gridy until gridy + currentPlacingVehicle!!.length)
                            if(initialState.occupiedSlots[i][gridx]) return
                    }
                    initialState.vehicles[currentPlacingVehicle!!] = VehiclePos(gridx, gridy, isVehicleHorizontal)
                    initialState.update()
                    currentPlacingVehicle = null
                    frame.repaint()
                }
            })

            addKeyListener(object : KeyAdapter() {
                override fun keyReleased(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_ESCAPE -> currentPlacingVehicle = null
                        KeyEvent.VK_SPACE -> {
                            isVehicleHorizontal = !isVehicleHorizontal
                            frame.repaint()
                        }
                        KeyEvent.VK_BACK_SPACE -> {
                            val gridx = currentMousePos.x / Conf.renderBlockSize
                            val gridy = currentMousePos.y / Conf.renderBlockSize
                            val removeKey = run {
                                for (k in initialState.vehicles.keys) {
                                    if (initialState.vehicles[k]!!.x == gridx && initialState.vehicles[k]!!.y == gridy) {
                                        return@run k
                                    }
                                }
                                return@run null
                            } ?: return
                            initialState.vehicles.remove(removeKey)
                            initialState.update()
                            frame.repaint()
                        }
                        else -> {
                            if(!allVehicles.containsKey(e.keyCode)) return
                            currentPlacingVehicle = allVehicles[e.keyCode]
                            frame.repaint()
                        }
                    }
                }
            })
            isFocusable = true
        }
        override fun paint(g: Graphics) {
            super.paint(g)
            displayState.render(g as Graphics2D)
            if(currentPlacingVehicle == null) return
            g.color = currentPlacingVehicle!!.color
            if(isVehicleHorizontal) {
                g.fillRect(currentMousePos.x - Conf.renderUnitSize / 2, currentMousePos.y - Conf.renderUnitSize / 2, Conf.renderBlockSize * currentPlacingVehicle!!.length - Conf.renderGapSize, Conf.renderUnitSize)
            } else {
                g.fillRect(currentMousePos.x - Conf.renderUnitSize / 2, currentMousePos.y - Conf.renderUnitSize / 2, Conf.renderUnitSize, Conf.renderBlockSize * currentPlacingVehicle!!.length - Conf.renderGapSize)
            }
        }
    }
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.isVisible = true
    frame.setBounds(0, 0, 500, 500)
}