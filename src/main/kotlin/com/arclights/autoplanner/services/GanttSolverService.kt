package com.arclights.autoplanner.services

import com.arclights.autoplanner.mappers.GanttMapper
import com.arclights.autoplanner.models.GanttConfig
import com.arclights.autoplanner.models.GanttResult
import com.arclights.autoplanner.models.RelationConstraint
import com.arclights.autoplanner.models.RelationConstraintType
import com.arclights.autoplanner.models.ResourceAllocationConfig
import com.arclights.autoplanner.models.TaskConfig
import com.arclights.autoplanner.printResult
import jakarta.inject.Singleton
import org.jacop.constraints.And
import org.jacop.constraints.IfThenElse
import org.jacop.constraints.Max
import org.jacop.constraints.Sum
import org.jacop.constraints.XeqC
import org.jacop.constraints.XgtY
import org.jacop.constraints.XgteqC
import org.jacop.constraints.XlteqC
import org.jacop.constraints.XplusCeqZ
import org.jacop.core.IntVar
import org.jacop.core.Store
import org.jacop.core.Var
import org.jacop.search.DepthFirstSearch
import org.jacop.search.IndomainMin
import org.jacop.search.Search
import org.jacop.search.SelectChoicePoint
import org.jacop.search.SimpleSelect
import org.jacop.search.SimpleSolutionListener


@Singleton
class GanttSolverService(private val ganttMapper: GanttMapper) {

    fun calculate(ganttConfig: GanttConfig): GanttResult {
        val (store, vars, cost, taskToStart) = setup(ganttConfig)
        solve(store, vars, cost, taskToStart)
        return ganttMapper.mapResult(taskToStart)
    }

    private fun solve(store: Store, vars: List<IntVar>, cost: IntVar, taskToStart: Map<TaskConfig, IntVar>) {
        println("Consistent: ${store.consistency()}")
        if (store.consistency().not()) {
            println(store.recentlyFailedConstraint)
        }
        println("Solving...")
        println("Number of variables: ${vars.size}")
        val t1: Long = System.currentTimeMillis()

        val select: SelectChoicePoint<IntVar> = SimpleSelect(vars.toTypedArray(), null, IndomainMin())

        class TaskPrintOutListener<T : Var> : SimpleSolutionListener<T>() {
            override fun executeAfterSolution(search: Search<T>, select: SelectChoicePoint<T>): Boolean {
                val parent = super.executeAfterSolution(search, select)

                search.costVariable?.also { println("Solution cost $it") }

                if (noSolutions > 1) {
                    println("No of solutions : $noSolutions")
                    println("Last Solution :")
                } else {
                    println("Solution :")
                }

                var solutionIndex = 0

                if (isRecordingSolutions) solutionIndex = noSolutions - 1

//                vars.forEachIndexed { i, variable ->
//                    println("${variable.id}=${solutions[solutionIndex][i]}")
//                }
//                println()

                printResult(ganttMapper.mapResult(taskToStart))
                println()

                return parent
            }
        }

        val search = DepthFirstSearch<IntVar>()
        search.solutionListener = TaskPrintOutListener()
        search.solutionListener.searchAll(true)
        search.solutionListener.recordSolutions(true)

        val result: Boolean = search.labeling(store, select, cost)


        if (result) store.print()

        val t2: Long = System.currentTimeMillis()

        println("\n\t*** Execution time = " + (t2 - t1) + " ms")
    }

    private fun setup(ganttConfig: GanttConfig): Setup {
        println("Setting up the constrains...")
        val store = Store()

        val (resourceAllocations, tasks) = ganttConfig

        val vars = mutableListOf<IntVar>()

        val worstCaseEnd = tasks.sumOf { it.length }
        println("Worst case end: $worstCaseEnd")

        val initIntVar = { name: String, min: Int, max: Int -> IntVar(store, name, min, max).also(vars::add) }
        val initTimeIntVar = { name: String -> initIntVar(name, 0, worstCaseEnd) }

        val tasksToStartAndEnd = tasks.associateWith { task -> setupTask(store, task, initTimeIntVar) }

        val taskIdToStartAndEnd = tasksToStartAndEnd.mapKeys { it.key.id }
        val tasksToStart = tasksToStartAndEnd.mapValues { (_, startAndEnd) -> startAndEnd.start }

        val taskEnds = tasksToStartAndEnd.values.map { it.end }

        val lastTaskEnd = initTimeIntVar("Last task end")
        store.impose(Max(taskEnds, lastTaskEnd))

        setupResourceConstraints(store, resourceAllocations, tasksToStartAndEnd, initIntVar)

        setupRelationshipConstraints(store, tasks, taskIdToStartAndEnd)

        return Setup(store, vars, lastTaskEnd, tasksToStart)
    }

