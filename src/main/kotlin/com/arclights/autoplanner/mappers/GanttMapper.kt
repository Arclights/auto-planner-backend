package com.arclights.autoplanner.mappers

import com.arclights.autoplanner.models.GanttConfig
import com.arclights.autoplanner.models.GanttResult
import com.arclights.autoplanner.models.Task
import com.arclights.autoplanner.models.TaskConfig
import jakarta.inject.Singleton
import org.jacop.core.IntVar
import org.jacop.core.Store

@Singleton
class GanttMapper {
    fun mapResult(taskToStart: Map<TaskConfig, IntVar>): GanttResult = GanttResult(mapTasks(taskToStart))

    private fun mapTasks(taskToStart: Map<TaskConfig, IntVar>): List<Task> =
        taskToStart.map { mapTask(it.key, it.value) }

    private fun mapTask(taskConfig: TaskConfig, start: IntVar) = Task(taskConfig.name, start.value(), taskConfig.length)
}