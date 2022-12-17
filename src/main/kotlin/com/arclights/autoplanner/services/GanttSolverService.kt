package com.arclights.autoplanner.services

import com.arclights.autoplanner.mappers.GanttMapper
import com.arclights.autoplanner.models.GanttConfig
import com.arclights.autoplanner.models.GanttResult
import com.arclights.autoplanner.models.RelationConstraint
import com.arclights.autoplanner.models.RelationConstraintType
import com.arclights.autoplanner.models.TaskConfig
import jakarta.inject.Singleton
import org.jacop.constraints.Max
import org.jacop.constraints.XgteqY
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
        val (store, vars, cost, taskToStart) = setup(ganttConfig)
        solve(store, vars, cost)
        return ganttMapper.mapResult(taskToStart)
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

        val (_, tasks) = ganttConfig

        val vars = mutableListOf<IntVar>()

        val worstCaseEnd = tasks.sumOf { it.length }
        val tasksToStart = tasks
            .associateWith { task -> IntVar(store, "${task.name} start", 0, worstCaseEnd).also(vars::add) }

        val taskIdsToStart = tasksToStart.mapKeys { it.key.id }

        val taskIdsToEnds = tasksToStart
            .mapValues { (task, taskStart) ->
                val end = IntVar(store, "${task.name} end", 0, worstCaseEnd).also(vars::add)
                store.impose(XplusCeqZ(taskStart, task.length, end))
                end
            }
            .mapKeys { it.key.id }

        val taskEnds = taskIdsToEnds.map(Map.Entry<String, IntVar>::value)

        val lastTaskEnd = IntVar(store, "Last task start", 0, worstCaseEnd).also(vars::add)
        store.impose(Max(taskEnds, lastTaskEnd))

        setupRelationshipConstraints(store, tasks, taskIdsToStart, taskIdsToEnds)

        return Setup(store, vars, lastTaskEnd, tasksToStart)
    }

    private fun setupRelationshipConstraints(
        store: Store,
        tasks: List<TaskConfig>,
        taskIdsToStart: Map<String, IntVar>,
        taskIdsToEnd: Map<String, IntVar>
    ) {
        tasks.forEach { task ->
            task.relationConstraints.forEach { relationConstraint ->
                setupRelationshipConstraint(store, task.id, relationConstraint, taskIdsToStart, taskIdsToEnd)
            }
        }
    }

    private fun setupRelationshipConstraint(
        store: Store,
        taskId: String,
        relationConstraint: RelationConstraint,
        taskIdsToStart: Map<String, IntVar>,
        taskIdsToEnd: Map<String, IntVar>
    ) {
        when (relationConstraint.type) {
            RelationConstraintType.AFTER -> setupAfterRelationshipConstraint(
                store,
                taskIdsToStart.getValue(taskId),
                taskIdsToEnd.getValue(relationConstraint.taskIdRelationTo)
            )

            RelationConstraintType.BEFORE -> setupBeforeRelationshipConstraint(
                store,
                taskIdsToEnd.getValue(taskId),
                taskIdsToStart.getValue(relationConstraint.taskIdRelationTo)
            )
        }
    }

    private fun setupAfterRelationshipConstraint(
        store: Store,
        taskStartFrom: IntVar,
        taskEndTo: IntVar,
    ) {
        store.impose(XgteqY(taskStartFrom, taskEndTo))
    }

    private fun setupBeforeRelationshipConstraint(
        store: Store,
        taskEndFrom: IntVar,
        taskStartTo: IntVar
    ) {
        store.impose(XgteqY(taskEndFrom, taskStartTo))
    }

    private data class Setup(
        val store: Store,
        val vars: List<IntVar>,
        val lastTaskEnd: IntVar,
        val taskToStart: Map<TaskConfig, IntVar>
    )
}