    private fun setupTask(
        store: Store,
        taskConfig: TaskConfig,
        initTimeIntVar: (String) -> IntVar
    ): TaskSetup {
        val start = initTimeIntVar("${taskConfig.name} start")
        val end = initTimeIntVar("${taskConfig.name} end")
        store.impose(XplusCeqZ(start, taskConfig.length - 1, end))

        return TaskSetup(start, end)
    }

    private fun setupRelationshipConstraints(
        store: Store,
        tasks: List<TaskConfig>,
        taskIdToTaskSetup: Map<String, TaskSetup>
    ) {
        tasks.forEach { task ->
            task.relationConstraints.forEach { relationConstraint ->
                setupRelationshipConstraint(store, task.id, relationConstraint, taskIdToTaskSetup)
            }
        }
    }

    private fun setupRelationshipConstraint(
        store: Store,
        taskId: String,
        relationConstraint: RelationConstraint,
        taskIdToTaskSetup: Map<String, TaskSetup>
    ) {
        val fromTaskSetup = taskIdToTaskSetup.getValue(taskId)
        val toTaskSetup = taskIdToTaskSetup.getValue(relationConstraint.taskIdRelationTo)
        when (relationConstraint.type) {
            RelationConstraintType.AFTER -> setupAfterRelationshipConstraint(
                store,
                fromTaskSetup.start,
                toTaskSetup.end
            )

            RelationConstraintType.BEFORE -> setupBeforeRelationshipConstraint(
                store,
                fromTaskSetup.end,
                toTaskSetup.start
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
        taskToStartAndEnds: Map<TaskConfig, TaskSetup>,
        initIntVar: (String, Int, Int) -> IntVar
    ) {
        resourceAllocations.forEach { resourceAllocationConfig ->
            setupResourceConstraints(
                store,
                resourceAllocationConfig,
                taskToStartAndEnds,
                initIntVar
            )
        }
    }

    private fun setupResourceConstraints(
        store: Store,
        resourceAllocation: ResourceAllocationConfig,
        taskToStartAndEnds: Map<TaskConfig, TaskSetup>,
        initIntVar: (String, Int, Int) -> IntVar
    ) {
        (resourceAllocation.timeUnitFrom..resourceAllocation.timeUnitUntil).forEach { time ->
            setupResourceConstraints(store, time, resourceAllocation.nbrOfResources, taskToStartAndEnds, initIntVar)
        }
    }

    private fun setupResourceConstraints(
        store: Store,
        time: Int,
        resourcesAvailable: Int,
        taskToStartAndEnds: Map<TaskConfig, TaskSetup>,
        initIntVar: (String, Int, Int) -> IntVar
    ) {
        val resourcesUsedAtTime = taskToStartAndEnds.entries.mapIndexed { i, (taskConfig, _) ->
            initIntVar("resources used for task $i at time $time", 0, taskConfig.requiredResources)
        }

        taskToStartAndEnds.entries.forEachIndexed { i, (taskConfig, startAndEnd) ->
            val (start, end) = startAndEnd
            store.impose(
                IfThenElse(
                    And(
                        XlteqC(start, time),
                        XgteqC(end, time)
                    ),
                    XeqC(resourcesUsedAtTime[i], taskConfig.requiredResources),
                    XeqC(resourcesUsedAtTime[i], 0)
                )
            )
        }
        val totalResourcesUsedAtTime = initIntVar("Resources used at time $time", 0, resourcesAvailable)
        store.impose(Sum(resourcesUsedAtTime, totalResourcesUsedAtTime))
        store.impose(XlteqC(totalResourcesUsedAtTime, resourcesAvailable))
    }

    private data class Setup(
        val store: Store,
        val vars: List<IntVar>,
        val lastTaskEnd: IntVar,
        val taskToStart: Map<TaskConfig, IntVar>
    )

    private data class TaskSetup(
        val start: IntVar,
        val end: IntVar
    )
}