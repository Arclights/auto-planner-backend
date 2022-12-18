package com.arclights.autoplanner.services

import com.arclights.autoplanner.mappers.GanttMapper
import com.arclights.autoplanner.models.GanttConfig
import com.arclights.autoplanner.models.GanttResult
import com.arclights.autoplanner.models.RelationConstraint
import com.arclights.autoplanner.models.RelationConstraintType
import com.arclights.autoplanner.models.ResourceAllocationConfig
import com.arclights.autoplanner.models.TaskConfig
import jakarta.inject.Singleton
import org.jacop.constraints.Count
import org.jacop.constraints.Max
import org.jacop.constraints.XgtY
import org.jacop.constraints.XlteqC
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

        val (resourceAllocations, tasks) = ganttConfig

        val vars = mutableListOf<IntVar>()

        val worstCaseEnd = tasks.sumOf { it.length }

        val initIntVar = { name: String -> IntVar(store, name, 0, worstCaseEnd).also(vars::add) }

        val tasksToVars = tasks.associateWith { task -> setupTask(store, task, initIntVar) }

        val tasksToStart = tasksToVars.mapValues { (_, taskVars) -> taskVars.first() }
        val taskIdsToStart = tasksToStart.mapKeys { it.key.id }
        val taskIdsToEnd = tasksToVars.mapValues { (_, taskVars) -> taskVars.last() }.mapKeys { (task, _) -> task.id }

        val taskEnds = taskIdsToEnd.map(Map.Entry<String, IntVar>::value)

        val lastTaskEnd = initIntVar("Last task end")
        store.impose(Max(taskEnds, lastTaskEnd))

        val taskVars = tasksToVars.values.flatten()
        setupResourceConstraints(store, resourceAllocations, taskVars, initIntVar)

        setupRelationshipConstraints(store, tasks, taskIdsToStart, taskIdsToEnd)

        return Setup(store, vars, lastTaskEnd, tasksToStart)
    }

    private fun setupTask(
        store: Store,
        taskConfig: TaskConfig,
        initIntVar: (String) -> IntVar
    ): List<IntVar> {
        val vars = (0 until taskConfig.length).map { initIntVar("${taskConfig.name} $it") }
        vars.reduce { previous, current ->
            store.impose(XplusCeqZ(previous, 1, current))
            current
        }

        vars.first().id = "${taskConfig.name} start"
        vars.last().id = "${taskConfig.name} end"

        return vars
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
        store.impose(XgtY(taskStartFrom, taskEndTo))
    }

    private fun setupBeforeRelationshipConstraint(
        store: Store,
        taskEndFrom: IntVar,
        taskStartTo: IntVar
    ) {
        store.impose(XgtY(taskEndFrom, taskStartTo))
    }

    private fun setupResourceConstraints(
        store: Store,
        resourceAllocations: List<ResourceAllocationConfig>,
        taskVars: List<IntVar>,
        initIntVar: (String) -> IntVar
    ) {
        resourceAllocations.forEach { resourceAllocationConfig ->
            setupResourceConstraints(
                store,
                resourceAllocationConfig,
                taskVars,
                initIntVar
            )
        }
    }

    private fun setupResourceConstraints(
        store: Store,
        resourceAllocation: ResourceAllocationConfig,
        taskVars: List<IntVar>,
        initIntVar: (String) -> IntVar
    ) {
        (resourceAllocation.timeUnitFrom..resourceAllocation.timeUnitUntil).forEach { timeUnit ->
            setupResourceConstraint(
                store,
                timeUnit,
                resourceAllocation.nbrOfResources,
                taskVars,
                initIntVar
            )
        }
    }

    /**
     * Should not exceed the number of resources at a specific point in time
     */
    private fun setupResourceConstraint(
        store: Store,
        timeUnit: Int,
        nbrOfResources: Int,
        taskVars: List<IntVar>,
        initIntVar: (String) -> IntVar
    ) {
        val resourceVar = initIntVar("Resources at time $timeUnit")
        store.impose(Count(taskVars, resourceVar, timeUnit))
        store.impose(XlteqC(resourceVar, nbrOfResources))
    }

    private data class Setup(
        val store: Store,
        val vars: List<IntVar>,
        val lastTaskEnd: IntVar,
        val taskToStart: Map<TaskConfig, IntVar>
    )
}