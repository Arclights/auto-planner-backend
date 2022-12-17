package com.arclights.autoplanner.services

import com.arclights.autoplanner.mappers.GanttMapper
import com.arclights.autoplanner.models.GanttConfig
import com.arclights.autoplanner.models.GanttResult
import com.arclights.autoplanner.models.TaskConfig
import jakarta.inject.Singleton
import org.jacop.constraints.Max
import org.jacop.constraints.XplusCeqZ
import org.jacop.core.IntVar
import org.jacop.core.Store
import org.jacop.search.DepthFirstSearch
import org.jacop.search.IndomainMin
import org.jacop.search.SelectChoicePoint
import org.jacop.search.SimpleSelect


@Singleton
class GanttSolverService(private val ganttMapper: GanttMapper) {

    fun calculate(ganttConfig: GanttConfig): GanttResult {
        var (store, vars, cost, taskToStart) = setup(ganttConfig)
        solve(store, vars, cost)
        return ganttMapper.mapResult(ganttConfig, store, taskToStart)
    }

    private fun solve(store: Store, vars: List<IntVar>, cost: IntVar) {
        val t1: Long = System.currentTimeMillis()

        val select: SelectChoicePoint<IntVar> = SimpleSelect(vars.toTypedArray(), null, IndomainMin())

        val search = DepthFirstSearch<IntVar>()

        val result: Boolean = search.labeling(store, select, cost)

        if (result) store.print()

        val t2: Long = System.currentTimeMillis()

        println("\n\t*** Execution time = " + (t2 - t1) + " ms")
    }

    private fun setup(ganttConfig: GanttConfig): Setup {
        val store = Store()

        val vars = mutableListOf<IntVar>()

        var worstCaseEnd = ganttConfig.tasks.sumOf { it.length }
        var tasksToStart = ganttConfig.tasks
            .associateWith { task -> IntVar(store, task.name, 0, worstCaseEnd).also(vars::add) }

        val taskEnds = tasksToStart.map { (task, taskStart) ->
            val end = IntVar(store, "${task.name} end", 0, worstCaseEnd).also(vars::add)
            store.impose(XplusCeqZ(taskStart, task.length, end))
            end
        }

        val lastTaskEnd = IntVar(store, "Last task start", 0, worstCaseEnd).also(vars::add)
        store.impose(Max(taskEnds, lastTaskEnd))

        return Setup(store, vars, lastTaskEnd, tasksToStart)
    }

    private data class Setup(
        val store: Store,
        val vars: List<IntVar>,
        val lastTaskEnd: IntVar,
        val taskToStart: Map<TaskConfig, IntVar>
    )
}