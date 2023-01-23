package org.zrnq.idriver

import java.util.*
import kotlin.collections.HashSet

fun <T> astral(s0 : T,
               isGoal : (T) -> Boolean,
               next : (T) -> List<T>,
               cost : (T, T) -> Int,
               hStar : (T) -> Int,
               prog : (T) -> Unit) : List<T>? {
    val open = PriorityQueue<AstralNode<T>> { a1, a2 ->
        (a1.minCost + hStar(a1.node)) - (a2.minCost + hStar(a2.node)) // priority = g*(n) + h*(n)
    }.apply { add(AstralNode(s0, 0)) }
    val closed = HashSet<T>()
    while(!open.isEmpty()) {
        print("\r[${closed.size}/${closed.size + open.size}] states covered")
        val min = open.poll()
        prog(min.node)
        if(isGoal(min.node)) {
            return min.minTrace().reversed()
        }
        closed.add(min.node)
        val m = next(min.node).filter { !closed.contains(it) }
        m.forEach {
            val inOpen = open.find { iter -> iter.node == it }
            if(inOpen != null) {
                min.tryClaim(inOpen, cost)
            } else {
                open.add(AstralNode(it, min.minCost + cost(min.node, it), min))
            }
        }
    }
    return null
}

/**
 * A*算法辅助类
 * */
class AstralNode<N>(val node : N, var minCost : Int, var minParent : AstralNode<N>? = null) {
    private var children : MutableList<AstralNode<N>> = mutableListOf()
    fun minTrace(list : MutableList<N> = mutableListOf()) : List<N> {
        list.add(node)
        if(minParent != null)
            return minParent!!.minTrace(list)
        return list
    }
    fun tryClaim(child : AstralNode<N>, cost : (N, N) -> Int) {
        if(minCost + cost(node, child.node) >= child.minCost)
            return
        children.add(child)
        if(child.minParent != null) child.minParent!!.children.remove(child)
        child.minParent = this
        child.updateCost(cost)
    }
    private fun updateCost(cost : (N, N) -> Int) {
        minCost = minParent!!.minCost + cost(minParent!!.node, node)
        children.forEach { it.updateCost(cost) }
    }
}